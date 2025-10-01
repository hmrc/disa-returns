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

import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InternalServerErr, ReturnNotFoundErr}
import uk.gov.hmrc.disareturns.models.common.Month.Month
import uk.gov.hmrc.disareturns.models.summary.{ReturnSummaryResults, TaxYear}
import uk.gov.hmrc.disareturns.models.summary.repository.{MonthlyReturnsSummary, SaveReturnsSummaryResult}
import uk.gov.hmrc.disareturns.repositories.MonthlyReturnsSummaryRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReturnsSummaryService @Inject() (
  summaryRepo: MonthlyReturnsSummaryRepository,
  appConfig:   AppConfig
)(implicit ec: ExecutionContext) {

  def saveReturnsSummary(summary: MonthlyReturnsSummary): Future[SaveReturnsSummaryResult] =
    summaryRepo
      .upsert(summary)
      .map(_ => SaveReturnsSummaryResult.Saved)
      .recover { case e => SaveReturnsSummaryResult.Error(e.getMessage) }

  def retrieveReturnSummary(
    isaManagerReferenceNumber: String,
    taxYear:                   TaxYear,
    month:                     Month
  ): Future[Either[ErrorResponse, ReturnSummaryResults]] = {
    lazy val returnResultsLocation              = appConfig.getReturnResultsLocation(isaManagerReferenceNumber, taxYear, month)
    def returnSummaryResults(totalRecords: Int) = ReturnSummaryResults(returnResultsLocation, totalRecords, appConfig.returnResultsNumberOfPages)

    summaryRepo
      .retrieveSummary(isaManagerReferenceNumber, taxYear, month)
      .map {
        case Some(summary) => Right(returnSummaryResults(summary.totalRecords))
        case _             => Left(ReturnNotFoundErr(s"No return found for $isaManagerReferenceNumber for ${month.toString} ${taxYear.value}"))
      }
      .recover { case e => Left(InternalServerErr(e.getMessage)) }
  }
}
