/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.http.Status.{FORBIDDEN, OK, UNAUTHORIZED}
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.connectors.ETMPConnector
import uk.gov.hmrc.disareturns.connectors.response.{EtmpObligations, EtmpReportingWindow}
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec
import uk.gov.hmrc.disareturns.utils.WiremockHelper._

class ETMPConnectorISpec extends BaseIntegrationSpec {

  val testIsaManagerReferenceNumber = "123456"
  val obligationsUrl = s"/disa-returns-stubs/etmp/check-obligation-status/$testIsaManagerReferenceNumber"
  val reportingWindowUrl = "/disa-returns-stubs/etmp/check-reporting-window"

  val connector: ETMPConnector = app.injector.instanceOf[ETMPConnector]

  "ETMPConnector.checkReturnsObligationStatus" should {

    "return Right(EtmpObligations) when ETMP returns a successful response" in {
      val responseBody = """{ "obligationAlreadyMet": true }"""

      stubGet(obligationsUrl, OK, responseBody)

      val result = await(connector.checkReturnsObligationStatus(testIsaManagerReferenceNumber))

      result shouldBe Right(EtmpObligations(obligationAlreadyMet = true))
    }

    "return Left(UpstreamErrorResponse) when ETMP returns an error status" in {
      stubGet(obligationsUrl, UNAUTHORIZED, """{"error": "Not authorised"}""")

      val result = await(connector.checkReturnsObligationStatus(testIsaManagerReferenceNumber))

      result.left.toOption.get.statusCode shouldBe UNAUTHORIZED
    }

    "return Left(UpstreamErrorResponse) when ETMP fails with unexpected exception" in {
      // Simulate no stub = 404
      val result = await(connector.checkReturnsObligationStatus("non-existent"))

      result match {
        case Left(error) =>
          error.statusCode shouldBe 500
          error.message should include("Unexpected error")
        case Right(_) => fail("Expected Left")
      }
    }
  }

  "ETMPConnector.checkReportingWindowStatus" should {

    "return Right(EtmpReportingWindow) when ETMP returns a successful response" in {
      val responseBody = """{ "reportingWindowOpen": true }"""

      stubGet(reportingWindowUrl, OK, responseBody)

      val result = await(connector.checkReportingWindowStatus)

      result shouldBe Right(EtmpReportingWindow(reportingWindowOpen = true))
    }

    "return Left(UpstreamErrorResponse) when ETMP returns an error status" in {
      stubGet(reportingWindowUrl, FORBIDDEN, """{"error": "Forbidden"}""")

      val result = await(connector.checkReportingWindowStatus)

      result.left.toOption.get.statusCode shouldBe FORBIDDEN
    }

    "return Left(UpstreamErrorResponse) when ETMP call fails with unexpected exception" in {
      val result = await(connector.checkReportingWindowStatus)

      result match {
        case Left(error) =>
          error.statusCode shouldBe 500
          error.message should include("Unexpected error")
        case Right(_) => fail("Expected a Left")
      }
    }
  }
}
