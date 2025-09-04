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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.test.Helpers._
import play.api.test._
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.disareturns.connectors.response.{EtmpObligations, EtmpReportingWindow}
import uk.gov.hmrc.disareturns.controllers.CompleteReturnController
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, MismatchErr, ObligationClosed, ReportingWindowClosed}
import uk.gov.hmrc.disareturns.models.complete.CompleteReturnResponse
import uk.gov.hmrc.http.HttpResponse
import utils.BaseUnitSpec

import scala.concurrent.Future

class CompleteReturnControllerSpec extends BaseUnitSpec {

  private val controller = app.injector.instanceOf[CompleteReturnController]

  val isaManagerReference        = "Z1111"
  val invalidIsaManagerReference = "Z1111XXXXX"
  val returnId                   = "1ada0843-6594-4a46-bcd5-61e90a3acad6"
  val obligation:      EtmpObligations     = EtmpObligations(false)
  val reportingWindow: EtmpReportingWindow = EtmpReportingWindow(true)

  "CompleteReturnsController.complete" should {

    "return 204 for successful request" in {
      val returnSummaryLocation = s"/monthly/$isaManagerReference/$returnId/results/summary"
      val completeResponse      = CompleteReturnResponse(returnSummaryLocation)

      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockMonthlyReportDocumentService.existsByIsaManagerReferenceAndReturnId(isaManagerReference, returnId))
        .thenReturn(Future.successful(true))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Right((reportingWindow, obligation))))
      when(mockETMPService.closeObligationStatus(any())(any()))
        .thenReturn(EitherT.right[ErrorResponse](Future.successful(HttpResponse(200))))
      when(mockCompleteReturnService.validateRecordCount(isaManagerReference, returnId)).thenReturn(Future.successful(Right(completeResponse)))

      val request = FakeRequest(POST, s"/complete/$isaManagerReference/$returnId")
      val result  = controller.complete(isaManagerReference, returnId)(request)

      status(result) shouldBe OK
      val json = contentAsJson(result)
      (json \ "returnResultsSummaryLocation").as[String] shouldBe returnSummaryLocation
    }

    "return 400 BadRequest for an invalid ISA reference number" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      val request = FakeRequest(POST, s"/complete/$invalidIsaManagerReference/$returnId")
      val result  = controller.complete(invalidIsaManagerReference, returnId)(request)

      status(result) shouldBe BAD_REQUEST
      val json = contentAsJson(result)
      (json \ "message").as[String] shouldBe "ISA Manager Reference Number format is invalid"
    }
    "return 404 NotFound when the provided returnId does not exist" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockMonthlyReportDocumentService.existsByIsaManagerReferenceAndReturnId(isaManagerReference, returnId))
        .thenReturn(Future.successful(false))
      val request = FakeRequest(POST, s"/complete/$isaManagerReference/$returnId")
      val result  = controller.complete(isaManagerReference, returnId)(request)

      status(result) shouldBe NOT_FOUND
      val json = contentAsJson(result)
      (json \ "code").as[String]    shouldBe "RETURN_ID_NOT_FOUND"
      (json \ "message").as[String] shouldBe "The provided returnId could not be found"
    }

    "return 403 Forbidden when the reporting window is closed" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockMonthlyReportDocumentService.existsByIsaManagerReferenceAndReturnId(isaManagerReference, returnId)).thenReturn(Future.successful(true))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Left(ReportingWindowClosed)))
      val request = FakeRequest(POST, s"/complete/$isaManagerReference/$returnId")
      val result  = controller.complete(isaManagerReference, returnId)(request)

      status(result) shouldBe FORBIDDEN
      val json = contentAsJson(result)
      (json \ "code").as[String]    shouldBe "REPORTING_WINDOW_CLOSED"
      (json \ "message").as[String] shouldBe "Reporting window has been closed"
    }

    "return 403 Forbidden when the obligation is closed" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockMonthlyReportDocumentService.existsByIsaManagerReferenceAndReturnId(isaManagerReference, returnId)).thenReturn(Future.successful(true))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Left(ObligationClosed)))
      val request = FakeRequest(POST, s"/complete/$isaManagerReference/$returnId")
      val result  = controller.complete(isaManagerReference, returnId)(request)

      status(result) shouldBe FORBIDDEN
      val json = contentAsJson(result)
      (json \ "code").as[String]    shouldBe "OBLIGATION_CLOSED"
      (json \ "message").as[String] shouldBe "Obligation closed"
    }

    "return 400 BadRequest Mismatch when number of records declared in the header does not match the number submitted" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockMonthlyReportDocumentService.existsByIsaManagerReferenceAndReturnId(isaManagerReference, returnId)).thenReturn(Future.successful(true))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Right((reportingWindow, obligation))))
      when(mockCompleteReturnService.validateRecordCount(isaManagerReference, returnId)).thenReturn(Future.successful(Left(MismatchErr)))
      val request = FakeRequest(GET, s"/complete/$isaManagerReference/$returnId")
      val result  = controller.complete(isaManagerReference, returnId)(request)

      status(result) shouldBe BAD_REQUEST
      val json = contentAsJson(result)
      (json \ "code").as[String]    shouldBe "MISMATCH_EXPECTED_VS_RECEIVED"
      (json \ "message").as[String] shouldBe "Number of records declared in the header does not match the number submitted."
    }
  }

  "return 500 Internal Server error when auth throws an exception" in {
    when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.failed(new InternalError()))
    val request = FakeRequest(POST, s"/complete/$isaManagerReference/$returnId")
    val result  = controller.complete(isaManagerReference, returnId)(request)

    status(result) shouldBe INTERNAL_SERVER_ERROR
    val json = contentAsJson(result)
    (json \ "code").as[String]    shouldBe "INTERNAL_SERVER_ERROR"
    (json \ "message").as[String] shouldBe "There has been an issue processing your request"
  }
}
