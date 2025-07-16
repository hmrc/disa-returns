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

package controllers

import cats.data.EitherT
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.disareturns.connectors.response.{EtmpObligations, EtmpReportingWindow}
import uk.gov.hmrc.disareturns.controllers.InitiateSubmissionController
import uk.gov.hmrc.disareturns.models.errors.connector.responses.{InternalServerErr, MultipleErrorResponse, ObligationClosed, ReportingWindowClosed}
import utils.BaseUnitSpec

import scala.concurrent.Future

class InitiateSubmissionControllerSpec extends BaseUnitSpec {

  val controller: InitiateSubmissionController = app.injector.instanceOf[InitiateSubmissionController]

  val isaManagerRef = "Z123456"
  val boxId         = "box-123"
  val returnId      = "return-789"
  val obligation:      EtmpObligations     = EtmpObligations(false)
  val reportingWindow: EtmpReportingWindow = EtmpReportingWindow(true)

  val validSubmissionJson: JsValue = Json.obj(
    "totalRecords"     -> 400,
    "submissionPeriod" -> "JAN",
    "taxYear"          -> 2025
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockETMPService, mockPPNSService, mockInitiateSubmissionDataService)
  }

  "InitiateSubmissionController.initiate" should {

    "return 200 OK for valid submission" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(EitherT.rightT((reportingWindow, obligation)))
      when(mockPPNSService.getBoxId(any())(any()))
        .thenReturn(EitherT.rightT(boxId))
      when(mockInitiateSubmissionDataService.saveInitiateSubmission(any(), any(), any()))
        .thenReturn(Future.successful(returnId))

      val fakeRequest = FakeRequest("POST", s"/initiate/$isaManagerRef")
        .withHeaders("X-Client-ID" -> "client-999")
        .withBody(validSubmissionJson)

      val result = controller.initiate(isaManagerRef)(fakeRequest)

      status(result)                                  shouldBe OK
      (contentAsJson(result) \ "returnId").as[String] shouldBe returnId
      (contentAsJson(result) \ "boxId").as[String]    shouldBe boxId
      (contentAsJson(result) \ "action").as[String]   shouldBe "SUBMIT_RETURN_TO_PAGINATED_API"
    }

    "return 200 OK for valid nil submission" in {
      val validSubmissionJson: JsValue = Json.obj(
        "totalRecords"     -> 0,
        "submissionPeriod" -> "JAN",
        "taxYear"          -> 2025
      )
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(EitherT.rightT((reportingWindow, obligation)))
      when(mockPPNSService.getBoxId(any())(any()))
        .thenReturn(EitherT.rightT(boxId))
      when(mockInitiateSubmissionDataService.saveInitiateSubmission(any(), any(), any()))
        .thenReturn(Future.successful(returnId))

      val fakeRequest = FakeRequest("POST", s"/initiate/$isaManagerRef")
        .withHeaders("X-Client-ID" -> "client-999")
        .withBody(validSubmissionJson)

      val result = controller.initiate(isaManagerRef)(fakeRequest)

      status(result)                                  shouldBe OK
      (contentAsJson(result) \ "returnId").as[String] shouldBe returnId
      (contentAsJson(result) \ "boxId").as[String]    shouldBe boxId
      (contentAsJson(result) \ "action").as[String]   shouldBe "NIL_RETURN_ACCEPTED_NO_FURTHER_ACTION"
    }

    "return 400 BadRequest for invalid JSON when missing fields" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      val invalidJson = Json.obj("totalRecords" -> 1)
      val request = FakeRequest("POST", s"/initiate/$isaManagerRef")
        .withHeaders("X-Client-ID" -> "client-abc")
        .withBody(invalidJson)

      val result = controller.initiate(isaManagerRef)(request)

      status(result)                               shouldBe BAD_REQUEST
      (contentAsJson(result) \ "message").as[String] should include("Bad request")
      val errors = (contentAsJson(result) \ "errors").as[Seq[JsValue]]
      errors.map(e => (e \ "code").as[String]).head    shouldBe "MISSING_FIELD"
      errors.map(e => (e \ "message").as[String]).head shouldBe "This field is required"
      errors.map(e => (e \ "path").as[String]).head    shouldBe "/taxYear"
      errors.map(e => (e \ "code").as[String])(1)      shouldBe "MISSING_FIELD"
      errors.map(e => (e \ "message").as[String])(1)   shouldBe "This field is required"
      errors.map(e => (e \ "path").as[String])(1)      shouldBe "/submissionPeriod"
    }
    "return 400 BadRequest for invalid JSON when submissionPeriod is not an enum" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      val invalidJson = Json.obj("totalRecords" -> 100, "submissionPeriod" -> "January", "taxYear" -> 2025)
      val request = FakeRequest("POST", s"/initiate/$isaManagerRef")
        .withHeaders("X-Client-ID" -> "client-abc")
        .withBody(invalidJson)

      val result = controller.initiate(isaManagerRef)(request)

      status(result)                               shouldBe BAD_REQUEST
      (contentAsJson(result) \ "message").as[String] should include("Bad request")
      (contentAsJson(result) \ "code").as[String]    should include("VALIDATION_FAILURE")
      val errors = (contentAsJson(result) \ "errors").as[Seq[JsValue]]
      errors.map(e => (e \ "code").as[String]).head    shouldBe "VALIDATION_ERROR"
      errors.map(e => (e \ "message").as[String]).head shouldBe "Invalid month provided"
      errors.map(e => (e \ "path").as[String]).head    shouldBe "/submissionPeriod"
    }

    "return 403 Forbidden when ETMP obligation has already been met" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(EitherT.leftT(ObligationClosed))

      val request = FakeRequest("POST", s"/initiate/$isaManagerRef")
        .withHeaders("X-Client-ID" -> "client-abc")
        .withBody(validSubmissionJson)

      val result = controller.initiate(isaManagerRef)(request)

      status(result)                                 shouldBe FORBIDDEN
      (contentAsJson(result) \ "message").as[String] shouldBe "Return obligation already met"
      (contentAsJson(result) \ "code").as[String]    shouldBe "RETURN_OBLIGATION_ALREADY_MET"

    }
    "return 403 Forbidden when ETMP reporting window is closed" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(EitherT.leftT(ReportingWindowClosed))

      val request = FakeRequest("POST", s"/initiate/$isaManagerRef")
        .withHeaders("X-Client-ID" -> "client-abc")
        .withBody(validSubmissionJson)

      val result = controller.initiate(isaManagerRef)(request)

      status(result)                                 shouldBe FORBIDDEN
      (contentAsJson(result) \ "message").as[String] shouldBe "Reporting window has been closed"
      (contentAsJson(result) \ "code").as[String]    shouldBe "REPORTING_WINDOW_CLOSED"

    }
    "return 403 Forbidden when reporting ETMP window is closed and obligation has already been met" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(EitherT.leftT(MultipleErrorResponse(errors = Seq(ObligationClosed, ReportingWindowClosed))))

      val request = FakeRequest("POST", s"/initiate/$isaManagerRef")
        .withHeaders("X-Client-ID" -> "client-abc")
        .withBody(validSubmissionJson)

      val result = controller.initiate(isaManagerRef)(request)

      status(result)                                 shouldBe FORBIDDEN
      (contentAsJson(result) \ "message").as[String] shouldBe "Multiple issues found regarding your submission"
      (contentAsJson(result) \ "code").as[String]    shouldBe "FORBIDDEN"
      val errors = (contentAsJson(result) \ "errors").as[Seq[JsValue]]
      errors.map(e => (e \ "code").as[String]).head    shouldBe "RETURN_OBLIGATION_ALREADY_MET"
      errors.map(e => (e \ "message").as[String]).head shouldBe "Return obligation already met"
      errors.map(e => (e \ "code").as[String])(1)      shouldBe "REPORTING_WINDOW_CLOSED"
      errors.map(e => (e \ "message").as[String])(1)   shouldBe "Reporting window has been closed"

    }

    "return 500 Internal Server Error when PPNS responds with an upstream error" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(EitherT.rightT((reportingWindow, obligation)))
      when(mockPPNSService.getBoxId(any())(any()))
        .thenReturn(EitherT.leftT(InternalServerErr))
      val request = FakeRequest("POST", s"/initiate/$isaManagerRef")
        .withHeaders("X-Client-ID" -> "client-abc")
        .withBody(validSubmissionJson)

      val result = controller.initiate(isaManagerRef)(request)

      status(result)                                 shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "message").as[String] shouldBe "There has been an issue processing your request"
      (contentAsJson(result) \ "code").as[String]    shouldBe "INTERNAL_SERVER_ERROR"
    }

    "return 500 Internal Server Error when ETMP responds with an upstream error" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(EitherT.leftT(InternalServerErr))
      when(mockPPNSService.getBoxId(any())(any()))
        .thenReturn(EitherT.rightT(boxId))
      val request = FakeRequest("POST", s"/initiate/$isaManagerRef")
        .withHeaders("X-Client-ID" -> "client-abc")
        .withBody(validSubmissionJson)

      val result = controller.initiate(isaManagerRef)(request)

      status(result)                                 shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "message").as[String] shouldBe "There has been an issue processing your request"
      (contentAsJson(result) \ "code").as[String]    shouldBe "INTERNAL_SERVER_ERROR"
    }
  }
}
