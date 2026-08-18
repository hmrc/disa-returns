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

import uk.gov.hmrc.disareturns.models.common.{Month, ReportingPeriod}

import java.time.{Clock, LocalDate, YearMonth}
import javax.inject.{Inject, Singleton}

@Singleton
class ReportingPeriodService @Inject() (clock: Clock) {

  def previousMonthPeriod: ReportingPeriod = {
    val previousYearMonth: YearMonth   = YearMonth.from(LocalDate.now(clock)).minusMonths(1)
    val month:             Month.Value = Month(previousYearMonth.getMonthValue)
    val isAprilOrLater:    Boolean     = month.id >= Month.APR.id
    val taxYearStart:      Int         = if (isAprilOrLater) previousYearMonth.getYear else previousYearMonth.getYear - 1
    val taxYearEndShort:   Int         = (taxYearStart + 1) % 100
    ReportingPeriod(f"$taxYearStart%04d-$taxYearEndShort%02d", month)
  }
}
