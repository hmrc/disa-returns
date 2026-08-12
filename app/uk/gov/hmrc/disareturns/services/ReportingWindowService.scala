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

package uk.gov.hmrc.disareturns.services

import cats.data.EitherT
import play.api.Logging
import uk.gov.hmrc.disareturns.connectors.SubmissionConnector
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InternalServerErr}
import uk.gov.hmrc.disareturns.models.submission.ReportingWindowStatus
import uk.gov.hmrc.disareturns.utils.UpstreamErrorMapper.mapToErrorResponse
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportingWindowService @Inject() (connector: SubmissionConnector)(implicit ec: ExecutionContext) extends Logging {

  def getReportingWindowStatus()(implicit hc: HeaderCarrier): EitherT[Future, ErrorResponse, ReportingWindowStatus] = {
    logger.info("Getting reporting window status")
    EitherT {
      connector.getReportingWindowStatus.value.map {
        case Left(upstreamError) => Left(mapToErrorResponse(upstreamError))
        case Right(response) =>
          response.json
            .validate[ReportingWindowStatus]
            .fold(
              _ => Left(InternalServerErr()),
              reportingWindowStatus => Right(reportingWindowStatus)
            )
      }
    }
  }
}
