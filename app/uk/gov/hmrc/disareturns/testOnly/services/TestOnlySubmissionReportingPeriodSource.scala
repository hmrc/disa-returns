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

package uk.gov.hmrc.disareturns.testOnly.services

import uk.gov.hmrc.disareturns.models.common.ReportingPeriod
import uk.gov.hmrc.disareturns.services.{ReportingPeriodService, ReportingPeriodSource, SystemClock}
import uk.gov.hmrc.disareturns.testOnly.connectors.TestOnlySubmissionOverridesConnector
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestOnlySubmissionReportingPeriodSource @Inject() (
  connector:              TestOnlySubmissionOverridesConnector,
  reportingPeriodService: ReportingPeriodService,
  clock:                  SystemClock
)(implicit ec: ExecutionContext)
    extends ReportingPeriodSource {

  override def get(zReference: String)(implicit hc: HeaderCarrier): Future[ReportingPeriod] =
    connector.get(zReference).map { overrides =>
      val effectiveDate = overrides.clock.fold(clock.currentDate)(_.date)
      reportingPeriodService.previousMonthPeriod(effectiveDate)
    }
}
