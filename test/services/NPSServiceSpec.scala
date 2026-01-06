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

package services

import cats.data.EitherT
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.http.Status.{NO_CONTENT, OK}
import play.api.libs.json.Json
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaAccount
import uk.gov.hmrc.disareturns.models.returnResults.{IssueWithMessage, ReconciliationReportPage, ReconciliationReportResponse, ReturnResults}
import uk.gov.hmrc.disareturns.services.NPSService
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class NPSServiceSpec extends BaseUnitSpec {

  val service            = new NPSService(mockNPSConnector, mockAppConfig)
  val reportingNilReturn = false

  "NPSService.notification" should {

    "return Right(HttpResponse) when connector returns a 204" in {
      val httpResponse = HttpResponse(204, "")
      when(mockNPSConnector.sendNotification(validZReference, reportingNilReturn))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val result = service.notification(validZReference, reportingNilReturn).value.futureValue

      result shouldBe Right(httpResponse)
    }

    "return Left(ErrorResponse) when nps connector  returns an UpstreamErrorResponse" in {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
        headers = Map.empty
      )

      when(mockNPSConnector.sendNotification(validZReference, reportingNilReturn))
        .thenReturn(EitherT.leftT[Future, HttpResponse](exception))

      val result = service.notification(validZReference, reportingNilReturn).value.futureValue

      result shouldBe Left(UnauthorisedErr)
    }
  }

  "NPSService.submitIsaAccounts" should {

    "return Right(()) when connector responds with 204 NO_CONTENT" in {
      val httpResponse: HttpResponse = HttpResponse(NO_CONTENT, "")

      when(mockNPSConnector.submit(validZReference, Seq.empty[IsaAccount]))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val result: Either[ErrorResponse, Unit] =
        service.submitIsaAccounts(validZReference, Seq.empty).futureValue

      result shouldBe Right(())
    }

    "return Left(UnauthorisedErr) when connector fails with 401 UpstreamErrorResponse" in {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
        headers = Map.empty
      )

      when(mockNPSConnector.submit(validZReference, Seq.empty[IsaAccount]))
        .thenReturn(EitherT.leftT[Future, HttpResponse](exception))

      val result: Either[ErrorResponse, Unit] =
        service.submitIsaAccounts(validZReference, Seq.empty).futureValue

      result shouldBe Left(UnauthorisedErr)
    }

    "return Left(InternalServerErr) when a non-204 success status is returned (e.g. 200 OK)" in {
      val httpResponse: HttpResponse = HttpResponse(OK, "ignored body")

      when(mockNPSConnector.submit(validZReference, Seq.empty[IsaAccount]))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val result: Either[ErrorResponse, Unit] =
        service.submitIsaAccounts(validZReference, Seq.empty).futureValue

      result shouldBe Left(InternalServerErr())
    }
  }

  "NPSService.retrieveReconciliationReportPage" should {

    val totalRecords         = 10
    val returnResultsPerPage = 3
    val numberOfPages        = Some(4)

    "return correct reconciliation report page when connector responds with 200" in {
      val validReconciliationReportResponse = Json
        .toJson(
          ReconciliationReportResponse(
            totalRecords,
            Seq(
              ReturnResults("1", "a", IssueWithMessage("code", "message")),
              ReturnResults("2", "b", IssueWithMessage("code", "message"))
            )
          )
        )
        .toString()

      val httpResponse: HttpResponse = HttpResponse(OK, validReconciliationReportResponse)

      when(mockAppConfig.returnResultsRecordsPerPage).thenReturn(returnResultsPerPage)
      when(mockAppConfig.getNoOfPagesForReturnResults(any)).thenReturn(numberOfPages)
      when(mockNPSConnector.retrieveReconciliationReportPage(eqTo(validZReference), eqTo(validTaxYear), eqTo(validMonth), eqTo(0), eqTo(3))(any))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val pageIndex = 0

      val result: Either[ErrorResponse, ReconciliationReportPage] =
        service.retrieveReconciliationReportPage(validZReference, validTaxYear, validMonth, pageIndex).futureValue

      result shouldBe Right(
        ReconciliationReportPage(
          pageIndex,
          2,
          totalRecords,
          4,
          Seq(
            ReturnResults("1", "a", IssueWithMessage("code", "message")),
            ReturnResults("2", "b", IssueWithMessage("code", "message"))
          )
        )
      )
    }

    "return page not found error when return results come back empty" in {
      val emptyReconciliationReportResponse = Json.toJson(ReconciliationReportResponse(totalRecords, Nil)).toString()

      val httpResponse: HttpResponse = HttpResponse(OK, emptyReconciliationReportResponse)

      when(mockAppConfig.returnResultsRecordsPerPage).thenReturn(returnResultsPerPage)
      when(mockAppConfig.getNoOfPagesForReturnResults(any)).thenReturn(numberOfPages)
      when(mockNPSConnector.retrieveReconciliationReportPage(eqTo(validZReference), eqTo(validTaxYear), eqTo(validMonth), eqTo(0), eqTo(3))(any))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val pageIndex = 0

      val result: Either[ErrorResponse, ReconciliationReportPage] =
        service.retrieveReconciliationReportPage(validZReference, validTaxYear, validMonth, pageIndex).futureValue

      result shouldBe Left(ReportPageNotFoundErr(pageIndex))
    }

    "return 'page not found' error when upstream returns page not found" in {
      val errorResponse = UpstreamErrorResponse("PAGE_NOT_FOUND", 404)

      when(mockAppConfig.returnResultsRecordsPerPage).thenReturn(returnResultsPerPage)
      when(mockAppConfig.getNoOfPagesForReturnResults(any)).thenReturn(numberOfPages)
      when(mockNPSConnector.retrieveReconciliationReportPage(eqTo(validZReference), eqTo(validTaxYear), eqTo(validMonth), eqTo(0), eqTo(3))(any))
        .thenReturn(EitherT.leftT[Future, HttpResponse](errorResponse))

      val pageIndex = 0

      val result: Either[ErrorResponse, ReconciliationReportPage] =
        service.retrieveReconciliationReportPage(validZReference, validTaxYear, validMonth, pageIndex).futureValue

      result shouldBe Left(ReportPageNotFoundErr(pageIndex))
    }

    "return 'report not found' error when upstream returns report not found" in {
      val errorResponse = UpstreamErrorResponse("REPORT_NOT_FOUND", 404)

      when(mockAppConfig.returnResultsRecordsPerPage).thenReturn(returnResultsPerPage)
      when(mockAppConfig.getNoOfPagesForReturnResults(any)).thenReturn(numberOfPages)
      when(mockNPSConnector.retrieveReconciliationReportPage(eqTo(validZReference), eqTo(validTaxYear), eqTo(validMonth), eqTo(0), eqTo(3))(any))
        .thenReturn(EitherT.leftT[Future, HttpResponse](errorResponse))

      val pageIndex = 0

      val result: Either[ErrorResponse, ReconciliationReportPage] =
        service.retrieveReconciliationReportPage(validZReference, validTaxYear, validMonth, pageIndex).futureValue

      result shouldBe Left(ReportNotFoundErr)
    }

    "return internal server error when there are an invalid number of total records" in {
      val validReconciliationReportResponse = Json
        .toJson(
          ReconciliationReportResponse(
            -1,
            Seq(
              ReturnResults("1", "a", IssueWithMessage("code", "message")),
              ReturnResults("2", "b", IssueWithMessage("code", "message"))
            )
          )
        )
        .toString()

      val httpResponse: HttpResponse = HttpResponse(NO_CONTENT, validReconciliationReportResponse)

      when(mockAppConfig.returnResultsRecordsPerPage).thenReturn(returnResultsPerPage)
      when(mockAppConfig.getNoOfPagesForReturnResults(any)).thenReturn(numberOfPages)
      when(mockNPSConnector.retrieveReconciliationReportPage(eqTo(validZReference), eqTo(validTaxYear), eqTo(validMonth), eqTo(0), eqTo(3))(any))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val pageIndex = 0

      val result: Either[ErrorResponse, ReconciliationReportPage] =
        service.retrieveReconciliationReportPage(validZReference, validTaxYear, validMonth, pageIndex).futureValue

      result shouldBe Left(InternalServerErr())
    }

    "return internal server error when unexpected status comes through" in {
      val validReconciliationReportResponse = Json
        .toJson(
          ReconciliationReportResponse(
            totalRecords,
            Seq(
              ReturnResults("1", "a", IssueWithMessage("code", "message")),
              ReturnResults("2", "b", IssueWithMessage("code", "message"))
            )
          )
        )
        .toString()

      val httpResponse: HttpResponse = HttpResponse(NO_CONTENT, validReconciliationReportResponse)

      when(mockAppConfig.returnResultsRecordsPerPage).thenReturn(returnResultsPerPage)
      when(mockAppConfig.getNoOfPagesForReturnResults(any)).thenReturn(numberOfPages)
      when(mockNPSConnector.retrieveReconciliationReportPage(eqTo(validZReference), eqTo(validTaxYear), eqTo(validMonth), eqTo(0), eqTo(3))(any))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val pageIndex = 0

      val result: Either[ErrorResponse, ReconciliationReportPage] =
        service.retrieveReconciliationReportPage(validZReference, validTaxYear, validMonth, pageIndex).futureValue

      result shouldBe Left(InternalServerErr())
    }

    "return internal server error when response has invalid json" in {
      val httpResponse: HttpResponse = HttpResponse(OK, "bad json")

      when(mockAppConfig.returnResultsRecordsPerPage).thenReturn(returnResultsPerPage)
      when(mockAppConfig.getNoOfPagesForReturnResults(any)).thenReturn(numberOfPages)
      when(mockNPSConnector.retrieveReconciliationReportPage(eqTo(validZReference), eqTo(validTaxYear), eqTo(validMonth), eqTo(0), eqTo(3))(any))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val pageIndex = 0

      val result: Either[ErrorResponse, ReconciliationReportPage] =
        service.retrieveReconciliationReportPage(validZReference, validTaxYear, validMonth, pageIndex).futureValue

      result                    shouldBe a[Left[InternalServerErr, ReconciliationReportPage]]
      result.swap.value.message shouldBe InternalServerErr().message
    }
  }
}
