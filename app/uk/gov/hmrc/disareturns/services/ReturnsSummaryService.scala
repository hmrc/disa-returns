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

import play.api.Logging
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InternalServerErr, ReturnNotFoundErr}
import uk.gov.hmrc.disareturns.models.common.Month.Month
import uk.gov.hmrc.disareturns.models.summary.ReturnSummaryResults
import uk.gov.hmrc.disareturns.models.summary.repository.MonthlyReturnsSummary
import uk.gov.hmrc.disareturns.repositories.MonthlyReturnsSummaryRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReturnsSummaryService @Inject() (
  summaryRepo: MonthlyReturnsSummaryRepository,
  appConfig:   AppConfig
)(implicit ec: ExecutionContext)
    extends Logging {

  def saveReturnsSummary(summary: MonthlyReturnsSummary): Future[Either[ErrorResponse, Unit]] = {
    logger.info(s"Saving return summary for IM ref: [${summary.zRef}]")

    summaryRepo
      .upsert(summary)
      .map(_ => Right(()))
      .recover { case e => Left(InternalServerErr(e.getMessage)) }
  }

  def retrieveReturnSummary(
    isaManagerReferenceNumber: String,
    taxYear:                   String,
    month:                     Month
  ): Future[Either[ErrorResponse, ReturnSummaryResults]] = {
    logger.info(s"Retrieving return summary for IM ref: [$isaManagerReferenceNumber]")

    lazy val returnResultsLocation = appConfig.getReturnResultsLocation(isaManagerReferenceNumber, taxYear, month)
    def returnSummaryResults(totalRecords: Int) =
      ReturnSummaryResults(returnResultsLocation, totalRecords, appConfig.getNoOfPagesForReturnResults(totalRecords))

    summaryRepo
      .retrieveReturnSummary(isaManagerReferenceNumber, taxYear, month)
      .map {
        case Some(summary) => Right(returnSummaryResults(summary.totalRecords))
        case _             => Left(ReturnNotFoundErr(s"No return found for $isaManagerReferenceNumber for ${month.toString} $taxYear"))
      }
      .recover { case e => Left(InternalServerErr(e.getMessage)) }
  }
}
