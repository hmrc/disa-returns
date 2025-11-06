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

package uk.gov.hmrc.disareturns.controllers

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlEqualTo}
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.common.Month
import uk.gov.hmrc.disareturns.models.returnResults.{IssueOverSubscribed, IssueWithMessage, ReconciliationReportPage, ReturnResults}
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec

class ReconciliationResultControllerISpec extends BaseIntegrationSpec {

    private val isaManagerRef = "Z1234"
    private val taxYear = "2025-26"
    private val monthEnum = Month.SEP
    private val monthToken = monthEnum.toString
    private val page = 0

    "GET /monthly/:zRef/:year/:month/results/summary" should {

      "return 200 and the first page of the reconciliation report" in {
        val npsReportJson = """
                                      |{
                                      | "totalRecords": 3,
                                      | "returnResults": [
                                      |   {
                                      |     "accountNumber": "123",
                                      |     "nino": "ABC123",
                                      |     "issueIdentified": {
                                      |       "code": "OVER_SUBSCRIBED",
                                      |       "overSubscribedAmount": 1823.76
                                      |     }
                                      |   },
                                      |   {
                                      |     "accountNumber": "123",
                                      |     "nino": "ABC123",
                                      |     "issueIdentified": {
                                      |       "code": "FAILED_ELIGIBILITY",
                                      |       "message": "Failed Eligibility"
                                      |     }
                                      |   }
                                      | ]
                                      |}
        """.stripMargin

        stubAuth()
        stubNPSReportRetrieval(200, npsReportJson, 0, 2)

        val res: WSResponse = retrieveReconciliationReportPageRequest(isaManagerRef, taxYear, monthToken, page)

        res.status mustBe OK
        res.json.as[ReconciliationReportPage] mustBe ReconciliationReportPage(
          page,
          2,
          3,
          2,
          Seq(
            ReturnResults(
              "123",
              "ABC123",
              IssueOverSubscribed(
                "OVER_SUBSCRIBED",
                1823.76
              )
            ),
            ReturnResults(
              "123",
              "ABC123",
              IssueWithMessage(
                "FAILED_ELIGIBILITY",
                "Failed Eligibility"
              )
            )
          )
        )
      }

      "return 400 and an error message when paramater validation fails" in {
        stubAuth()
        stubNPSReportRetrieval(200, "", 0, 0)

        val res: WSResponse = retrieveReconciliationReportPageRequest(isaManagerRef, taxYear, monthToken, -1)

        res.status mustBe BAD_REQUEST
        (res.json \ "message" ).as[String] mustBe "Invalid page index parameter provided"
      }

      "return 401 and an error message when authorization fails" in {
        stubAuthFail()

        val res: WSResponse = retrieveReconciliationReportPageRequest(isaManagerRef, taxYear, monthToken, page)

        res.status mustBe UNAUTHORIZED
        (res.json \ "message" ).as[String] mustBe "Unauthorised"
      }

      "return 404 when a page is not found" in {
        stubAuth()
        stubNPSReportRetrieval(404, Json.obj("code" -> "PAGE_NOT_FOUND", "message" -> "PAGE_NOT_FOUND").toString, 0, 2)

        val res: WSResponse = retrieveReconciliationReportPageRequest(isaManagerRef, taxYear, monthToken, page)

        res.status mustBe NOT_FOUND
        (res.json \ "message" ).as[String] mustBe s"No page $page found"
      }

      "return 404 when a report is not found" in {
        stubAuth()
        stubNPSReportRetrieval(404, """{"message":"REPORT_NOT_FOUND", "responseCode":404}""", 0, 2)

        val res: WSResponse = retrieveReconciliationReportPageRequest(isaManagerRef, taxYear, monthToken, page)

        res.status mustBe NOT_FOUND
        (res.json \ "message" ).as[String] mustBe s"Report not found"
      }

      "return 500 when NPS sends invalid JSON" in {
        stubAuth()
        stubNPSReportRetrieval(200, "not good json", 0, 2)

        val res: WSResponse = retrieveReconciliationReportPageRequest(isaManagerRef, taxYear, monthToken, page)

        res.status mustBe INTERNAL_SERVER_ERROR
        (res.json \ "message" ).as[String] mustBe "There has been an issue processing your request"
      }

      "return 500 when NPS sends an unexpected status" in {
        stubAuth()
        stubNPSReportRetrieval(204, "", 0, 2)

        val res: WSResponse = retrieveReconciliationReportPageRequest(isaManagerRef, taxYear, monthToken, page)

        res.status mustBe INTERNAL_SERVER_ERROR
        (res.json \ "message" ).as[String] mustBe "There has been an issue processing your request"
      }
    }

    def retrieveReconciliationReportPageRequest(
                                       isaManagerReference: String,
                                       taxYear: String,
                                       month: String,
                                       pageIndex: Int,
                                       headers: Seq[(String, String)] = Seq("Authorization" -> "mock-bearer-token")
                                     ): WSResponse = {
      await(
        ws.url(s"http://localhost:$port/monthly/$isaManagerReference/$taxYear/$month/results?page=$pageIndex")
          .withFollowRedirects(follow = false)
          .withHttpHeaders(headers: _*)
          .get()
      )
    }

    def stubNPSReportRetrieval(status: Int, body: String, pageIndex: Int, pageSize: Int): Unit =
      stubFor(
        get(urlEqualTo(s"/monthly/$isaManagerRef/$taxYear/$monthToken/results?pageIndex=$pageIndex&pageSize=$pageSize"))
          .willReturn(aResponse().withStatus(status).withBody(body))
      )
}
