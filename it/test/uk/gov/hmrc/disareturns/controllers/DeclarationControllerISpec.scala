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

import com.github.tomakehurst.wiremock.client.WireMock.*
import play.api.http.HeaderNames.{AUTHORIZATION, CONTENT_TYPE}
import play.api.http.MimeTypes.JSON
import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.libs.ws.DefaultBodyWritables.writeableOf_String
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec

class DeclarationControllerISpec extends BaseIntegrationSpec {
  private val taxYear = "2026-27"
  private val month   = 9
  private val boxId   = "boxId1"


  val boxResponseJson: String =
    s"""
       |{
       |  "boxId": "boxId1",
       |  "boxName": "Test_Box",
       |  "boxCreator": {
       |    "clientId": "$testClientId"
       |  },
       |  "applicationId": "applicationId"
       |}
       |""".stripMargin

  "POST /monthly/:zReference/declaration" should {
    "generate the reporting period internally and return a periodless summary location" in {
      stubEligible()
      stubSubmissionDeclaration(ok, validZReference, taxYear, month)
      stubPPNSBoxId(boxResponseJson, testClientId)

      val result = declarationRequest()

      result.status shouldBe OK
      (result.json \ "returnResultsSummaryLocation").as[String] should include(s"/monthly/$validZReference/results/summary")
      (result.json \ "boxId").as[String] shouldBe boxId
    }

    "support nil returns using the internally generated period" in {
      stubEligible()
      stubSubmissionDeclaration(ok, validZReference, taxYear, month, nilReturn = true)
      stubPPNSBoxId(boxResponseJson, testClientId)
      declarationRequest(body = """{"nilReturn":true}""").status shouldBe OK
    }

    "retain Z-reference, header and body validation" in {
      declarationRequest(zReference = "invalid").status shouldBe BAD_REQUEST
      declarationRequest(headers = Seq(AUTHORIZATION -> "mock-bearer-token")).status shouldBe BAD_REQUEST
      declarationRequest(body = """{"nilReturn":123}""").status shouldBe BAD_REQUEST
      declarationRequest(body = """{"nilReturn":true,"nilReturn":true}""").status shouldBe BAD_REQUEST
    }

    "map eligibility and submission failures" in {
      stubReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> false))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), zReference = validZReference)
      declarationRequest().status shouldBe FORBIDDEN

      stubEligible()
      stubSubmissionDeclaration(serverError, validZReference, taxYear, month)
      declarationRequest().status shouldBe INTERNAL_SERVER_ERROR
    }

    "map missing submission data" in {
      stubEligible()
      stubSubmissionDeclaration(
        aResponse()
          .withStatus(UNPROCESSABLE_ENTITY)
          .withHeader(CONTENT_TYPE, JSON)
          .withBody("""{"code":"NO_SUBMISSION_DATA","error":"Cannot declare with nilReturn as false when no monthly return data has been submitted"}"""),
        validZReference,
        taxYear,
        month
      )
      declarationRequest().status shouldBe UNPROCESSABLE_ENTITY
    }
  }

  private def stubEligible(): Unit = {
    stubReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
    stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), zReference = validZReference)
  }

  private def declarationRequest(
    zReference: String = validZReference,
    headers: Seq[(String, String)] = testHeaders,
    body: String = """{"nilReturn":false}"""
  ): WSResponse = {
    stubAuth()
    await(ws.url(s"http://localhost:$port/monthly/$zReference/declaration").withHttpHeaders(headers: _*).post(body))
  }
}
