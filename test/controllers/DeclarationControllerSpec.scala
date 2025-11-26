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
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.disareturns.controllers.DeclarationController
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InternalServerErr, ObligationClosed, ReportingWindowClosed}
import uk.gov.hmrc.disareturns.models.etmp.{EtmpObligations, EtmpReportingWindow}
import uk.gov.hmrc.disareturns.models.helpers.ValidationHelper
import uk.gov.hmrc.http.HttpResponse
import utils.BaseUnitSpec

import scala.concurrent.Future

class DeclarationControllerSpec extends BaseUnitSpec {

  val controller: DeclarationController = app.injector.instanceOf[DeclarationController]

  val clientId = "client-999"
  val boxId    = "box-123"
  val obligation:      EtmpObligations     = EtmpObligations(false)
  val reportingWindow: EtmpReportingWindow = EtmpReportingWindow(true)
  val testUrl              = "http://localhost:9000"
  val invalidTaxYear       = "2011"
  val invalidMonth         = "September"
  val invalidIsaManagerRef = "Z12345454"

  "DeclarationController.declare" should {

    "return 200 OK when the declaration is successful" in {
      authorizationForZRef()
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Right((reportingWindow, obligation))))
      val httpResponse: HttpResponse = HttpResponse(200, "")
      when(mockAppConfig.selfHost).thenReturn(testUrl)
      when(mockETMPService.declaration(any())(any()))
        .thenReturn(EitherT.rightT[Future, ErrorResponse](httpResponse))
      when(mockNPSService.notification(any(), any())(any()))
        .thenReturn(EitherT.rightT[Future, ErrorResponse](httpResponse))
      when(mockPPNSService.getBoxId(any())(any()))
        .thenReturn(Future.successful(Right(Some(boxId))))
      when(notificationContextService.saveContext(any(), any(), any()))
        .thenReturn(Future.successful(Right()))

      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(POST, s"/monthly/$validZRef/$validTaxYear/$validMonth/declaration")
        .withHeaders("X-Client-ID" -> clientId)

      val result = controller.declare(validZRef, validTaxYear, validMonth.toString)(request)

      val summaryLocation = s"$testUrl/monthly/$validZRef/$validTaxYear/$validMonth/results/summary"

      status(result)                                                      shouldBe OK
      (contentAsJson(result) \ "returnResultsSummaryLocation").as[String] shouldBe summaryLocation
      (contentAsJson(result) \ "boxId").as[String]                        shouldBe boxId
    }

    "return 200 OK when the declaration is successful but no boxId has been retrieved from PPNS" in {
      authorizationForZRef()
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Right((reportingWindow, obligation))))
      val httpResponse: HttpResponse = HttpResponse(200, "")
      when(mockAppConfig.selfHost).thenReturn(testUrl)
      when(mockETMPService.declaration(any())(any()))
        .thenReturn(EitherT.rightT[Future, ErrorResponse](httpResponse))
      when(mockNPSService.notification(any(), any())(any()))
        .thenReturn(EitherT.rightT[Future, ErrorResponse](httpResponse))
      when(mockPPNSService.getBoxId(any())(any()))
        .thenReturn(Future.successful(Right(None)))
      when(notificationContextService.saveContext(any(), any(), any()))
        .thenReturn(Future.successful(Right()))

      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(POST, s"/monthly/$validZRef/$validTaxYear/$validMonth/declaration")
        .withHeaders("X-Client-ID" -> clientId)

      val result = controller.declare(validZRef, validTaxYear, validMonth.toString)(request)

      val summaryLocation = s"$testUrl/monthly/$validZRef/$validTaxYear/$validMonth/results/summary"

      status(result)                                                      shouldBe OK
      (contentAsJson(result) \ "returnResultsSummaryLocation").as[String] shouldBe summaryLocation
    }

    "return 400 BadRequest when validation fails for taxYear" in {
      authorizationForZRef()
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(POST, s"/monthly/$validZRef/$invalidTaxYear/$validMonth/declaration")
        .withHeaders("X-Client-ID" -> clientId)
      val result = controller.declare(validZRef, invalidTaxYear, validMonth.toString)(request)
      status(result)        shouldBe BAD_REQUEST
      contentAsJson(result) shouldBe Json.toJson(ValidationHelper.validateParams(validZRef, invalidTaxYear, validMonth.toString).left.toOption.get)
    }

    "return 400 BadRequest when validation fails for month" in {
      authorizationForZRef()
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(POST, s"/monthly/$validZRef/$validTaxYear/$invalidMonth/declaration")
        .withHeaders("X-Client-ID" -> clientId)
      val result = controller.declare(validZRef, validTaxYear, invalidMonth)(request)
      status(result)        shouldBe BAD_REQUEST
      contentAsJson(result) shouldBe Json.toJson(ValidationHelper.validateParams(validZRef, validTaxYear, invalidMonth).left.toOption.get)
    }

    "return 400 BadRequest when validation fails for isaManagerReference" in {
      authorizationForZRef()
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(POST, s"/monthly/$invalidIsaManagerRef/$validTaxYear/$validMonth/declaration")
        .withHeaders("X-Client-ID" -> clientId)
      val result = controller.declare(invalidIsaManagerRef, validTaxYear, validMonth.toString)(request)
      status(result) shouldBe BAD_REQUEST
      contentAsJson(result) shouldBe Json.toJson(
        ValidationHelper.validateParams(invalidIsaManagerRef, validTaxYear, validMonth.toString).left.toOption.get
      )

    }

    "return 400 BadRequest when the clientId is missing from the header" in {
      authorizationForZRef()
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(POST, s"/monthly/$validZRef/$validTaxYear/$validMonth/declaration")
      val result = controller.declare(validZRef, validTaxYear, validMonth.toString)(request)
      status(result) shouldBe BAD_REQUEST
      val json = contentAsJson(result)
      (json \ "code").as[String]    shouldBe "BAD_REQUEST"
      (json \ "message").as[String] shouldBe "Missing required header: X-Client-ID"
    }

    "return 403 Forbidden when the reporting window is closed" in {
      authorizationForZRef()
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Left(ReportingWindowClosed)))

      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(POST, s"/monthly/$validZRef/$validTaxYear/$validMonth/declaration")
        .withHeaders("X-Client-ID" -> clientId)

      val result = controller.declare(validZRef, validTaxYear, validMonth.toString)(request)

      status(result) shouldBe FORBIDDEN
      val json = contentAsJson(result)
      (json \ "code").as[String]    shouldBe "REPORTING_WINDOW_CLOSED"
      (json \ "message").as[String] shouldBe "Reporting window has been closed"

    }

    "return 403 Forbidden when the obligation is closed" in {
      authorizationForZRef()
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Left(ObligationClosed)))

      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(POST, s"/monthly/$validZRef/$validTaxYear/$validMonth/declaration")
        .withHeaders("X-Client-ID" -> clientId)

      val result = controller.declare(validZRef, validTaxYear, validMonth.toString)(request)

      status(result) shouldBe FORBIDDEN
      val json = contentAsJson(result)
      (json \ "code").as[String]    shouldBe "OBLIGATION_CLOSED"
      (json \ "message").as[String] shouldBe "Obligation closed"

    }

    "return 500 Internal Server Error when the call to PPNS fails" in {
      authorizationForZRef()
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Right((reportingWindow, obligation))))
      val httpResponse: HttpResponse = HttpResponse(200, "")
      when(mockETMPService.declaration(any())(any()))
        .thenReturn(EitherT.rightT[Future, ErrorResponse](httpResponse))
      when(mockNPSService.notification(any(), any())(any()))
        .thenReturn(EitherT.rightT[Future, ErrorResponse](httpResponse))
      when(mockPPNSService.getBoxId(any())(any()))
        .thenReturn(Future.successful(Left(InternalServerErr())))

      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(POST, s"/monthly/$validZRef/$validTaxYear/$validMonth/declaration")
        .withHeaders("X-Client-ID" -> clientId)

      val result = controller.declare(validZRef, validTaxYear, validMonth.toString)(request)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      val json = contentAsJson(result)
      (json \ "code").as[String]    shouldBe "INTERNAL_SERVER_ERROR"
      (json \ "message").as[String] shouldBe "There has been an issue processing your request"

    }

    "return 500 Internal Server Error when there is a failure whilst saving the notification context" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Right((reportingWindow, obligation))))
      val httpResponse: HttpResponse = HttpResponse(200, "")
      when(mockETMPService.declaration(any())(any()))
        .thenReturn(EitherT.rightT[Future, ErrorResponse](httpResponse))
      when(mockNPSService.notification(any(), any())(any()))
        .thenReturn(EitherT.rightT[Future, ErrorResponse](httpResponse))
      when(mockPPNSService.getBoxId(any())(any()))
        .thenReturn(Future.successful(Right(Some(boxId))))
      when(notificationContextService.saveContext(any(), any(), any()))
        .thenReturn(Future.successful(Left(InternalServerErr())))

      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(POST, s"/monthly/$validZRef/$validTaxYear/$validMonth/declaration")
        .withHeaders("X-Client-ID" -> clientId)

      val result = controller.declare(validZRef, validTaxYear, validMonth.toString)(request)

      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.toJson(InternalServerErr())

    }
  }
}
