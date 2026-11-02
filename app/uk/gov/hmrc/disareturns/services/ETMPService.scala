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
import uk.gov.hmrc.disareturns.connectors.ETMPConnector
import uk.gov.hmrc.disareturns.utils.UpstreamErrorMapper.mapToErrorResponse
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.submission.MonthlyReturnReportingStatus
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ETMPService @Inject() (connector: ETMPConnector, reportingStatusService: MonthlyReturnReportingStatusService)(implicit ec: ExecutionContext)
    extends Logging {

  def validateSubmissionEligibility(
    zReference:  String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorResponse, MonthlyReturnReportingStatus]] =
    reportingStatusService
      .getReportingStatus(zReference)
      .subflatMap { reportingStatus =>
        val errors: Seq[ErrorResponse] = Seq(
          if (!reportingStatus.reportingWindowOpen) Some(ReportingWindowClosed) else None,
          if (reportingStatus.obligationClosed) Some(ObligationClosed) else None
        ).flatten

        errors match {
          case Nil                => Right(reportingStatus)
          case singleError :: Nil => Left(singleError)
          case multipleErrors     => Left(MultipleErrorResponse(code = "FORBIDDEN", errors = multipleErrors))
        }
      }
      .value

  def declaration(zReference: String)(implicit hc: HeaderCarrier): EitherT[Future, ErrorResponse, HttpResponse] = {
    logger.info(s"[ETMPService][declaration] Submitting declaration for IM ref: [$zReference]")
    connector.sendDeclaration(zReference).leftMap(mapToErrorResponse)
  }

}
