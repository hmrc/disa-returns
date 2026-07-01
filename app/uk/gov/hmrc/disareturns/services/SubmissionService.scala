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
import uk.gov.hmrc.disareturns.models.common.ErrorResponse
import uk.gov.hmrc.disareturns.models.common.Month.Month
import uk.gov.hmrc.disareturns.models.common.UpstreamErrorMapper.mapToErrorResponse
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import java.nio.file.Path
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionService @Inject() (connector: SubmissionConnector)(implicit ec: ExecutionContext) extends Logging {

  def declare(zReference: String, taxYear: String, month: Month, nilReturnReported: Boolean)(implicit
    hc:                   HeaderCarrier
  ): EitherT[Future, ErrorResponse, HttpResponse] = {
    logger.info(
      s"Sending declaration to disa-returns-submission for IM ref: [$zReference], taxYear: [$taxYear], month: [$month], nilReturn: [$nilReturnReported]"
    )
    connector.sendDeclaration(zReference, taxYear, month, nilReturnReported).leftMap(mapToErrorResponse)
  }

  def submitMonthlyReturn(zReference: String, taxYear: String, month: Month, path: Path)(implicit
    hc:                               HeaderCarrier
  ): Future[Either[ErrorResponse, Unit]] = {
    logger.info(s"Sending monthly return to disa-returns-submission for IM ref: [$zReference], taxYear: [$taxYear], month: [$month]")
    connector.createMonthlyReturn(zReference, taxYear, month, nilReturn = false).flatMap {
      case Left(UpstreamErrorResponse(_, CONFLICT, _, _)) =>
        logger.info(s"Monthly return already exists for IM ref: [$zReference], taxYear: [$taxYear], month: [$month] - proceeding to store submission data")
        storeSubmission(zReference, taxYear, month, path)
      case Left(upstreamError) => Future.successful(Left(mapToErrorResponse(upstreamError)))
      case Right(())           => storeSubmission(zReference, taxYear, month, path)
    }
  }

  private def storeSubmission(zReference: String, taxYear: String, month: Month, path: Path)(implicit
    hc: HeaderCarrier
  ): Future[Either[ErrorResponse, Unit]] =
    connector.sendMonthlyReturn(zReference, taxYear, month, FileIO.fromPath(path)).map {
      case Left(upstreamError) => Left(mapToErrorResponse(upstreamError))
      case Right(()) =>
        logger.info(s"Monthly return submitted successfully for IM ref: [$zReference], taxYear: [$taxYear], month: [$month]")
        Right(())
    }
}
