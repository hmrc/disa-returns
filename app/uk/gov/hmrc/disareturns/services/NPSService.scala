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
import play.api.http.Status.OK
import uk.gov.hmrc.disareturns.connectors.NPSConnector
import uk.gov.hmrc.disareturns.models.common.Month.Month
import uk.gov.hmrc.disareturns.utils.UpstreamErrorMapper.mapToErrorResponse
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InternalServerErr, InvalidCursorErr, ReportNotFoundErr}
import uk.gov.hmrc.disareturns.models.returnResults.{ReconciliationReport, ReconciliationReportResponse}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NPSService @Inject() (connector: NPSConnector, cursorCrypto: CursorCrypto)(implicit ec: ExecutionContext) extends Logging {

  def notification(zReference: String, nilReturnReported: Boolean)(implicit
    hc:                        HeaderCarrier
  ): EitherT[Future, ErrorResponse, HttpResponse] = {
    logger.info(s"[NPSService][notification] Sending notification to NPS for IM ref: [$zReference]")
    connector.sendNotification(zReference, nilReturnReported).leftMap(mapToErrorResponse)
  }

  def retrieveReconciliationReport(zReference: String, taxYear: String, month: Month, cursor: Option[String], limit: Int)(implicit
    hc:                                        HeaderCarrier
  ): Future[Either[ErrorResponse, ReconciliationReport]] = {
    logger.info(
      s"[NPSService][retrieveReconciliationReport] Retrieving reconciliation report from NPS for IM ref: [$zReference] with month/taxYear: [$month] [$taxYear]"
    )

    val decryptedCursor = cursor.fold[Either[ErrorResponse, Option[String]]](Right(None))(
      cursorCrypto.decrypt(_, zReference, taxYear, month.toString, limit).map(Some(_))
    )

    decryptedCursor.fold(
      error => Future.successful(Left(error)),
      npsCursor =>
        connector.retrieveReconciliationReport(zReference, taxYear, month, npsCursor, limit).value.map {
          case Left(upstreamError) =>
            Left(
              upstreamError.message match {
                case message if message.contains("REPORT_NOT_FOUND") => ReportNotFoundErr
                case message if message.contains("INVALID_CURSOR")   => InvalidCursorErr
                case _                                               => mapToErrorResponse(upstreamError)
              }
            )
          case Right(response) =>
            response.status match {
              case OK =>
                try {
                  val report = response.json.as[ReconciliationReportResponse]
                  Right(
                    ReconciliationReport(
                      report.returnResults,
                      report.nextCursor.map(cursorCrypto.encrypt(_, zReference, taxYear, month.toString, limit))
                    )
                  )
                } catch {
                  case e: Throwable =>
                    logger.error(
                      s"[NPSService][retrieveReconciliationReport] Caught exception with message: [${e.getMessage}] when parsing response from NPS for IM ref: [$zReference] with month/taxYear: [$month] [$taxYear]"
                    )
                    Left(InternalServerErr())
                }
              case otherStatus =>
                logger.error(
                  s"[NPSService][retrieveReconciliationReport] Unexpected status: [$otherStatus] was received from NPS report retrieval for IM ref: [$zReference] with month/taxYear: [$month] [$taxYear]"
                )
                Left(InternalServerErr())
            }
        }
    )
  }
}
