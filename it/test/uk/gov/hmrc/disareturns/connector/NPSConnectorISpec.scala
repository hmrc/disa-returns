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

package uk.gov.hmrc.disareturns.connector

import play.api.http.Status.{NOT_FOUND, NO_CONTENT, OK, UNAUTHORIZED}
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.connectors.NPSConnector
import uk.gov.hmrc.disareturns.models.common.Month
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec
import uk.gov.hmrc.disareturns.utils.WiremockHelper._

class NPSConnectorISpec extends BaseIntegrationSpec {

  private val testIsaManagerReferenceNumber = "Z1234"
  private val sendNotificationUrl           = s"/nps/declaration/$testIsaManagerReferenceNumber"
  private val isaManagerReferenceNumber = "Z1234"
  private val taxYear = "2026-27"
  private val month = Month.JAN
  private val connector: NPSConnector = app.injector.instanceOf[NPSConnector]

  "NPSConnector.submit" should {

    val submitUrl                     = s"/nps/submit/$isaManagerReferenceNumber"

    "return Right(HttpResponse) when NPS returns 204 NO_CONTENT" in {
      stubPost(submitUrl, NO_CONTENT, "")

      val Right(response) =
        await(connector.submit(isaManagerReferenceNumber, Nil).value)

      response.status shouldBe NO_CONTENT
      response.body   shouldBe ""
    }

    "return Left(UpstreamErrorResponse) when NPS returns an error status (401)" in {
      stubPost(submitUrl, UNAUTHORIZED, """{"error":"Not authorised"}""")

      val Left(err) =
        await(connector.submit(isaManagerReferenceNumber, Nil).value)

      err.statusCode shouldBe UNAUTHORIZED
      err.message      should include("Not authorised")
    }

    "return Left(UpstreamErrorResponse) when the call fails with an unexpected exception" in {
      val Left(err) =
        await(connector.submit("non-existent", Nil).value)

      err.statusCode shouldBe NOT_FOUND
      err.message      should include("No response could be served as there are no stub mappings in this WireMock instance.")
    }
  }
  "NPSConnector.sendNotification" should {

    "return Right(HttpResponse) when NPS returns 204 NO_CONTENT" in {
      stubPost(sendNotificationUrl, NO_CONTENT, "")

      val Right(response) =
        await(connector.sendNotification(testIsaManagerReferenceNumber, nilReturnReported = true).value)

      response.status shouldBe NO_CONTENT
      response.body   shouldBe ""
    }

    "return Left(UpstreamErrorResponse) when NPS returns an error status (401)" in {
      stubPost(sendNotificationUrl, UNAUTHORIZED, """{"error":"Not authorised"}""")

      val Left(err) =
        await(connector.sendNotification(testIsaManagerReferenceNumber, nilReturnReported = false).value)

      "NPSConnector.retrieveReconciliationReportPage" should {

        val pageIndex = 0
        val pageSize = 2
        val reportRetrievalUrl = s"/monthly/$isaManagerReferenceNumber/$taxYear/${month.toString}/results?pageIndex=$pageIndex&pageSize=$pageSize"
        val reconciliationReport =
          """
            |{
            | "totalRecords": 12,
            | "returnResults": {
            |   "accountNumber": 123,
            |   "nino": ABC123,
            |   "issueIdentified": {
            |     "code": "OVER_SUBSCRIBED",
            |     "amount": 1823.76
            |   }
            | }
            |}
        """.stripMargin

        "return Right(HttpResponse) when NPS returns 200 OK" in {
          stubGet(reportRetrievalUrl, OK, reconciliationReport)

          val Right(response) =
            await(connector.retrieveReconciliationReportPage(isaManagerReferenceNumber, taxYear, month, pageIndex, pageSize).value)

          response.status shouldBe OK
          response.body shouldBe reconciliationReport
        }

        "return Left(UpstreamErrorResponse) when NPS returns an error status (401)" in {
          stubGet(reportRetrievalUrl, UNAUTHORIZED, """{"error":"Not authorised"}""")

          val Left(err) =
            await(connector.retrieveReconciliationReportPage(isaManagerReferenceNumber, taxYear, month, pageIndex, pageSize).value)

          err.statusCode shouldBe UNAUTHORIZED
          err.message should include("Not authorised")
        }

        "return Left(UpstreamErrorResponse) when the call fails with an unexpected exception" in {
          val Left(err) =
            await(connector.sendNotification("non-existent", nilReturnReported = true).value)
          await(connector.retrieveReconciliationReportPage("non-existent", "nope", month, pageIndex, pageSize).value)

          err.statusCode shouldBe NOT_FOUND
          err.message should include("No response could be served as there are no stub mappings in this WireMock instance.")
        }

      }
}
