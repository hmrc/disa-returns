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

package services

import uk.gov.hmrc.disareturns.models.common.{Month, ReportingPeriod}
import uk.gov.hmrc.disareturns.services.ReportingPeriodService
import utils.BaseUnitSpec

import java.time.{Clock, Instant, ZoneOffset}

class ReportingPeriodServiceSpec extends BaseUnitSpec {

  "previousMonthPeriod" should {
    Seq(
      ("2026-01-15T00:00:00Z", ReportingPeriod("2025-26", Month.DEC), "January to the previous December"),
      ("2026-04-05T00:00:00Z", ReportingPeriod("2025-26", Month.MAR), "5 April to March in the previous tax year"),
      ("2026-04-06T00:00:00Z", ReportingPeriod("2025-26", Month.MAR), "6 April to March in the previous tax year"),
      ("2026-05-05T00:00:00Z", ReportingPeriod("2026-27", Month.APR), "5 May to April in the new tax year"),
      ("2026-05-06T00:00:00Z", ReportingPeriod("2026-27", Month.APR), "6 May to April in the new tax year")
    ).foreach { case (instant, expected, description) =>
      s"map $description" in {
        val service = new ReportingPeriodService(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC))
        service.previousMonthPeriod shouldBe expected
      }
    }
  }
}
