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

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.disareturns.connectors.response.{EtmpObligations, EtmpReportingWindow}
import uk.gov.hmrc.disareturns.controllers.InitiateReturnsController
import uk.gov.hmrc.disareturns.models.common._
import utils.BaseUnitSpec

import java.time.LocalDate
import scala.concurrent.Future

class InitiateReturnsControllerSpec extends BaseUnitSpec {

  val controller: InitiateReturnsController = app.injector.instanceOf[InitiateReturnsController]

  val isaManagerRef = "Z123456"
  val boxId         = "box-123"
  val returnId      = "return-789"
  val obligation:      EtmpObligations     = EtmpObligations(false)
  val reportingWindow: EtmpReportingWindow = EtmpReportingWindow(true)

  val validSubmissionJson: JsValue = Json.obj(
    "totalRecords"     -> 400,
    "submissionPeriod" -> "JAN",
    "taxYear"          -> LocalDate.now.getYear
  )

  "InitiateReturnsController.initiate" should {

    "return 200 OK for valid submission" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Right((reportingWindow, obligation))))
      when(mockPPNSService.getBoxId(any())(any()))
        .thenReturn(Future.successful(Right(boxId)))
      when(mockMonthlyReportDocumentService.saveReturnMetadata(any(), any(), any()))
        .thenReturn(Future.successful(returnId))

      val request = FakeRequest("POST", s"/monthly/$isaManagerRef/init")
        .withHeaders("X-Client-ID" -> "client-999")
        .withBody(validSubmissionJson)

      val result = controller.initiate(isaManagerRef)(request)

      status(result)                                  shouldBe OK
      (contentAsJson(result) \ "returnId").as[String] shouldBe returnId
      (contentAsJson(result) \ "boxId").as[String]    shouldBe boxId
      (contentAsJson(result) \ "action").as[String]   shouldBe "SUBMIT_RETURN_TO_PAGINATED_API"
    }

    "return 200 OK for valid nil submission" in {
      val validSubmissionJson: JsValue = Json.obj(
        "totalRecords"     -> 0,
        "submissionPeriod" -> "JAN",
        "taxYear"          -> LocalDate.now.getYear
      )
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Right((reportingWindow, obligation))))
      when(mockPPNSService.getBoxId(any())(any()))
        .thenReturn(Future.successful(Right(boxId)))
      when(mockMonthlyReportDocumentService.saveReturnMetadata(any(), any(), any()))
        .thenReturn(Future.successful(returnId))

      val request = FakeRequest("POST", s"/monthly/$isaManagerRef/init")
        .withHeaders("X-Client-ID" -> "client-999")
        .withBody(validSubmissionJson)

      val result = controller.initiate(isaManagerRef)(request)

      status(result)                                  shouldBe OK
      (contentAsJson(result) \ "returnId").as[String] shouldBe returnId
      (contentAsJson(result) \ "boxId").as[String]    shouldBe boxId
      (contentAsJson(result) \ "action").as[String]   shouldBe "NIL_RETURN_ACCEPTED_NO_FURTHER_ACTION"
    }

    "return 400 BadRequest for invalid JSON when missing fields" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      val invalidJson = Json.obj("totalRecords" -> 1)
      val request = FakeRequest("POST", s"/monthly/$isaManagerRef/init")
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
      val invalidJson = Json.obj("totalRecords" -> 100, "submissionPeriod" -> "January", "taxYear" -> LocalDate.now.getYear)
      val request = FakeRequest("POST", s"/monthly/$isaManagerRef/init")
        .withHeaders("X-Client-ID" -> "client-abc")
        .withBody(invalidJson)

      val result = controller.initiate(isaManagerRef)(request)

      status(result)                                 shouldBe BAD_REQUEST
      (contentAsJson(result) \ "message").as[String] shouldBe "Bad request"
      (contentAsJson(result) \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
      val errors = (contentAsJson(result) \ "errors").as[Seq[JsValue]]
      errors.map(e => (e \ "code").as[String]).head    shouldBe "VALIDATION_ERROR"
      errors.map(e => (e \ "message").as[String]).head shouldBe "Invalid month provided"
      errors.map(e => (e \ "path").as[String]).head    shouldBe "/submissionPeriod"
    }

    "return 400 BadRequest for request providing an invalid IsaManagerRef" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      val request = FakeRequest("POST", s"/monthly/invalidRef/init")
        .withHeaders("X-Client-ID" -> "client-999")
        .withBody(validSubmissionJson)

      val result = controller.initiate("InvalidRef")(request)

      status(result)                                 shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[String]    shouldBe "BAD_REQUEST"
      (contentAsJson(result) \ "message").as[String] shouldBe "ISA Manager Reference Number format is invalid"
    }

    "return 403 Forbidden when ETMP obligation has already been met" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Left(ObligationClosed)))

      val request = FakeRequest("POST", s"/monthly/$isaManagerRef/init")
        .withHeaders("X-Client-ID" -> "client-abc")
        .withBody(validSubmissionJson)

      val result = controller.initiate(isaManagerRef)(request)

      status(result)                                 shouldBe FORBIDDEN
      (contentAsJson(result) \ "message").as[String] shouldBe "Obligation closed"
      (contentAsJson(result) \ "code").as[String]    shouldBe "OBLIGATION_CLOSED"

    }
    "return 403 Forbidden when ETMP reporting window is closed" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Left(ReportingWindowClosed)))

      val request = FakeRequest("POST", s"/monthly/$isaManagerRef/init")
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
        .thenReturn(Future.successful(Left(MultipleErrorResponse(errors = Seq(ObligationClosed, ReportingWindowClosed)))))

      val request = FakeRequest("POST", s"/monthly/$isaManagerRef/init")
        .withHeaders("X-Client-ID" -> "client-abc")
        .withBody(validSubmissionJson)

      val result = controller.initiate(isaManagerRef)(request)

      status(result)                                 shouldBe FORBIDDEN
      (contentAsJson(result) \ "message").as[String] shouldBe "Multiple issues found regarding your submission"
      (contentAsJson(result) \ "code").as[String]    shouldBe "FORBIDDEN"
      val errors = (contentAsJson(result) \ "errors").as[Seq[JsValue]]
      errors.map(e => (e \ "code").as[String]).head    shouldBe "OBLIGATION_CLOSED"
      errors.map(e => (e \ "message").as[String]).head shouldBe "Obligation closed"
      errors.map(e => (e \ "code").as[String])(1)      shouldBe "REPORTING_WINDOW_CLOSED"
      errors.map(e => (e \ "message").as[String])(1)   shouldBe "Reporting window has been closed"

    }

    "return 500 Internal Server Error when PPNS responds with an upstream error" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Right((reportingWindow, obligation))))
      when(mockPPNSService.getBoxId(any())(any()))
        .thenReturn(Future.successful(Left(InternalServerErr)))
      val request = FakeRequest("POST", s"/monthly/$isaManagerRef/init")
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
        .thenReturn(Future.successful(Left(InternalServerErr)))
      when(mockPPNSService.getBoxId(any())(any()))
        .thenReturn(Future.successful(Right(boxId)))

      val request = FakeRequest("POST", s"/monthly/$isaManagerRef/init")
        .withHeaders("X-Client-ID" -> "client-abc")
        .withBody(validSubmissionJson)

      val result = controller.initiate(isaManagerRef)(request)

      status(result)                                 shouldBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "message").as[String] shouldBe "There has been an issue processing your request"
      (contentAsJson(result) \ "code").as[String]    shouldBe "INTERNAL_SERVER_ERROR"
    }
    "return 401 Unauthorised  when ETMP responds with an unauthorised error" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Left(Unauthorised)))
      val request = FakeRequest("POST", s"/monthly/$isaManagerRef/init")
        .withHeaders("X-Client-ID" -> "client-abc")
        .withBody(validSubmissionJson)

      val result = controller.initiate(isaManagerRef)(request)

      status(result)                                 shouldBe UNAUTHORIZED
      (contentAsJson(result) \ "message").as[String] shouldBe "Not authorised to access this service"
      (contentAsJson(result) \ "code").as[String]    shouldBe "UNAUTHORISED"
    }
  }
}
