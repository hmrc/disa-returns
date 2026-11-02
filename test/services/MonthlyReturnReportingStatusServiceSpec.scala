/*
 * Copyright 2026 HM Revenue & Customs
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
import org.mockito.Mockito._
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InternalServerErr, UnauthorisedErr}
import uk.gov.hmrc.disareturns.models.submission.MonthlyReturnReportingStatus
import uk.gov.hmrc.disareturns.services.MonthlyReturnReportingStatusService
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class MonthlyReturnReportingStatusServiceSpec extends BaseUnitSpec {

  "MonthlyReturnReportingStatusService.getReportingStatus" should {

    "return Right(MonthlyReturnReportingStatus), ignoring additional fields, when disa-returns-submission returns a reporting status" in new TestSetup {
      val responseBody: String =
        """{
          |  "reportingWindowOpen": true,
          |  "reportingWindowStart": "2026-06-06T00:00:00Z",
          |  "reportingWindowEnd": "2026-06-19T23:59:59Z",
          |  "resolvedAt": "2026-06-12T00:00:00Z",
          |  "taxYear": "2026-27",
          |  "month": 5,
          |  "obligationClosed": false,
          |  "monthlyReturnDeclared": false,
          |  "nilReturn": false
          |}""".stripMargin

      when(mockSubmissionConnector.getMonthlyReturnReportingStatus(validZReference))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(200, responseBody)))

      val result: Either[ErrorResponse, MonthlyReturnReportingStatus] = service.getReportingStatus(validZReference).value.futureValue

      result shouldBe Right(MonthlyReturnReportingStatus(reportingWindowOpen = true, obligationClosed = false))
    }

    "return Left(UnauthorisedErr) when the call to disa-returns-submission returns an UpstreamErrorResponse" in new TestSetup {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
        headers = Map.empty
      )

      when(mockSubmissionConnector.getMonthlyReturnReportingStatus(validZReference))
        .thenReturn(EitherT.leftT[Future, HttpResponse](exception))

      val result: Either[ErrorResponse, MonthlyReturnReportingStatus] = service.getReportingStatus(validZReference).value.futureValue

      result shouldBe Left(UnauthorisedErr)
    }

    "return Left(InternalServerErr) when disa-returns-submission returns a 500" in new TestSetup {
      when(mockSubmissionConnector.getMonthlyReturnReportingStatus(validZReference))
        .thenReturn(EitherT.leftT[Future, HttpResponse](UpstreamErrorResponse("failed", 500, 500)))

      val result: Either[ErrorResponse, MonthlyReturnReportingStatus] = service.getReportingStatus(validZReference).value.futureValue

      result shouldBe Left(InternalServerErr())
    }

    "return Left(InternalServerErr) when the response JSON cannot be parsed into a reporting status" in new TestSetup {
      val responseBody: String =
        """{
          |  "reportingWindowOpen": "false",
          |  "obligationClosed": "false"
          |}""".stripMargin

      when(mockSubmissionConnector.getMonthlyReturnReportingStatus(validZReference))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(200, responseBody)))

      val result: Either[ErrorResponse, MonthlyReturnReportingStatus] = service.getReportingStatus(validZReference).value.futureValue

      result shouldBe Left(InternalServerErr())
    }
  }

  trait TestSetup {
    val service: MonthlyReturnReportingStatusService = new MonthlyReturnReportingStatusService(mockSubmissionConnector)
  }
}
