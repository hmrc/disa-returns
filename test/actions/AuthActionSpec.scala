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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.InsufficientEnrolments
import uk.gov.hmrc.disareturns.controllers.actionBuilders.AuthAction
import utils.BaseUnitSpec

import scala.concurrent.Future

class AuthActionSpec extends BaseUnitSpec {

  val action = new AuthAction(mockAuthConnector)

  import play.api.mvc.Results._
  def testBlock: Request[AnyContent] => Future[Result] =
    _ => Future.successful(Ok("Success"))

  "AuthAction.invokeBlock" should {

    "allow the request when user is authorised" in {
      when(mockAuthConnector.authorise[Unit](any(), any())(any(), any()))
        .thenReturn(Future.successful(()))

      val request = FakeRequest().withHeaders("Authorization" -> "Bearer abc123")

      val result = action.invokeBlock(request, testBlock)

      status(result)          shouldBe OK
      contentAsString(result) shouldBe "Success"
    }

    "return BadRequest when AuthorisationException is thrown" in {
      when(mockAuthConnector.authorise[Unit](any(), any())(any(), any()))
        .thenReturn(Future.failed(new InsufficientEnrolments("Missing enrolment")))

      val request = FakeRequest().withHeaders("Authorization" -> "Bearer abc123")

      val result = action.invokeBlock(request, testBlock)

      status(result) shouldBe UNAUTHORIZED
      contentAsJson(result) shouldBe Json.obj(
        "code"    -> "BAD_REQUEST",
        "message" -> "Missing enrolment"
      )
    }

    "return InternalServerError for unexpected exceptions" in {
      when(mockAuthConnector.authorise[Unit](any(), any())(any(), any()))
        .thenReturn(Future.failed(new RuntimeException("Unexpected error")))

      val request = FakeRequest().withHeaders("Authorization" -> "Bearer abc123")

      val result = action.invokeBlock(request, testBlock)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.obj(
        "code"    -> "INTERNAL_SERVER_ERROR",
        "message" -> "There has been an issue processing your request"
      )
    }
  }
}
