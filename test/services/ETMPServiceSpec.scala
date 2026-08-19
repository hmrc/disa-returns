/*
 * Copyright 2023 HM Revenue & Customs
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

package services

import cats.data.EitherT
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InternalServerErr, MultipleErrorResponse, ObligationClosed, ReportingWindowClosed, UnauthorisedErr}
import uk.gov.hmrc.disareturns.models.etmp.EtmpObligations
import uk.gov.hmrc.disareturns.models.submission.ReportingWindowStatus
import uk.gov.hmrc.disareturns.services.ETMPService
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class ETMPServiceSpec extends BaseUnitSpec {

  "ETMPService.checkObligationStatus" should {

    "return Right(EtmpObligations) when call to ETMP connector returns an obligation status" in new TestSetup {
      val expectedResponse:    EtmpObligations = EtmpObligations(true)
      val etmpObligationsJson: JsValue         = Json.toJson(expectedResponse)
      val httpResponse:        HttpResponse    = HttpResponse(200, etmpObligationsJson.toString())

      when(mockETMPConnector.getReturnsObligationStatus(validZReference))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val result: Either[ErrorResponse, EtmpObligations] = service.getObligationStatus(validZReference).value.futureValue

      result shouldBe Right(expectedResponse)
    }

    "return Left(UpstreamErrorResponse) when the call to ETMP connector fails with an UpstreamErrorResponse" in new TestSetup {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
        headers = Map.empty
      )

      when(mockETMPConnector.getReturnsObligationStatus(validZReference))
        .thenReturn(EitherT.leftT[Future, HttpResponse](exception))

      val result: Either[ErrorResponse, EtmpObligations] = service.getObligationStatus(validZReference).value.futureValue

      result shouldBe Left(UnauthorisedErr)
    }
  }

  "ETMPService.checkReportingWindowStatus" should {

    "return Left(InternalServerErr) when the response JSON cannot be parsed into a obligation status" in new TestSetup {
      val obligation: String = """{
                                 |  "obligationAlreadyMet": "true"
                                 |}""".stripMargin

      val httpResponse: HttpResponse = HttpResponse(200, obligation)

      when(mockETMPConnector.getReturnsObligationStatus(validZReference))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val result: Either[ErrorResponse, EtmpObligations] = service.getObligationStatus(validZReference).value.futureValue

      result shouldBe Left(InternalServerErr())
    }
  }

  "ETMPService.validateEtmpSubmissionEligibility" should {

    "return Right((ReportingWindowStatus, EtmpObligations)) when the reporting window is open and the obligation is not met" in new TestSetup {
      val reportingWindow: ReportingWindowStatus = ReportingWindowStatus(reportingWindowOpen = true)
      val obligations:     EtmpObligations       = EtmpObligations(obligationAlreadyMet = false)

      when(mockReportingWindowService.getReportingWindowStatus(any())(any()))
        .thenReturn(EitherT.rightT[Future, ErrorResponse](reportingWindow))
      when(mockETMPConnector.getReturnsObligationStatus(validZReference))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(200, Json.toJson(obligations).toString())))

      val result: Either[ErrorResponse, (ReportingWindowStatus, EtmpObligations)] =
        service.validateEtmpSubmissionEligibility(validZReference, testCredentialId).futureValue

      result shouldBe Right((reportingWindow, obligations))
    }

    "return Left(ReportingWindowClosed) when the reporting window is closed" in new TestSetup {
      val reportingWindow: ReportingWindowStatus = ReportingWindowStatus(reportingWindowOpen = false)
      val obligations:     EtmpObligations       = EtmpObligations(obligationAlreadyMet = false)

      when(mockReportingWindowService.getReportingWindowStatus(any())(any()))
        .thenReturn(EitherT.rightT[Future, ErrorResponse](reportingWindow))
      when(mockETMPConnector.getReturnsObligationStatus(validZReference))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(200, Json.toJson(obligations).toString())))

      val result: Either[ErrorResponse, (ReportingWindowStatus, EtmpObligations)] =
        service.validateEtmpSubmissionEligibility(validZReference, testCredentialId).futureValue

      result shouldBe Left(ReportingWindowClosed)
    }

    "return Left(ObligationClosed) when the reporting window is open but the obligation is already met" in new TestSetup {
      val reportingWindow: ReportingWindowStatus = ReportingWindowStatus(reportingWindowOpen = true)
      val obligations:     EtmpObligations       = EtmpObligations(obligationAlreadyMet = true)

      when(mockReportingWindowService.getReportingWindowStatus(any())(any()))
        .thenReturn(EitherT.rightT[Future, ErrorResponse](reportingWindow))
      when(mockETMPConnector.getReturnsObligationStatus(validZReference))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(200, Json.toJson(obligations).toString())))

      val result: Either[ErrorResponse, (ReportingWindowStatus, EtmpObligations)] =
        service.validateEtmpSubmissionEligibility(validZReference, testCredentialId).futureValue

      result shouldBe Left(ObligationClosed)
    }

    "return Left(MultipleErrorResponse) when the reporting window is closed and the obligation is already met" in new TestSetup {
      val reportingWindow: ReportingWindowStatus = ReportingWindowStatus(reportingWindowOpen = false)
      val obligations:     EtmpObligations       = EtmpObligations(obligationAlreadyMet = true)

      when(mockReportingWindowService.getReportingWindowStatus(any())(any()))
        .thenReturn(EitherT.rightT[Future, ErrorResponse](reportingWindow))
      when(mockETMPConnector.getReturnsObligationStatus(validZReference))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(200, Json.toJson(obligations).toString())))

      val result: Either[ErrorResponse, (ReportingWindowStatus, EtmpObligations)] =
        service.validateEtmpSubmissionEligibility(validZReference, testCredentialId).futureValue

      result shouldBe Left(MultipleErrorResponse(code = "FORBIDDEN", errors = Seq(ReportingWindowClosed, ObligationClosed)))
    }

    "return Left(error) when the reporting window service fails" in new TestSetup {
      when(mockReportingWindowService.getReportingWindowStatus(any())(any()))
        .thenReturn(EitherT.leftT[Future, ReportingWindowStatus](UnauthorisedErr))
      when(mockETMPConnector.getReturnsObligationStatus(validZReference))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(200, Json.toJson(EtmpObligations(false)).toString())))

      val result: Either[ErrorResponse, (ReportingWindowStatus, EtmpObligations)] =
        service.validateEtmpSubmissionEligibility(validZReference, testCredentialId).futureValue

      result shouldBe Left(UnauthorisedErr)
    }
  }

  "ETMPService.closeObligationStatus" should {
    "return Right(HttpResponse(200)) when call to ETMP connector is successful" in new TestSetup {
      val expectedResponse:    EtmpObligations = EtmpObligations(true)
      val etmpObligationsJson: JsValue         = Json.toJson(expectedResponse)
      val httpResponse:        HttpResponse    = HttpResponse(200, etmpObligationsJson.toString())

      when(mockETMPConnector.sendDeclaration(validZReference))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val result: Either[ErrorResponse, HttpResponse] = service.declaration(validZReference).value.futureValue

      result shouldBe Right(httpResponse)
    }
  }
  "ETMPService.closeObligationStatus" should {
    "return Left(UpstreamErrorResponse) when the call to ETMP connector returns an UpstreamErrorResponse" in new TestSetup {

      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
        headers = Map.empty
      )

      when(mockETMPConnector.sendDeclaration(validZReference))
        .thenReturn(EitherT.leftT[Future, HttpResponse](exception))

      val result: Either[ErrorResponse, HttpResponse] = service.declaration(validZReference).value.futureValue

      result shouldBe Left(UnauthorisedErr)
    }
  }

  trait TestSetup {
    val service: ETMPService = new ETMPService(mockETMPConnector, mockReportingWindowService)
  }
}
