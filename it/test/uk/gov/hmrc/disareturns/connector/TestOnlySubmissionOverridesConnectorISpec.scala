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

package uk.gov.hmrc.disareturns.connector

import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.testOnly.connectors.TestOnlySubmissionOverridesConnector
import uk.gov.hmrc.disareturns.testOnly.connectors.TestOnlySubmissionOverridesConnector.{ClockOverride, ReportingWindowOverride, TestOverride}
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec
import uk.gov.hmrc.disareturns.utils.WiremockHelper.{stubGet, verifyGet}
import uk.gov.hmrc.http.UpstreamErrorResponse

import java.time.{Instant, LocalDate}

class TestOnlySubmissionOverridesConnectorISpec extends BaseIntegrationSpec {

  private val endpoint = s"/disa-returns-submission/test-only/overrides/$validZReference"
  private val connector = app.injector.instanceOf[TestOnlySubmissionOverridesConnector]

  "TestOnlySubmissionOverridesConnector.get" should {
    "strongly decode clock and reporting-window overrides from a successful response" in {
      stubGet(
        endpoint,
        OK,
        s"""{"zReference":"$validZReference","clock":{"date":"2026-05-10"},"reportingWindow":{"startDate":"2026-05-01T00:00:00Z","endDate":"2026-05-31T23:59:59Z"}}"""
      )

      await(connector.get(validZReference)) shouldBe
        TestOverride(
          validZReference,
          Some(ClockOverride(LocalDate.parse("2026-05-10"))),
          Some(ReportingWindowOverride(Instant.parse("2026-05-01T00:00:00Z"), Instant.parse("2026-05-31T23:59:59Z")))
        )
      verifyGet(endpoint)
    }

    "decode null overrides as absent" in {
      stubGet(endpoint, OK, s"""{"zReference":"$validZReference","clock":null,"reportingWindow":null}""")

      await(connector.get(validZReference)) shouldBe TestOverride(validZReference, None, None)
      verifyGet(endpoint)
    }

    "decode omitted optional overrides as absent" in {
      stubGet(endpoint, OK, s"""{"zReference":"$validZReference"}""")

      await(connector.get(validZReference)) shouldBe TestOverride(validZReference, None, None)
      verifyGet(endpoint)
    }

    "fail when the aggregate responds unsuccessfully" in {
      stubGet(endpoint, INTERNAL_SERVER_ERROR, "")

      val failure = intercept[UpstreamErrorResponse](await(connector.get(validZReference)))
      failure.statusCode shouldBe INTERNAL_SERVER_ERROR
      verifyGet(endpoint)
    }

    "fail when a successful response has an invalid override" in {
      stubGet(endpoint, OK, s"""{"zReference":"$validZReference","clock":{"date":"invalid"},"reportingWindow":null}""")

      intercept[Exception](await(connector.get(validZReference)))
      verifyGet(endpoint)
    }
  }
}
