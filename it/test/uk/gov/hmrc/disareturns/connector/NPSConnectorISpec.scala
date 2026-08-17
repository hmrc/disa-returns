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

import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND, NO_CONTENT, OK, UNAUTHORIZED}
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.connectors.NPSConnector
import uk.gov.hmrc.disareturns.models.common.Month
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec
import uk.gov.hmrc.disareturns.utils.WiremockHelper._

class NPSConnectorISpec extends BaseIntegrationSpec {

  private val sendNotificationUrl = s"/nps/declaration/$validZReference"
  private val taxYear             = "2026-27"
  private val month               = Month.JAN
  private val connector: NPSConnector = app.injector.instanceOf[NPSConnector]

  "NPSConnector.sendNotification" should {

    "return Right(HttpResponse) when NPS returns 204 NO_CONTENT" in {
      stubPost(sendNotificationUrl, NO_CONTENT, "")

      val response =
        await(connector.sendNotification(validZReference, nilReturnReported = true).value).value

      response.status shouldBe NO_CONTENT
      response.body   shouldBe ""
    }

    "return Left(UpstreamErrorResponse) when NPS returns an error status (401)" in {
      stubPost(sendNotificationUrl, UNAUTHORIZED, """{"error":"Not authorised"}""")

      val err =
        await(connector.sendNotification(validZReference, nilReturnReported = false).value).left.value

      err.statusCode shouldBe UNAUTHORIZED
      err.message      should include("Not authorised")
    }
  }

  "NPSConnector.retrieveReconciliationReportPage" should {

    val pageIndex          = 0
    val pageSize           = 2
    val reportRetrievalUrl = s"/monthly/$validZReference/$taxYear/${month.toString}/results?pageIndex=$pageIndex&pageSize=$pageSize"
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

      val response =
        await(connector.retrieveReconciliationReportPage(validZReference, taxYear, month, pageIndex, pageSize).value).value

      response.status shouldBe OK
      response.body   shouldBe reconciliationReport
    }

    "return Left(UpstreamErrorResponse) when NPS returns an error status (401)" in {
      stubGet(reportRetrievalUrl, UNAUTHORIZED, """{"error":"Not authorised"}""")

      val err =
        await(connector.retrieveReconciliationReportPage(validZReference, taxYear, month, pageIndex, pageSize).value).left.value

      err.statusCode shouldBe UNAUTHORIZED
      err.message      should include("Not authorised")
      verifyGet(reportRetrievalUrl, count = 1)
    }

    "retry persistent server errors four times" in {
      stubGet(reportRetrievalUrl, INTERNAL_SERVER_ERROR, "failed")
      await(
        connector.retrieveReconciliationReportPage(validZReference, taxYear, month, pageIndex, pageSize).value
      ).left.value.statusCode shouldBe INTERNAL_SERVER_ERROR
      verifyGet(reportRetrievalUrl, count = 4)
    }

    "return Left(UpstreamErrorResponse) when the call fails with an unexpected exception" in {
      val err =
        await(connector.retrieveReconciliationReportPage("non-existent", "nope", month, pageIndex, pageSize).value).left.value

      err.statusCode shouldBe NOT_FOUND
      err.message      should include("No response could be served as there are no stub mappings in this WireMock instance.")
    }
  }
}
