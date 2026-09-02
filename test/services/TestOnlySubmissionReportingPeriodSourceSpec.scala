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

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{never, verify, when}
import uk.gov.hmrc.disareturns.models.common.{Month, ReportingPeriod}
import uk.gov.hmrc.disareturns.services.{ReportingPeriodService, SystemClock, SystemReportingPeriodSource}
import uk.gov.hmrc.disareturns.testOnly.connectors.TestOnlySubmissionOverridesConnector
import uk.gov.hmrc.disareturns.testOnly.connectors.TestOnlySubmissionOverridesConnector.{ClockOverride, TestOverride}
import uk.gov.hmrc.disareturns.testOnly.services.TestOnlySubmissionReportingPeriodSource
import uk.gov.hmrc.http.HeaderCarrier
import utils.BaseUnitSpec

import java.time.LocalDate
import scala.concurrent.Future

class TestOnlySubmissionReportingPeriodSourceSpec extends BaseUnitSpec {

  "SystemReportingPeriodSource" should {
    "derive the period from the local system clock" in {
      val clock = mock[SystemClock]
      when(clock.currentDate).thenReturn(LocalDate.parse("2026-05-10"))
      val source = new SystemReportingPeriodSource(clock, new ReportingPeriodService())

      source.get(validZReference).futureValue shouldBe ReportingPeriod("2026-27", Month.APR)
      verify(clock).currentDate
    }
  }

  "TestOnlySubmissionReportingPeriodSource" should {
    "derive the period from a clock override and propagate request context" in {
      val connector = mock[TestOnlySubmissionOverridesConnector]
      val clock     = mock[SystemClock]
      val requestHc = HeaderCarrier(otherHeaders = Seq("test-header" -> "value"))
      when(connector.get(eqTo(validZReference))(eqTo(requestHc)))
        .thenReturn(Future.successful(TestOverride(validZReference, Some(ClockOverride(LocalDate.parse("2026-05-10"))), None)))
      val source = new TestOnlySubmissionReportingPeriodSource(connector, new ReportingPeriodService(), clock)

      source.get(validZReference)(requestHc).futureValue shouldBe ReportingPeriod("2026-27", Month.APR)
      verify(connector).get(eqTo(validZReference))(eqTo(requestHc))
      verify(clock, never).currentDate
    }

    "fall back to the local system clock when there is no clock override" in {
      val connector = mock[TestOnlySubmissionOverridesConnector]
      val clock     = mock[SystemClock]
      when(connector.get(any)(any)).thenReturn(Future.successful(TestOverride(validZReference, None, None)))
      when(clock.currentDate).thenReturn(LocalDate.parse("2026-05-10"))
      val source = new TestOnlySubmissionReportingPeriodSource(connector, new ReportingPeriodService(), clock)

      source.get(validZReference).futureValue shouldBe ReportingPeriod("2026-27", Month.APR)
      verify(clock).currentDate
    }

    "propagate aggregate failures without using the local clock" in {
      val connector = mock[TestOnlySubmissionOverridesConnector]
      val clock     = mock[SystemClock]
      val failure   = new RuntimeException("aggregate unavailable")
      when(connector.get(any)(any)).thenReturn(Future.failed(failure))
      val source = new TestOnlySubmissionReportingPeriodSource(connector, new ReportingPeriodService(), clock)

      source.get(validZReference).failed.futureValue shouldBe failure
      verify(clock, never).currentDate
    }
  }
}
