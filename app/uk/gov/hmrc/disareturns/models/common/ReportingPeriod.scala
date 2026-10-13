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

package uk.gov.hmrc.disareturns.models.common

import uk.gov.hmrc.disareturns.models.common.Month.Month

import java.time.YearMonth

case class ReportingPeriod(taxYear: String, month: Month) {

  def yearMonth: YearMonth = {
    val taxYearStart = taxYear.take(4).toInt
    YearMonth.of(if (month.id >= Month.APR.id) taxYearStart else taxYearStart + 1, month.id)
  }
}
