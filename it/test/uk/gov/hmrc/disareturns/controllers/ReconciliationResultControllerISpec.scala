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
import org.scalatest.matchers.must.Matchers.mustBe
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.common.Month
import uk.gov.hmrc.disareturns.models.returnResults.{IssueOverSubscribed, IssueWithMessage, ReconciliationReport, ReturnResults}
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec

class ReconciliationResultControllerISpec extends BaseIntegrationSpec {

  private val taxYear    = "2026-27"
  private val monthToken = Month.SEP.toString
  private val results = Seq(
    ReturnResults("123", "ABC123", IssueOverSubscribed("OVER_SUBSCRIBED", 1823.76)),
    ReturnResults("123", "ABC123", IssueWithMessage("FAILED_ELIGIBILITY", "Failed Eligibility"))
  )

  "GET /monthly/:zReference/results" should {
    "return results and an opaque URL-safe cursor, then send its raw value downstream" in {
      val firstNpsResponse = Json.obj("returnResults" -> results, "nextCursor" -> "raw-nps-cursor").toString
      val finalNpsResponse = Json.obj("returnResults" -> Json.arr()).toString
      stubAuth()
      stubNPSReportRetrieval(OK, firstNpsResponse, None, 2)

      val first = retrieveReconciliationReportRequest(validZReference, None, Some(2))

      first.status mustBe OK
      val publicCursor = (first.json \ "nextCursor").as[String]
      publicCursor.matches("[A-Za-z0-9_-]+") mustBe true
      (publicCursor == "raw-nps-cursor") mustBe false
      first.json.as[ReconciliationReport] mustBe ReconciliationReport(results, Some(publicCursor))

      stubNPSReportRetrieval(OK, finalNpsResponse, Some("raw-nps-cursor"), 2)
      val finalResponse = retrieveReconciliationReportRequest(validZReference, Some(publicCursor), Some(2))

      finalResponse.status mustBe OK
      finalResponse.json mustBe Json.obj("returnResults" -> Json.arr())
    }

    "use the default limit when limit is omitted" in {
      stubAuth()
      stubNPSReportRetrieval(OK, Json.obj("returnResults" -> Json.arr()).toString, None, 200)

      retrieveReconciliationReportRequest(validZReference).status mustBe OK
    }

    "return 400 when limit is zero or above the maximum" in {
      stubAuth()
      Seq(0, 1001).foreach { limit =>
        val response = retrieveReconciliationReportRequest(validZReference, limit = Some(limit))
        response.status mustBe BAD_REQUEST
        (response.json \ "message").as[String] mustBe "Invalid limit parameter provided"
      }
    }

    "return 400 for a tampered public cursor" in {
      stubAuth()
      val response = retrieveReconciliationReportRequest(validZReference, cursor = Some("not-a-valid-cursor"))

      response.status mustBe BAD_REQUEST
      (response.json \ "message").as[String] mustBe "Invalid cursor parameter provided"
    }

    "return 401 when authorization fails" in {
      stubAuthFail()
      retrieveReconciliationReportRequest(validZReference).status mustBe UNAUTHORIZED
    }

    "return 404 when the report is not found" in {
      stubAuth()
      stubNPSReportRetrieval(NOT_FOUND, """{"message":"REPORT_NOT_FOUND", "responseCode":404}""", None, 200)

      val response = retrieveReconciliationReportRequest(validZReference)
      response.status mustBe NOT_FOUND
      (response.json \ "message").as[String] mustBe "Report not found"
    }

    "return 500 when NPS sends invalid JSON or an unexpected status" in {
      stubAuth()
      stubNPSReportRetrieval(OK, "not good json", None, 200)
      retrieveReconciliationReportRequest(validZReference).status mustBe INTERNAL_SERVER_ERROR

      stubNPSReportRetrieval(NO_CONTENT, "", None, 10)
      retrieveReconciliationReportRequest(validZReference, limit = Some(10)).status mustBe INTERNAL_SERVER_ERROR
    }
  }

  private def retrieveReconciliationReportRequest(
    zReference: String,
    cursor:     Option[String] = None,
    limit:      Option[Int] = None,
    headers:    Seq[(String, String)] = Seq(AUTHORIZATION -> "mock-bearer-token")
  ): WSResponse = {
    val query = Seq(cursor.map("cursor" -> _), limit.map(value => "limit" -> value.toString)).flatten
    await(
      ws.url(s"http://localhost:$port/monthly/$zReference/results")
        .addQueryStringParameters(query*)
        .withFollowRedirects(follow = false)
        .withHttpHeaders(headers*)
        .get()
    )
  }

  private def stubNPSReportRetrieval(status: Int, body: String, cursor: Option[String], limit: Int): Unit = {
    val query = s"limit=$limit" + cursor.fold("")(value => s"&cursor=$value")
    stubFor(
      get(urlEqualTo(s"/monthly/$validZReference/$taxYear/$monthToken/results?$query"))
        .willReturn(aResponse().withStatus(status).withBody(body))
    )
  }
}
