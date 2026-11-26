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
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{never, times, verify, when}
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.{AgentInformation, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, BearerTokenExpired, Enrolment, UnsupportedAuthProvider}
import uk.gov.hmrc.disareturns.config.Constants.{AgentEnrolmentKey, EnrolmentKey}
import uk.gov.hmrc.disareturns.controllers.actionBuilders.{AuthenticatedAuthAction, EnrolmentVerificationAuthAction}
import uk.gov.hmrc.disareturns.models.common.{IdentifierRequest, InternalServerErr, UnauthorisedErr, UserDetails}
import utils.BaseUnitSpec

import scala.concurrent.{Future, Promise}

class AuthActionSpec extends BaseUnitSpec {

  private def enrolmentAuthAction(zRef: String, agentAuthenticationEnabled: Boolean = true) = {
    when(mockAppConfig.agentAuthenticationEnabled).thenReturn(agentAuthenticationEnabled)
    new EnrolmentVerificationAuthAction(mockAuthConnector, stubControllerComponents(), mockAppConfig).apply(zRef)
  }

  private def authenticatedAuthAction(zRef: String) =
    new AuthenticatedAuthAction(mockAuthConnector, stubControllerComponents()).apply(zRef)

  import play.api.mvc.Results._
  def testBlock: IdentifierRequest[AnyContent] => Future[Result] =
    _ => Future.successful(Ok("Success"))

  private def capturingBlock(captured: Promise[UserDetails]): IdentifierRequest[AnyContent] => Future[Result] = request => {
    captured.success(request.userDetails)
    Future.successful(Ok("Success"))
  }

  "AuthAction.invokeBlock" should {

    "allow the request when user is authorised" in {
      authorizationForZRef()

      val request = FakeRequest().withHeaders(AUTHORIZATION -> "Bearer abc123")

      val result = enrolmentAuthAction(validZReference).invokeBlock(request, testBlock)

      status(result)          shouldBe OK
      contentAsString(result) shouldBe "Success"

      val predicateCaptor: ArgumentCaptor[Predicate] =
        ArgumentCaptor.forClass(classOf[Predicate])

      val retrievalCaptor: ArgumentCaptor[Retrieval[?]] =
        ArgumentCaptor.forClass(classOf[Retrieval[?]])

      verify(mockAuthConnector).authorise(
        predicateCaptor.capture(),
        retrievalCaptor.capture()
      )(any(), any())

      val actualPredicate = predicateCaptor.getValue
      val expectedPredicate =
        (AffinityGroup.Organisation and Enrolment(EnrolmentKey)) or (AffinityGroup.Agent and Enrolment(AgentEnrolmentKey))

      val actualRetrieval   = retrievalCaptor.getValue
      val expectedRetrieval = authorisedEnrolments and credentials and credentialRole and affinityGroup and agentInformation and groupIdentifier

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

    "bind IM user details to the request" in {
      authorizationForZRef()
      val captured = Promise[UserDetails]()

      val result = enrolmentAuthAction(validZReference).invokeBlock(FakeRequest(), capturingBlock(captured))

      status(result)              shouldBe OK
      captured.future.futureValue shouldBe testIsaManagerDetails
    }

    "allow an agent with a delegated enrolment for the Z-Ref and bind Agent user details to the request" in {
      stubEnrolments(userDetails = agentRetrievals())
      stubAgentDelegatedEnrolmentSuccess()
      val captured = Promise[UserDetails]()

      val result = enrolmentAuthAction(validZReference).invokeBlock(FakeRequest(), capturingBlock(captured))

      status(result)              shouldBe OK
      captured.future.futureValue shouldBe testAgentDetails
      verify(mockAuthConnector).authorise(eqTo(agentDelegatedEnrolmentPredicate()), any())(any(), any())
    }

    "return FORBIDDEN with AGENT_CLIENT_RELATIONSHIP_NOT_FOUND when an agent does not have a delegated enrolment for the Z-Ref" in {
      stubEnrolments(userDetails = agentRetrievals())
      stubAgentDelegatedEnrolmentFailure()

      val result = enrolmentAuthAction(validZReference).invokeBlock(FakeRequest(), testBlock)

      status(result) shouldBe FORBIDDEN
      contentAsJson(result) shouldBe Json.obj(
        "code"    -> "AGENT_CLIENT_RELATIONSHIP_NOT_FOUND",
        "message" -> s"We are unable to authenticate your access to $validZReference"
      )
    }

    "return UNAUTHORISED without checking for a delegated enrolment when agent authentication is disabled" in {
      stubEnrolments(userDetails = agentRetrievals())

      val result = enrolmentAuthAction(validZReference, agentAuthenticationEnabled = false).invokeBlock(FakeRequest(), testBlock)

      status(result)        shouldBe UNAUTHORIZED
      contentAsJson(result) shouldBe Json.toJson(UnauthorisedErr)
      verify(mockAuthConnector, never()).authorise(eqTo(agentDelegatedEnrolmentPredicate()), any())(any(), any())
    }

    "return UNAUTHORISED without checking for a delegated enrolment when the agent group identifier is missing" in {
      stubEnrolments(userDetails = agentRetrievals(groupId = None))

      val result = enrolmentAuthAction(validZReference).invokeBlock(FakeRequest(), testBlock)

      status(result)        shouldBe UNAUTHORIZED
      contentAsJson(result) shouldBe Json.toJson(UnauthorisedErr)
      verify(mockAuthConnector, never()).authorise(eqTo(agentDelegatedEnrolmentPredicate()), any())(any(), any())
    }

    "return UNAUTHORISED when the agent delegated enrolment check fails with another AuthorisationException" in {
      stubEnrolments(userDetails = agentRetrievals())
      stubAgentDelegatedEnrolmentFailure(BearerTokenExpired())

      val result = enrolmentAuthAction(validZReference).invokeBlock(FakeRequest(), testBlock)

      status(result)        shouldBe UNAUTHORIZED
      contentAsJson(result) shouldBe Json.toJson(UnauthorisedErr)
    }

    "return InternalServerError when the agent delegated enrolment check fails unexpectedly" in {
      stubEnrolments(userDetails = agentRetrievals())
      stubAgentDelegatedEnrolmentFailure(new RuntimeException("Unexpected error"))

      val result = enrolmentAuthAction(validZReference).invokeBlock(FakeRequest(), testBlock)

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.toJson(InternalServerErr())
    }

    "default missing agent information to unknown" in {
      stubEnrolments(userDetails =
        new ~(new ~(new ~(new ~(None, None), Some(AffinityGroup.Agent)), AgentInformation(None, None, None)), Some(testGroupId))
      )
      stubAgentDelegatedEnrolmentSuccess()
      val captured = Promise[UserDetails]()

      val result = enrolmentAuthAction(validZReference).invokeBlock(FakeRequest(), capturingBlock(captured))

      status(result)              shouldBe OK
      captured.future.futureValue shouldBe UserDetails.Agent(testGroupId, UserDetails.unknown, UserDetails.unknown)
    }

    Seq(
      "group identifier"          -> isaManagerRetrievals(groupId = None),
      "credentials for an IM"     -> isaManagerRetrievals(credentials = None),
      "credential role for an IM" -> isaManagerRetrievals(credentialRole = None)
    ).foreach { case (missing, retrievals) =>
      s"return UNAUTHORISED when auth does not return the $missing" in {
        stubEnrolments(userDetails = retrievals)

        val result = enrolmentAuthAction(validZReference).invokeBlock(FakeRequest(), testBlock)

        status(result)        shouldBe UNAUTHORIZED
        contentAsJson(result) shouldBe Json.toJson(UnauthorisedErr)
      }
    }

    "return UNAUTHORISED for an individual user" in {
      stubEnrolments(userDetails = isaManagerRetrievals(affinityGroup = AffinityGroup.Individual))

      val result = enrolmentAuthAction(validZReference).invokeBlock(FakeRequest(), testBlock)

      status(result)        shouldBe UNAUTHORIZED
      contentAsJson(result) shouldBe Json.toJson(UnauthorisedErr)
    }

    "return UNAUTHORISED when zRef does not match that retrieved from enrolment" in {
      authorizationForZRef(zRef = "MISMATCH")

      val request = FakeRequest().withHeaders(AUTHORIZATION -> "Bearer abc123")

      val result = enrolmentAuthAction(validZReference).invokeBlock(request, testBlock)

      status(result)        shouldBe UNAUTHORIZED
      contentAsJson(result) shouldBe Json.toJson(UnauthorisedErr)
    }

    "return UNAUTHORISED when enrolment key does not match" in {
      stubEnrolments(enrolmentKey = "HMRC-HELLO", zRef = validZReference)

      val request = FakeRequest().withHeaders(AUTHORIZATION -> "Bearer abc123")

      val result = enrolmentAuthAction(validZReference).invokeBlock(request, testBlock)

      status(result)        shouldBe UNAUTHORIZED
      contentAsJson(result) shouldBe Json.toJson(UnauthorisedErr)
    }

    "return UNAUTHORISED when identifier is missing" in {
      stubEnrolments(identifierKey = None)

      val request = FakeRequest().withHeaders(AUTHORIZATION -> "Bearer abc123")

      val result = enrolmentAuthAction(validZReference).invokeBlock(request, testBlock)

      status(result)        shouldBe UNAUTHORIZED
      contentAsJson(result) shouldBe Json.toJson(UnauthorisedErr)
    }

    "return UNAUTHORISED when AuthorisationException is thrown by auth connector" in {
      unauthorized(UnsupportedAuthProvider("fubar"))

      val request = FakeRequest().withHeaders(AUTHORIZATION -> "Bearer abc123")

      val result = enrolmentAuthAction(validZReference).invokeBlock(request, testBlock)

      status(result)        shouldBe UNAUTHORIZED
      contentAsJson(result) shouldBe Json.toJson(UnauthorisedErr)
    }

    "return InternalServerError for unexpected exceptions thrown by auth connector" in {
      unauthorized(new RuntimeException("Unexpected error"))

      val request = FakeRequest().withHeaders(AUTHORIZATION -> "Bearer abc123")

      val result = enrolmentAuthAction(validZReference).invokeBlock(request, testBlock)

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.toJson(InternalServerErr())
    }

    "allow an authenticated request without checking enrolments and bind user details" in {
      when(mockAuthConnector.authorise[UserDetailsRetrievals](any(), any())(any(), any()))
        .thenReturn(Future.successful(isaManagerRetrievals()))
      val captured = Promise[UserDetails]()

      val request = FakeRequest().withHeaders(AUTHORIZATION -> "Bearer abc123")

      val result = authenticatedAuthAction(validZReference).invokeBlock(request, capturingBlock(captured))

      status(result)              shouldBe OK
      contentAsString(result)     shouldBe "Success"
      captured.future.futureValue shouldBe testIsaManagerDetails

      val predicateCaptor: ArgumentCaptor[Predicate] =
        ArgumentCaptor.forClass(classOf[Predicate])

      verify(mockAuthConnector).authorise[UserDetailsRetrievals](predicateCaptor.capture(), any())(any(), any())
      predicateCaptor.getValue shouldBe (AffinityGroup.Organisation or (AffinityGroup.Agent and Enrolment(AgentEnrolmentKey)))
    }

    "allow an agent without checking for a delegated enrolment when enrolment verification is disabled" in {
      when(mockAuthConnector.authorise[UserDetailsRetrievals](any(), any())(any(), any()))
        .thenReturn(Future.successful(agentRetrievals()))
      val captured = Promise[UserDetails]()

      val result = authenticatedAuthAction(validZReference).invokeBlock(FakeRequest(), capturingBlock(captured))

      status(result)              shouldBe OK
      captured.future.futureValue shouldBe testAgentDetails
      verify(mockAuthConnector, times(1)).authorise(any(), any())(any(), any())
      verify(mockAuthConnector, never()).authorise(eqTo(agentDelegatedEnrolmentPredicate()), any())(any(), any())
    }

    "return UNAUTHORISED for an agent without a group identifier when enrolment verification is disabled" in {
      when(mockAuthConnector.authorise[UserDetailsRetrievals](any(), any())(any(), any()))
        .thenReturn(Future.successful(agentRetrievals(groupId = None)))

      val result = authenticatedAuthAction(validZReference).invokeBlock(FakeRequest(), testBlock)

      status(result)        shouldBe UNAUTHORIZED
      contentAsJson(result) shouldBe Json.toJson(UnauthorisedErr)
    }

    "return UNAUTHORISED for an individual user when enrolment verification is disabled" in {
      when(mockAuthConnector.authorise[UserDetailsRetrievals](any(), any())(any(), any()))
        .thenReturn(Future.successful(isaManagerRetrievals(affinityGroup = AffinityGroup.Individual)))

      val result = authenticatedAuthAction(validZReference).invokeBlock(FakeRequest(), testBlock)

      status(result)        shouldBe UNAUTHORIZED
      contentAsJson(result) shouldBe Json.toJson(UnauthorisedErr)
    }

    "return UNAUTHORISED for an unauthenticated request when enrolment verification is disabled" in {
      unauthorized(UnsupportedAuthProvider("fubar"))

      val request = FakeRequest().withHeaders(AUTHORIZATION -> "Bearer abc123")

      val result = authenticatedAuthAction(validZReference).invokeBlock(request, testBlock)

      status(result)        shouldBe UNAUTHORIZED
      contentAsJson(result) shouldBe Json.toJson(UnauthorisedErr)
    }
  }
}
