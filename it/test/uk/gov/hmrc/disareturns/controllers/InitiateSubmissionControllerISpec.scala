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
import play.api.libs.json.{JsNumber, JsObject, JsString, JsValue, Json}
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.initiate.inboundRequest.InitiateSubmission
import uk.gov.hmrc.disareturns.repositories.InitiateSubmissionRepository
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec

class InitiateSubmissionControllerISpec extends BaseIntegrationSpec {

  implicit val mongo: InitiateSubmissionRepository = app.injector.instanceOf[InitiateSubmissionRepository]
  val isaManagerRef = "Z123456"
  val initiateUrl   = s"/monthly/$isaManagerRef/init"

  val validRequestJson: JsObject = Json.obj(
    "totalRecords"     -> 100,
    "submissionPeriod" -> "APR",
    "taxYear"          -> 2025
  )

  val invalidRequestJson: JsObject = Json.obj(
    "totalRecords"     -> 9000,
    "submissionPeriod" -> "InvalidMonth",
    "taxYear"          -> 2025
  )

  def stubEtmpReportingWindow(status: Int, body: JsObject): Unit =
    stubFor(
      get(urlEqualTo("/disa-returns-stubs/etmp/check-reporting-window"))
        .willReturn(aResponse().withStatus(status).withBody(body.toString))
    )

  def stubEtmpObligation(status: Int, body: JsObject): Unit =
    stubFor(
      get(urlEqualTo(s"/disa-returns-stubs/etmp/check-obligation-status/$isaManagerRef"))
        .willReturn(aResponse().withStatus(status).withBody(body.toString))
    )

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

  def stubPPNSBoxId(): Unit =
    stubFor(
      get(urlEqualTo(s"/box?clientId=$testClientId&boxName=obligations/declaration/isa/return%23%231.0%23%23callbackUrl"))
        .willReturn(ok(boxResponseJson))
    )

  def stubMongoSave(): Unit =
    stubFor(
      post(urlEqualTo(s"/mongo/submissions"))
        .willReturn(ok(boxResponseJson))
    )

  "POST /monthly/:isaManagerRef/init" should {

    "return 200 OK when the submission is valid and all services respond successfully" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false))
      stubPPNSBoxId()

      val result = initiateRequest(validRequestJson)

      result.status shouldBe OK

      val mongoRecord: Option[InitiateSubmission] = await(mongo.findByIsaManagerReference(isaManagerRef))
      val returnId = mongoRecord.map(_.returnId).get

      (result.json \ "returnId").as[String] shouldBe returnId
      (result.json \ "action").as[String]   shouldBe "SUBMIT_RETURN_TO_PAGINATED_API"
      (result.json \ "boxId").as[String]    shouldBe "boxId1"
    }

    "return a 500 when clientId is missing from the request header" in {
      val headersWithoutClientId: Seq[(String, String)] = Seq(
        "Authorization" -> "mock-bearer-token"
      )
      val result: WSResponse = initiateRequest(validRequestJson,headers = headersWithoutClientId)
      result.status                        shouldBe INTERNAL_SERVER_ERROR
      (result.json \ "message").as[String] shouldBe "Missing required header: X-Client-ID"
    }

    "return 403 Forbidden when obligation has already been met" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> true))
      stubPPNSBoxId()

      val result = initiateRequest(validRequestJson)

      result.status                        shouldBe FORBIDDEN
      (result.json \ "code").as[String]    shouldBe "RETURN_OBLIGATION_ALREADY_MET"
      (result.json \ "message").as[String] shouldBe "Return obligation already met"
    }

    "return 403 Forbidden when reporting window is closed" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> false))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false))
      stubPPNSBoxId()

      val result = initiateRequest(validRequestJson)

      result.status                        shouldBe FORBIDDEN
      (result.json \ "code").as[String]    shouldBe "REPORTING_WINDOW_CLOSED"
      (result.json \ "message").as[String] shouldBe "Reporting window has been closed"
    }

    "return 403 Forbidden and multiple error response when reporting window is closed and obligation has already been met" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> false))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> true))
      stubPPNSBoxId()

      val result = initiateRequest(validRequestJson)

      result.status                        shouldBe FORBIDDEN
      (result.json \ "code").as[String]    shouldBe "FORBIDDEN"
      (result.json \ "message").as[String] shouldBe "Multiple issues found regarding your submission"
      val errors = (result.json \ "errors").as[Seq[JsValue]]
      errors.exists(e => (e \ "code").as[String] == "RETURN_OBLIGATION_ALREADY_MET") shouldBe true
      errors.exists(e => (e \ "code").as[String] == "REPORTING_WINDOW_CLOSED")       shouldBe true
    }

    "return 400 Bad Request when request fails validation with invalid month" in {
      val result = initiateRequest(invalidRequestJson)

      result.status                        shouldBe BAD_REQUEST
      (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
      (result.json \ "message").as[String] shouldBe "Bad request"
      val errors = (result.json \ "errors").as[Seq[JsValue]]
      errors.exists(e => (e \ "code").as[String] == "VALIDATION_ERROR")              shouldBe true
      errors.map(e => (e \ "message").as[String].contains("Invalid month provided")) shouldBe List(true)
    }

    "return 400 Bad Request when request fails validation with invalid tax year" in {
      val result = initiateRequest(
        invalidRequestJson +
          ("taxYear"          -> JsNumber(2026)) +
          ("submissionPeriod" -> JsString("JAN"))
      )

      result.status                        shouldBe BAD_REQUEST
      (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
      (result.json \ "message").as[String] shouldBe "Bad request"
      val errors = (result.json \ "errors").as[Seq[JsValue]]
      errors.exists(e => (e \ "code").as[String] == "VALIDATION_ERROR")                                   shouldBe true
      errors.map(e => (e \ "message").as[String].contains("Tax year must be the current tax year: 2025")) shouldBe List(true)
    }
  }

  def initiateRequest(requestBody: JsObject, headers: Seq[(String, String)] = testHeaders): WSResponse = {
    stubAuth()
    mongo.dropCollection()
    await(
      ws.url(
        s"http://localhost:$port/monthly/$isaManagerRef/init"
      ).withFollowRedirects(follow = false)
        .withHttpHeaders(
          headers: _*
        )
        .post(requestBody)
    )
  }
}
