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

package uk.gov.hmrc.disareturns.services

import cats.data.EitherT
import play.api.Logging
import play.api.http.Status.{NO_CONTENT, OK}
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.connectors.NPSConnector
import uk.gov.hmrc.disareturns.models.common.Month.Month
import uk.gov.hmrc.disareturns.models.common.UpstreamErrorMapper.mapToErrorResponse
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InternalServerErr, ReportPageNotFoundErr, ReturnNotFoundErr}
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaAccount
import uk.gov.hmrc.disareturns.models.returnResults.{ReconciliationReportPage, ReconciliationReportResponse, ReturnResults}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NPSService @Inject() (connector: NPSConnector, config: AppConfig)(implicit ec: ExecutionContext) extends Logging {

  def notification(isaManagerReference: String)(implicit hc: HeaderCarrier): EitherT[Future, ErrorResponse, HttpResponse] = {
    logger.info(s"Sending notification to NPS for IM ref: [$isaManagerReference]")
    connector.sendNotification(isaManagerReference).leftMap(mapToErrorResponse)
  }

  def submitIsaAccounts(isaManagerReferenceNumber: String, isaAccounts: Seq[IsaAccount])(implicit
    hc:                                            HeaderCarrier
  ): Future[Either[ErrorResponse, Unit]] = {
    logger.info(s"Submitting ISA Accounts to NPS for IM ref: [$isaManagerReferenceNumber]")

    connector.submit(isaManagerReferenceNumber, isaAccounts).value.map {
      case Left(upstreamError) => Left(mapToErrorResponse(upstreamError))
      case Right(response) =>
        response.status match {
          case NO_CONTENT  => Right(())
          case otherStatus => Left(InternalServerErr(s"Unexpected status $otherStatus was received from NPS submission"))
        }
    }
  }

  def retrieveReconciliationReportPage(isaManagerReferenceNumber: String, taxYear: String, month: Month, pageIndex: Int)(implicit
    hc:                                                           HeaderCarrier
  ): Future[Either[ErrorResponse, ReconciliationReportPage]] = {

    def getPage(report: ReconciliationReportResponse, pageIndex: Int): Either[ErrorResponse, ReconciliationReportPage] = {
      val totalNoOfPages = config.getNoOfPagesForReturnResults(report.returnResults.size)
      val totalRecords   = report.returnResults.size
      val pageSize       = config.returnResultsRecordsPerPage
      val startOfPage    = pageIndex * pageSize

      val onePageOfResults =
        if (startOfPage >= totalRecords || totalRecords == 0) Seq.empty[ReturnResults]
        else report.returnResults.slice(startOfPage, math.min(startOfPage + pageSize, totalRecords))

      if (onePageOfResults.isEmpty) Left(ReportPageNotFoundErr(pageIndex))
      else Right(ReconciliationReportPage(pageIndex, onePageOfResults.size, totalRecords, totalNoOfPages, onePageOfResults))
    }

    logger.info(s"Retrieving reconciliation report from NPS for IM ref: [$isaManagerReferenceNumber] with month/taxYear: [$month] [$taxYear]")

    connector.retrieveReconciliationReport(isaManagerReferenceNumber, taxYear, month).value.map {
      case Left(upstreamError) =>
        Left(
          if (upstreamError.statusCode == 404) ReturnNotFoundErr("Return not found")
          else mapToErrorResponse(upstreamError)
        )
      case Right(response) =>
        response.status match {
          case OK =>
            try getPage(response.json.as[ReconciliationReportResponse], pageIndex)
            catch {
              case e: Throwable => Left(InternalServerErr(e.getMessage))
            }
          case otherStatus => Left(InternalServerErr(s"Unexpected status $otherStatus was received from NPS report retrieval"))
        }
    }
  }
}
