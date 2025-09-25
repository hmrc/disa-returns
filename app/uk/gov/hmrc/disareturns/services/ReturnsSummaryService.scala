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

import uk.gov.hmrc.disareturns.models.common.Month.Month
import uk.gov.hmrc.disareturns.models.summary.repository.SaveReturnsSummaryResult
import uk.gov.hmrc.disareturns.repositories.{MonthlyReturnsSummaryRepository, ReturnMetadataRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReturnsSummaryService @Inject() (
  metadataRepo: ReturnMetadataRepository,
  summaryRepo:  MonthlyReturnsSummaryRepository
)(implicit ec:  ExecutionContext) {

  def saveReturnsSummary(zRef: String, year: Int, month: Month, totalRecords: Int): Future[SaveReturnsSummaryResult] =
    metadataRepo
      .existsForZrefAndPeriod(zRef, year, month)
      .flatMap {
        case false => Future.successful(SaveReturnsSummaryResult.NotFound(s"No return found for $zRef for month: $month and year: $year"))
        case true =>
          summaryRepo
            .upsert(zRef, year, month, totalRecords)
            .map(_ => SaveReturnsSummaryResult.Saved)
      }
      .recover { case e => SaveReturnsSummaryResult.Error(e.getMessage) }
}
