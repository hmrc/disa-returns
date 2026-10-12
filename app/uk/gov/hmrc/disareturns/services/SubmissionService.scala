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
import org.apache.pekko.stream.scaladsl.FileIO
import play.api.Logging
import play.api.http.Status.CONFLICT
import uk.gov.hmrc.disareturns.connectors.SubmissionConnector
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, ReportingPeriod, UserDetails}
import uk.gov.hmrc.disareturns.models.common.Month.Month
import uk.gov.hmrc.disareturns.utils.UpstreamErrorMapper.mapToErrorResponse
import uk.gov.hmrc.disareturns.utils.UuidGenerator
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import java.nio.file.Path
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

@Singleton
class SubmissionService @Inject() (connector: SubmissionConnector, uuidGenerator: UuidGenerator, auditService: AuditService)(implicit
  ec:                                         ExecutionContext
) extends Logging {

  def declare(zReference: String, taxYear: String, month: Month, nilReturnReported: Boolean)(implicit
    hc:                   HeaderCarrier
  ): EitherT[Future, ErrorResponse, HttpResponse] = {
    logger.info(
      s"[SubmissionService][declare] Sending declaration to disa-returns-submission for IM ref: [$zReference], taxYear: [$taxYear], month: [$month], nilReturn: [$nilReturnReported]"
    )
    connector.sendDeclaration(zReference, taxYear, month, nilReturnReported).leftMap(mapToErrorResponse)
  }

  def submitMonthlyReturn(zReference: String, userDetails: UserDetails, taxYear: String, month: Month, path: Path)(implicit
    hc:                               HeaderCarrier
  ): Future[Either[ErrorResponse, Unit]] = {
    logger.info(
      s"[SubmissionService][submitMonthlyReturn] Sending monthly return to disa-returns-submission for IM ref: [$zReference], taxYear: [$taxYear], month: [$month]"
    )
    connector.createMonthlyReturn(zReference, taxYear, month, nilReturn = false).flatMap {
      case Left(UpstreamErrorResponse(_, CONFLICT, _, _)) =>
        logger.info(
          s"[SubmissionService][submitMonthlyReturn] Monthly return already exists for IM ref: [$zReference], taxYear: [$taxYear], month: [$month] - proceeding to store submission data"
        )
        sendSubmission(zReference, taxYear, month, path)
      case Left(upstreamError) => Future.successful(Left(mapToErrorResponse(upstreamError)))
      case Right(response) =>
        auditUploadStarted(zReference, userDetails, ReportingPeriod(taxYear, month), response)
        sendSubmission(zReference, taxYear, month, path)
    }
  }

  private def auditUploadStarted(zReference: String, userDetails: UserDetails, reportingPeriod: ReportingPeriod, createResponse: HttpResponse)(
    implicit hc:                             HeaderCarrier
  ): Unit =
    Try((createResponse.json \ "submissionId").as[String]).toOption match {
      case Some(internalReturnId) =>
        auditService
          .auditApiUploadStarted(zReference, userDetails, reportingPeriod, internalReturnId)
          .recover { case NonFatal(e) =>
            logger.warn(s"[SubmissionService][auditUploadStarted] Failed to audit APIUploadStarted for IM ref: [$zReference]", e)
          }
      case None =>
        logger.warn(
          s"[SubmissionService][auditUploadStarted] APIUploadStarted not audited for IM ref: [$zReference], no submissionId in create monthly return response"
        )
    }

  private def sendSubmission(zReference: String, taxYear: String, month: Month, path: Path)(implicit
    hc:                                  HeaderCarrier
  ): Future[Either[ErrorResponse, Unit]] = {
    val submissionId = uuidGenerator.randomUuid()

    connector.sendSubmission(zReference, taxYear, month, submissionId, FileIO.fromPath(path)).map {
      case Left(upstreamError) => Left(mapToErrorResponse(upstreamError))
      case Right(()) =>
        logger.info(
          s"[SubmissionService][sendSubmission] Monthly return submitted successfully for IM ref: [$zReference], taxYear: [$taxYear], month: [$month], submissionId: [$submissionId]"
        )
        Right(())
    }
  }
}
