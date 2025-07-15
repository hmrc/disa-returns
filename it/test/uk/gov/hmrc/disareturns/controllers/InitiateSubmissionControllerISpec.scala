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
import play.api.libs.json._
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

  def stubPPNSInternalServerError(): Unit =
    stubFor(
      get(urlEqualTo(s"/box?clientId=$testClientId&boxName=obligations/declaration/isa/return%23%231.0%23%23callbackUrl"))
        .willReturn(serverError)
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
    "return 200 OK when the submission is valid for a nil return" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false))
      stubPPNSBoxId()

      val result = initiateRequest(validRequestJson +
        ("totalRecords"          -> JsNumber(0)))

      result.status shouldBe OK

      val mongoRecord: Option[InitiateSubmission] = await(mongo.findByIsaManagerReference(isaManagerRef))
      val returnId = mongoRecord.map(_.returnId).get

      (result.json \ "returnId").as[String] shouldBe returnId
      (result.json \ "action").as[String]   shouldBe "NIL_RETURN_ACCEPTED_NO_FURTHER_ACTION"
      (result.json \ "boxId").as[String]    shouldBe "boxId1"
    }

    "return a 500 when clientId is missing from the request header" in {
      val headersWithoutClientId: Seq[(String, String)] = Seq(
        "Authorization" -> "mock-bearer-token"
      )
      val result: WSResponse = initiateRequest(validRequestJson, headers = headersWithoutClientId)
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
      errors.exists(e => (e \ "code").as[String].contains("RETURN_OBLIGATION_ALREADY_MET")) shouldBe true
      errors.exists(e => (e \ "code").as[String].contains("REPORTING_WINDOW_CLOSED"))       shouldBe true
    }

    "return 400 Bad Request when request fails validation with invalid month" in {
      val result = initiateRequest(invalidRequestJson)

      result.status                        shouldBe BAD_REQUEST
      (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
      (result.json \ "message").as[String] shouldBe "Bad request"
      val errors = (result.json \ "errors").as[Seq[JsValue]]
      errors.map(e => (e \ "code").as[String] shouldBe "VALIDATION_ERROR")
      errors.map(e => (e \ "path").as[String] shouldBe "/submissionPeriod")
      errors.map(e => (e \ "message").as[String] shouldBe "Invalid month provided")
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
      errors.map(e => (e \ "code").as[String] shouldBe "VALIDATION_ERROR")
      errors.map(e => (e \ "path").as[String] shouldBe "/taxYear")
      errors.map(e => (e \ "message").as[String] shouldBe "Tax year must be the current tax year")
    }
  }
  "return 400 Bad Request when request fails validation with invalid totalRecords" in {
    val invalidRequestJson: JsObject = Json.obj(
      "totalRecords"     -> -1,
      "submissionPeriod" -> "JAN",
      "taxYear"          -> 2025
    )
    val result = initiateRequest(invalidRequestJson)

    result.status                        shouldBe BAD_REQUEST
    (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
    (result.json \ "message").as[String] shouldBe "Bad request"
    val errors = (result.json \ "errors").as[Seq[JsValue]]
    errors.map(e => (e \ "code").as[String] shouldBe "VALIDATION_ERROR")
    errors.map(e => (e \ "path").as[String] shouldBe "/totalRecords")
    errors.map(e => (e \ "message").as[String] shouldBe "This field must be greater than or equal to 0")
  }
  "return 400 Bad Request when request fails validation with invalid totalRecords and taxYear" in {
    val invalidRequestJson: JsObject = Json.obj(
      "totalRecords"     -> -1,
      "submissionPeriod" -> "JAN",
      "taxYear"          -> 2024
    )
    val result = initiateRequest(invalidRequestJson)

    result.status                        shouldBe BAD_REQUEST
    (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
    (result.json \ "message").as[String] shouldBe "Bad request"
    val errors = (result.json \ "errors").as[Seq[JsValue]]
    errors.map(e => (e \ "code").as[String] shouldBe "VALIDATION_ERROR")
    errors.exists(e => (e \ "path").as[String].contains("/taxYear"))                                         shouldBe true
    errors.exists(e => (e \ "path").as[String].contains("/totalRecords"))                                    shouldBe true
    errors.exists(e => (e \ "message").as[String].contains("Tax year cannot be in the past"))                shouldBe true
    errors.exists(e => (e \ "message").as[String].contains("This field must be greater than or equal to 0")) shouldBe true

  }
  "return 400 Bad Request when request fails validation with invalid totalRecords and taxYear and submissionPeriod" in {
    val invalidRequestJson: JsObject = Json.obj(
      "totalRecords"     -> -1,
      "submissionPeriod" -> "January",
      "taxYear"          -> 2024
    )
    val result = initiateRequest(invalidRequestJson)

    result.status                        shouldBe BAD_REQUEST
    (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
    (result.json \ "message").as[String] shouldBe "Bad request"
    val errors = (result.json \ "errors").as[Seq[JsValue]]
    errors.map(e => (e \ "code").as[String] shouldBe "VALIDATION_ERROR")
    errors.exists(e => (e \ "path").as[String].contains("/taxYear"))                                         shouldBe true
    errors.exists(e => (e \ "path").as[String].contains("/totalRecords"))                                    shouldBe true
    errors.exists(e => (e \ "path").as[String].contains("/submissionPeriod"))                                shouldBe true
    errors.exists(e => (e \ "message").as[String].contains("Tax year cannot be in the past"))                shouldBe true
    errors.exists(e => (e \ "message").as[String].contains("This field must be greater than or equal to 0")) shouldBe true
    errors.exists(e => (e \ "message").as[String].contains("Invalid month provided"))                        shouldBe true

  }
  "return 400 Bad Request when request fails validation with a missing fields" in {
    val invalidRequestJson: JsObject = Json.obj(
      "submissionPeriod" -> "January",
      "taxYear"          -> 2024
    )
    val result = initiateRequest(invalidRequestJson)

    result.status                        shouldBe BAD_REQUEST
    (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
    (result.json \ "message").as[String] shouldBe "Bad request"
    val errors = (result.json \ "errors").as[Seq[JsValue]]
    errors.exists(e => (e \ "code").as[String].contains( "MISSING_FIELD")) shouldBe true
    errors.exists(e => (e \ "message").as[String].contains( "This field is required")) shouldBe true
    errors.exists(e => (e \ "path").as[String].contains( "/totalRecords")) shouldBe true

  }
  "return 400 Bad Request when request fails validation with tax year not a whole number" in {
    val invalidRequestJson: JsObject = Json.obj(
      "totalRecords" -> 5000,
      "submissionPeriod" -> "January",
      "taxYear"          -> 2024.1
    )
    val result = initiateRequest(invalidRequestJson)

    result.status                        shouldBe BAD_REQUEST
    (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
    (result.json \ "message").as[String] shouldBe "Bad request"
    val errors = (result.json \ "errors").as[Seq[JsValue]]
    errors.exists(e => (e \ "code").as[String].contains( "VALIDATION_ERROR")) shouldBe true
    errors.exists(e => (e \ "message").as[String].contains( "Tax year must be a valid whole number")) shouldBe true
    errors.exists(e => (e \ "path").as[String].contains( "/taxYear")) shouldBe true

  }
  "return 400 Bad Request when request fails validation with tax year not an integer" in {
    val invalidRequestJson: JsObject = Json.obj(
      "totalRecords" -> 5000,
      "submissionPeriod" -> "January",
      "taxYear"          -> "2024"
    )
    val result = initiateRequest(invalidRequestJson)

    result.status                        shouldBe BAD_REQUEST
    (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
    (result.json \ "message").as[String] shouldBe "Bad request"
    val errors = (result.json \ "errors").as[Seq[JsValue]]
    errors.exists(e => (e \ "code").as[String].contains( "VALIDATION_ERROR")) shouldBe true
    errors.exists(e => (e \ "message").as[String].contains( "Tax year must be a number")) shouldBe true
    errors.exists(e => (e \ "path").as[String].contains( "/taxYear")) shouldBe true

  }
  "return 400 Bad Request when request fails validation with totalRecords not a valid number" in {
    val invalidRequestJson: JsObject = Json.obj(
      "totalRecords" -> "5000",
      "submissionPeriod" -> "January",
      "taxYear"          -> 2025
    )
    val result = initiateRequest(invalidRequestJson)

    result.status                        shouldBe BAD_REQUEST
    (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
    (result.json \ "message").as[String] shouldBe "Bad request"
    val errors = (result.json \ "errors").as[Seq[JsValue]]
    errors.exists(e => (e \ "code").as[String].contains( "VALIDATION_ERROR")) shouldBe true
    errors.exists(e => (e \ "message").as[String].contains( "This field must be greater than or equal to 0")) shouldBe true
    errors.exists(e => (e \ "path").as[String].contains( "/totalRecords")) shouldBe true

  }

  "return 500 Internal Server Error when upstream error returned from ppns" in {
    stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
    stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false))
    stubPPNSInternalServerError()

    val result = initiateRequest(validRequestJson)

    result.status                        shouldBe INTERNAL_SERVER_ERROR
    (result.json \ "code").as[String]    shouldBe "INTERNAL_SERVER_ERROR"
    (result.json \ "message").as[String] shouldBe "There has been an issue processing your request"
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
