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
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, MultipleErrorResponse, ObligationClosed, ReportingWindowClosed, UnauthorisedErr}
import uk.gov.hmrc.disareturns.models.submission.MonthlyReturnReportingStatus
import uk.gov.hmrc.disareturns.services.ETMPService
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class ETMPServiceSpec extends BaseUnitSpec {

  "ETMPService.validateSubmissionEligibility" should {

    "return Right(MonthlyReturnReportingStatus) when the reporting window is open and the obligation is not closed" in new TestSetup {
      val reportingStatus: MonthlyReturnReportingStatus = MonthlyReturnReportingStatus(reportingWindowOpen = true, obligationClosed = false)

      when(mockReportingStatusService.getReportingStatus(any())(any()))
        .thenReturn(EitherT.rightT[Future, ErrorResponse](reportingStatus))

      val result: Either[ErrorResponse, MonthlyReturnReportingStatus] =
        service.validateSubmissionEligibility(validZReference).futureValue

      result shouldBe Right(reportingStatus)
      verify(mockReportingStatusService).getReportingStatus(validZReference)
    }

    "return Left(ReportingWindowClosed) when the reporting window is closed" in new TestSetup {
      when(mockReportingStatusService.getReportingStatus(any())(any()))
        .thenReturn(EitherT.rightT[Future, ErrorResponse](MonthlyReturnReportingStatus(reportingWindowOpen = false, obligationClosed = false)))

      val result: Either[ErrorResponse, MonthlyReturnReportingStatus] =
        service.validateSubmissionEligibility(validZReference).futureValue

      result shouldBe Left(ReportingWindowClosed)
    }

    "return Left(ObligationClosed) when the reporting window is open but the obligation is closed" in new TestSetup {
      when(mockReportingStatusService.getReportingStatus(any())(any()))
        .thenReturn(EitherT.rightT[Future, ErrorResponse](MonthlyReturnReportingStatus(reportingWindowOpen = true, obligationClosed = true)))

      val result: Either[ErrorResponse, MonthlyReturnReportingStatus] =
        service.validateSubmissionEligibility(validZReference).futureValue

      result shouldBe Left(ObligationClosed)
    }

    "return Left(MultipleErrorResponse) when the reporting window is closed and the obligation is closed" in new TestSetup {
      when(mockReportingStatusService.getReportingStatus(any())(any()))
        .thenReturn(EitherT.rightT[Future, ErrorResponse](MonthlyReturnReportingStatus(reportingWindowOpen = false, obligationClosed = true)))

      val result: Either[ErrorResponse, MonthlyReturnReportingStatus] =
        service.validateSubmissionEligibility(validZReference).futureValue

      result shouldBe Left(MultipleErrorResponse(code = "FORBIDDEN", errors = Seq(ReportingWindowClosed, ObligationClosed)))
    }

    "return Left(error) when the reporting status service fails" in new TestSetup {
      when(mockReportingStatusService.getReportingStatus(any())(any()))
        .thenReturn(EitherT.leftT[Future, MonthlyReturnReportingStatus](UnauthorisedErr))

      val result: Either[ErrorResponse, MonthlyReturnReportingStatus] =
        service.validateSubmissionEligibility(validZReference).futureValue

      result shouldBe Left(UnauthorisedErr)
    }
  }

  "ETMPService.closeObligationStatus" should {
    "return Right(HttpResponse(200)) when call to ETMP connector is successful" in new TestSetup {
      val httpResponse: HttpResponse = HttpResponse(200, "")

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
    val service: ETMPService = new ETMPService(mockETMPConnector, mockReportingStatusService)
  }
}
