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
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InternalServerErr, ReportNotFoundErr, ReportPageNotFoundErr}
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaAccount
import uk.gov.hmrc.disareturns.models.returnResults.{ReconciliationReportPage, ReconciliationReportResponse}
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
          case NO_CONTENT => Right(())
          case _          => Left(InternalServerErr())
        }
    }
  }

  def retrieveReconciliationReportPage(isaManagerReferenceNumber: String, taxYear: String, month: Month, pageIndex: Int)(implicit
    hc:                                                           HeaderCarrier
  ): Future[Either[ErrorResponse, ReconciliationReportPage]] = {

    def convertResponseToPage(response: ReconciliationReportResponse): Either[ErrorResponse, ReconciliationReportPage] = {
      val totalRecords      = response.totalRecords
      val totalNoOfPages    = config.getNoOfPagesForReturnResults(totalRecords)
      val recordsInThisPage = response.returnResults.size

      if (response.returnResults.isEmpty) Left(ReportPageNotFoundErr(pageIndex))
      else
        totalNoOfPages.fold[Either[ErrorResponse, ReconciliationReportPage]] {
          logger.error(
            s"Invalid number of total records: [$totalRecords] received from upstream for IM Ref: [$isaManagerReferenceNumber] for [$taxYear] [$month]"
          )
          Left(InternalServerErr())
        } { noOfPages =>
          Right(ReconciliationReportPage(pageIndex, recordsInThisPage, totalRecords, noOfPages, response.returnResults))
        }
    }

    logger.info(
      s"Retrieving reconciliation report page: [$pageIndex] from NPS for IM ref: [$isaManagerReferenceNumber] with month/taxYear: [$month] [$taxYear]"
    )

    val pageSize = config.returnResultsRecordsPerPage

    connector.retrieveReconciliationReportPage(isaManagerReferenceNumber, taxYear, month, pageIndex, pageSize).value.map {
      case Left(upstreamError) =>
        Left(
          upstreamError.message match {
            case message if message.contains("REPORT_NOT_FOUND") => ReportNotFoundErr
            case message if message.contains("PAGE_NOT_FOUND")   => ReportPageNotFoundErr(pageIndex)
            case _                                               => mapToErrorResponse(upstreamError)
          }
        )
      case Right(response) =>
        response.status match {
          case OK =>
            try convertResponseToPage(response.json.as[ReconciliationReportResponse])
            catch {
              case e: Throwable =>
                logger.error(
                  s"Caught exception with message: [${e.getMessage}] when parsing response from NPS for IM ref: [$isaManagerReferenceNumber] with month/taxYear: [$month] [$taxYear]"
                )
                Left(InternalServerErr())
            }
          case otherStatus =>
            logger.error(
              s"Unexpected status: [$otherStatus] was received from NPS report retrieval for IM ref: [$isaManagerReferenceNumber] with month/taxYear: [$month] [$taxYear]"
            )
            Left(InternalServerErr())
        }
    }
  }
}
