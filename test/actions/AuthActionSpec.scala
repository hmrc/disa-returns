/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package actions

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.verify
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.authorisedEnrolments
import uk.gov.hmrc.auth.core.{Enrolment, Enrolments, UnsupportedAuthProvider}
import uk.gov.hmrc.disareturns.controllers.actionBuilders.AuthAction
import uk.gov.hmrc.disareturns.models.common.{InternalServerErr, UnauthorisedErr}
import utils.BaseUnitSpec

import scala.concurrent.Future

class AuthActionSpec extends BaseUnitSpec {

  private def authAction(zRef: String) = new AuthAction(mockAuthConnector, stubControllerComponents()).apply(zRef)

  import play.api.mvc.Results._
  def testBlock: Request[AnyContent] => Future[Result] =
    _ => Future.successful(Ok("Success"))

  "AuthAction.invokeBlock" should {

    "allow the request when user is authorised" in {
      authorizationForZRef()

      val request = FakeRequest().withHeaders("Authorization" -> "Bearer abc123")

      val result = authAction(validZRef).invokeBlock(request, testBlock)

      status(result)          shouldBe OK
      contentAsString(result) shouldBe "Success"

      val predicateCaptor: ArgumentCaptor[Predicate] =
        ArgumentCaptor.forClass(classOf[Predicate])

      val retrievalCaptor: ArgumentCaptor[Retrieval[Enrolments]] =
        ArgumentCaptor.forClass(classOf[Retrieval[Enrolments]])

      verify(mockAuthConnector).authorise(
        predicateCaptor.capture(),
        retrievalCaptor.capture()
      )(any(), any())

      val actualPredicate   = predicateCaptor.getValue
      val expectedPredicate = Enrolment("HMRC-DISA-ORG")

      val actualRetrieval   = retrievalCaptor.getValue
      val expectedRetrieval = authorisedEnrolments

      withClue(
        s"""
           |Auth predicate mismatch:
           |Expected: $expectedPredicate
           |Actual:   $actualPredicate
           |""".stripMargin
      ) {
        actualPredicate shouldBe expectedPredicate
      }

      withClue(
        s"""
           |Auth retrieval mismatch:
           |Expected: $expectedRetrieval
           |Actual:   $actualRetrieval
           |""".stripMargin
      ) {
        actualRetrieval shouldBe expectedRetrieval
      }
    }

    "return UNAUTHORISED when zRef does not match that retrieved from enrolment" in {
      authorizationForZRef("Z1235")

      val request = FakeRequest().withHeaders("Authorization" -> "Bearer abc123")

      val result = authAction(validZRef).invokeBlock(request, testBlock)

      status(result)        shouldBe UNAUTHORIZED
      contentAsJson(result) shouldBe Json.toJson(UnauthorisedErr)
    }

    "return UNAUTHORISED when enrolment key does not match" in {
      stubEnrolments(enrolmentKey = "HMRC-HELLO")

      val request = FakeRequest().withHeaders("Authorization" -> "Bearer abc123")

      val result = authAction(validZRef).invokeBlock(request, testBlock)

      status(result)        shouldBe UNAUTHORIZED
      contentAsJson(result) shouldBe Json.toJson(UnauthorisedErr)
    }

    "return UNAUTHORISED when identifier is missing" in {
      stubEnrolments(identifierKey = None)

      val request = FakeRequest().withHeaders("Authorization" -> "Bearer abc123")

      val result = authAction(validZRef).invokeBlock(request, testBlock)

      status(result)        shouldBe UNAUTHORIZED
      contentAsJson(result) shouldBe Json.toJson(UnauthorisedErr)
    }

    "return UNAUTHORISED when AuthorisationException is thrown by auth connector" in {
      unauthorized(UnsupportedAuthProvider("fubar"))

      val request = FakeRequest().withHeaders("Authorization" -> "Bearer abc123")

      val result = authAction(validZRef).invokeBlock(request, testBlock)

      status(result)        shouldBe UNAUTHORIZED
      contentAsJson(result) shouldBe Json.toJson(UnauthorisedErr)
    }

    "return InternalServerError for unexpected exceptions thrown by auth connector" in {
      unauthorized(new RuntimeException("Unexpected error"))

      val request = FakeRequest().withHeaders("Authorization" -> "Bearer abc123")

      val result = authAction(validZRef).invokeBlock(request, testBlock)

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.toJson(InternalServerErr())
    }
  }
}
