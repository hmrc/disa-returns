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
import uk.gov.hmrc.disareturns.services.MonthlyReturnEligibilityService
import utils.BaseUnitSpec

import scala.concurrent.Future

class MonthlyReturnEligibilityServiceSpec extends BaseUnitSpec {

  "MonthlyReturnEligibilityService.validateSubmissionEligibility" should {

    "return Right(()) when the reporting window is open and the obligation is not closed" in new TestSetup {
      when(mockReportingStatusService.getReportingStatus(any())(any()))
        .thenReturn(
          EitherT.rightT[Future, ErrorResponse](
            MonthlyReturnReportingStatus(reportingWindowOpen = true, obligationClosed = false, monthlyReturnDeclared = false)
          )
        )

      val result: Either[ErrorResponse, Unit] =
        service.validateSubmissionEligibility(validZReference).futureValue

      result shouldBe Right(())
      verify(mockReportingStatusService).getReportingStatus(validZReference)
    }

    "return Left(ReportingWindowClosed) when the reporting window is closed" in new TestSetup {
      when(mockReportingStatusService.getReportingStatus(any())(any()))
        .thenReturn(
          EitherT.rightT[Future, ErrorResponse](
            MonthlyReturnReportingStatus(reportingWindowOpen = false, obligationClosed = false, monthlyReturnDeclared = false)
          )
        )

      val result: Either[ErrorResponse, Unit] =
        service.validateSubmissionEligibility(validZReference).futureValue

      result shouldBe Left(ReportingWindowClosed)
    }

    "return Left(ObligationClosed) when the reporting window is open but the obligation is closed" in new TestSetup {
      when(mockReportingStatusService.getReportingStatus(any())(any()))
        .thenReturn(
          EitherT.rightT[Future, ErrorResponse](
            MonthlyReturnReportingStatus(reportingWindowOpen = true, obligationClosed = true, monthlyReturnDeclared = false)
          )
        )

      val result: Either[ErrorResponse, Unit] =
        service.validateSubmissionEligibility(validZReference).futureValue

      result shouldBe Left(ObligationClosed)
    }

    "return Left(MultipleErrorResponse) when the reporting window is closed and the obligation is closed" in new TestSetup {
      when(mockReportingStatusService.getReportingStatus(any())(any()))
        .thenReturn(
          EitherT.rightT[Future, ErrorResponse](
            MonthlyReturnReportingStatus(reportingWindowOpen = false, obligationClosed = true, monthlyReturnDeclared = false)
          )
        )

      val result: Either[ErrorResponse, Unit] =
        service.validateSubmissionEligibility(validZReference).futureValue

      result shouldBe Left(MultipleErrorResponse(code = "FORBIDDEN", errors = Seq(ReportingWindowClosed, ObligationClosed)))
    }

    "return Left(ObligationClosed) when the monthly return has been declared" in new TestSetup {
      when(mockReportingStatusService.getReportingStatus(any())(any()))
        .thenReturn(
          EitherT.rightT[Future, ErrorResponse](
            MonthlyReturnReportingStatus(reportingWindowOpen = true, obligationClosed = false, monthlyReturnDeclared = true)
          )
        )

      val result: Either[ErrorResponse, Unit] =
        service.validateSubmissionEligibility(validZReference).futureValue

      result shouldBe Left(ObligationClosed)
    }

    "return a single Left(ObligationClosed) when the obligation is closed and the monthly return has been declared" in new TestSetup {
      when(mockReportingStatusService.getReportingStatus(any())(any()))
        .thenReturn(
          EitherT.rightT[Future, ErrorResponse](
            MonthlyReturnReportingStatus(reportingWindowOpen = true, obligationClosed = true, monthlyReturnDeclared = true)
          )
        )

      val result: Either[ErrorResponse, Unit] =
        service.validateSubmissionEligibility(validZReference).futureValue

      result shouldBe Left(ObligationClosed)
    }

    "return Left(MultipleErrorResponse) when the reporting window is closed and the monthly return has been declared" in new TestSetup {
      when(mockReportingStatusService.getReportingStatus(any())(any()))
        .thenReturn(
          EitherT.rightT[Future, ErrorResponse](
            MonthlyReturnReportingStatus(reportingWindowOpen = false, obligationClosed = false, monthlyReturnDeclared = true)
          )
        )

      val result: Either[ErrorResponse, Unit] =
        service.validateSubmissionEligibility(validZReference).futureValue

      result shouldBe Left(MultipleErrorResponse(code = "FORBIDDEN", errors = Seq(ReportingWindowClosed, ObligationClosed)))
    }

    "return Left(error) when the reporting status service fails" in new TestSetup {
      when(mockReportingStatusService.getReportingStatus(any())(any()))
        .thenReturn(EitherT.leftT[Future, MonthlyReturnReportingStatus](UnauthorisedErr))

      val result: Either[ErrorResponse, Unit] =
        service.validateSubmissionEligibility(validZReference).futureValue

      result shouldBe Left(UnauthorisedErr)
    }
  }

  trait TestSetup {
    val service: MonthlyReturnEligibilityService = new MonthlyReturnEligibilityService(mockReportingStatusService)
  }
}
