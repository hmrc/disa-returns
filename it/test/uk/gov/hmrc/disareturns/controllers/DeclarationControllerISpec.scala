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

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status.{BAD_REQUEST, FORBIDDEN, INTERNAL_SERVER_ERROR, OK}
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.helpers.ValidationHelper
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec

class DeclarationControllerISpec extends BaseIntegrationSpec {

  val taxYear        = "2025-26"
  val month          = "FEB"
  val boxId          = "boxId1"
  val declarationUrl = s"/monthly/$validZReference/$taxYear/$month/declaration"

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

  "POST /monthly/:zReference/:taxYear/:month/declaration" should {

    "return 200 OK when the declaration is successful and a boxId has been retrieved from PPNS" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), zReference = validZReference)
      stubETMPDeclaration(ok, validZReference)
      stubNPSNotification(ok, validZReference)
      stubPPNSBoxId(boxResponseJson, testClientId)

      val result = declarationRequest(validZReference, taxYear, month)

      result.status                                           shouldBe OK
      (result.json \ "returnResultsSummaryLocation").as[String] should include(s"/monthly/$validZReference/$taxYear/$month/results/summary")
      (result.json \ "boxId").as[String]                      shouldBe boxId
    }
  }

  "return 200 OK when the declaration is successful and no boxId has been retrieved from PPNS" in {
    stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
    stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), zReference = validZReference)
    stubETMPDeclaration(ok, validZReference)
    stubNPSNotification(ok, validZReference)
    stubFor(
      get(urlEqualTo(s"/box?clientId=$testClientId&boxName=obligations%2Fdeclaration%2Fisa%2Freturn%23%231.0%23%23callbackUrl"))
        .willReturn(notFound())
    )

    val result = declarationRequest(validZReference, taxYear, month)

    result.status                                           shouldBe OK
    (result.json \ "returnResultsSummaryLocation").as[String] should include(s"/monthly/$validZReference/$taxYear/$month/results/summary")
  }

  "return 200 OK when an explicit nil return declaration is submitted" in {
    val nilReturnBody =
      """
        |{
        |  "nilReturn": true
        |}
        |""".stripMargin

    stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
    stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), zReference = validZReference)
    stubETMPDeclaration(ok, validZReference)
    stubNPSNotification(ok, validZReference, nilReturn = true)
    stubPPNSBoxId(boxResponseJson, testClientId)

    val result = declarationRequest(validZReference, taxYear, month, body = nilReturnBody)

    result.status                                           shouldBe OK
    (result.json \ "returnResultsSummaryLocation").as[String] should include(s"/monthly/$validZReference/$taxYear/$month/results/summary")
    (result.json \ "boxId").as[String]                      shouldBe boxId
  }

  "return 200 OK when an explicit none nil return declaration is submitted" in {
    val nilReturnBody =
      """
        |{
        |  "nilReturn": false
        |}
        |""".stripMargin

    stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
    stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), zReference = validZReference)
    stubETMPDeclaration(ok, validZReference)
    stubNPSNotification(ok, validZReference)
    stubPPNSBoxId(boxResponseJson, testClientId)

    val result = declarationRequest(validZReference, taxYear, month, body = nilReturnBody)

    result.status                                           shouldBe OK
    (result.json \ "returnResultsSummaryLocation").as[String] should include(s"/monthly/$validZReference/$taxYear/$month/results/summary")
    (result.json \ "boxId").as[String]                      shouldBe boxId
  }

  "return 400 Bad Request for invalid taxYear" in {
    val invalidTaxYear = "2025"
    val result         = declarationRequest(validZReference, invalidTaxYear, month)

    result.status shouldBe BAD_REQUEST
    result.json   shouldBe Json.toJson(ValidationHelper.validateParams(validZReference, invalidTaxYear, month).left.toOption.get)
  }

  "return 400 Bad Request for invalid month" in {
    val invalidMonth = "April"
    val result       = declarationRequest(validZReference, taxYear, invalidMonth)

    result.status shouldBe BAD_REQUEST
    result.json   shouldBe Json.toJson(ValidationHelper.validateParams(validZReference, taxYear, invalidMonth).left.toOption.get)
  }

  "return 400 Bad Request for invalid zReference" in {
    val invalidZReference = "z65803"
    val result            = declarationRequest(invalidZReference, taxYear, month)

    result.status shouldBe BAD_REQUEST
    result.json   shouldBe Json.toJson(ValidationHelper.validateParams(invalidZReference, taxYear, month).left.toOption.get)

  }

  "return 400 Bad Request when the clientId is missing from the header" in {

    val headers = Seq(
      "Authorization" -> "mock-bearer-token"
    )

    stubAuth()
    val result = await(
      ws.url(s"http://localhost:$port/monthly/$validZReference/$taxYear/$month/declaration")
        .withHttpHeaders(headers: _*)
        .post("")
    )

    result.status shouldBe BAD_REQUEST
    val json = result.json
    (json \ "code").as[String]    shouldBe "BAD_REQUEST"
    (json \ "message").as[String] shouldBe "Missing required header: X-Client-ID"
  }

  "return 400 Bad Request when an invalid nil return request body is submitted" in {
    stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
    stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), zReference = validZReference)
    stubETMPDeclaration(ok, validZReference)
    stubNPSNotification(ok, validZReference)
    stubPPNSBoxId(boxResponseJson, testClientId)

    val nilReturnBody =
      """
        |{
        |  "nilReturn": 123
        |}
        |""".stripMargin

    val result = declarationRequest(validZReference, taxYear, month, body = nilReturnBody)

    val json = result.json
    (json \ "code").as[String]    shouldBe "MALFORMED_JSON"
    (json \ "message").as[String] shouldBe "One of the NDJson lines contains malformed JSON"
  }

  "return 403 Forbidden when the reporting window is closed" in {
    stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> false))
    stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), zReference = validZReference)
    val result = declarationRequest(validZReference, taxYear, month)

    result.status shouldBe FORBIDDEN
    val json = result.json
    (json \ "code").as[String]    shouldBe "REPORTING_WINDOW_CLOSED"
    (json \ "message").as[String] shouldBe "Reporting window has been closed"
  }

  "return 403 Forbidden when the obligation is closed" in {
    stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
    stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> true), zReference = validZReference)
    val result = declarationRequest(validZReference, taxYear, month)

    result.status shouldBe FORBIDDEN
    val json = result.json
    (json \ "code").as[String]    shouldBe "OBLIGATION_CLOSED"
    (json \ "message").as[String] shouldBe "Obligation closed"
  }

  "return 500 Internal Server Error when the call to nps declaration fails" in {
    stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
    stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), zReference = validZReference)
    stubETMPDeclaration(serverError, validZReference)
    val result = declarationRequest(validZReference, taxYear, month)

    result.status shouldBe INTERNAL_SERVER_ERROR
    val json = result.json
    (json \ "code").as[String]    shouldBe "INTERNAL_SERVER_ERROR"
    (json \ "message").as[String] shouldBe "There has been an issue processing your request"
  }

  def declarationRequest(
    zReference: String,
    taxYear:    String,
    month:      String,
    headers:    Seq[(String, String)] = testHeaders,
    body:       String = ""
  ): WSResponse = {
    stubAuth()
    await(
      ws.url(
        s"http://localhost:$port/monthly/$zReference/$taxYear/$month/declaration"
      ).withHttpHeaders(headers: _*)
        .post(body)
    )
  }
}
