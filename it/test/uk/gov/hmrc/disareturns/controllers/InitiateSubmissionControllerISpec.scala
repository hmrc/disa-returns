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
import java.time.temporal.ChronoField.YEAR

import java.time.LocalDateTime

class InitiateSubmissionControllerISpec extends BaseIntegrationSpec {

  implicit val mongo: InitiateSubmissionRepository = app.injector.instanceOf[InitiateSubmissionRepository]
  val isaManagerRef = "Z123456"
  val initiateUrl   = s"/monthly/$isaManagerRef/init"

  val validRequestJson: JsObject = Json.obj(
    "totalRecords"     -> 100,
    "submissionPeriod" -> "APR",
    "taxYear"          -> LocalDateTime.now().getYear
  )

  val invalidRequestJson: JsObject = Json.obj(
    "totalRecords"     -> 9000,
    "submissionPeriod" -> "InvalidMonth",
    "taxYear"          -> LocalDateTime.now().getYear
  )

  def stubEtmpReportingWindow(status: Int, body: JsObject): Unit =
    stubFor(
      get(urlEqualTo("/etmp/check-reporting-window"))
        .willReturn(aResponse().withStatus(status).withBody(body.toString))
    )

  def stubEtmpObligation(status: Int, body: JsObject): Unit =
    stubFor(
      get(urlEqualTo(s"/etmp/check-obligation-status/$isaManagerRef"))
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

  "POST /monthly/:isaManagerRef/init" should {

    "return 200 OK when a valid request body is provided and ETMP checks are successful" in {
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

    "return 200 OK when a valid nil return request body is provided and ETMP checks are successful" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false))
      stubPPNSBoxId()

      val result = initiateRequest(
        validRequestJson +
          ("totalRecords" -> JsNumber(0))
      )

      result.status shouldBe OK

      val mongoRecord: Option[InitiateSubmission] = await(mongo.findByIsaManagerReference(isaManagerRef))
      val returnId = mongoRecord.map(_.returnId).get

      (result.json \ "returnId").as[String] shouldBe returnId
      (result.json \ "action").as[String]   shouldBe "NIL_RETURN_ACCEPTED_NO_FURTHER_ACTION"
      (result.json \ "boxId").as[String]    shouldBe "boxId1"
    }

    "return a 400 Bad Request when clientId is missing from the request header" in {
      val headersWithoutClientId: Seq[(String, String)] = Seq(
        "Authorization" -> "mock-bearer-token"
      )
      val result: WSResponse = initiateRequest(validRequestJson, headers = headersWithoutClientId)
      result.status                        shouldBe BAD_REQUEST
      (result.json \ "message").as[String] shouldBe "Missing required header: X-Client-ID"
    }

    "return a 400 Bad Request when isaManagerRef is invalid" in {
      val result: WSResponse = initiateRequest(validRequestJson, isaManagerReference = "NOT_VALID")
      result.status                        shouldBe BAD_REQUEST
      (result.json \ "message").as[String] shouldBe "ISA Manager Reference Number format is invalid"
    }

    "return 403 Forbidden when ETMP returns obligation already met" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> true))
      stubPPNSBoxId()

      val result = initiateRequest(validRequestJson)

      result.status                        shouldBe FORBIDDEN
      (result.json \ "code").as[String]    shouldBe "OBLIGATION_CLOSED"
      (result.json \ "message").as[String] shouldBe "Obligation closed"
    }

    "return 403 Forbidden when ETMP returns reporting window closed" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> false))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false))
      stubPPNSBoxId()

      val result = initiateRequest(validRequestJson)

      result.status                        shouldBe FORBIDDEN
      (result.json \ "code").as[String]    shouldBe "REPORTING_WINDOW_CLOSED"
      (result.json \ "message").as[String] shouldBe "Reporting window has been closed"
    }

    "return 403 Forbidden with correct errors when ETMP reporting window is closed and obligation already met" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> false))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> true))
      stubPPNSBoxId()

      val result = initiateRequest(validRequestJson)
      result.status                        shouldBe FORBIDDEN
      (result.json \ "code").as[String]    shouldBe "FORBIDDEN"
      (result.json \ "message").as[String] shouldBe "Multiple issues found regarding your submission"

      val errors = (result.json \ "errors").as[Seq[JsValue]]
      errors.map(e => (e \ "code").as[String]).head    shouldBe "REPORTING_WINDOW_CLOSED"
      errors.map(e => (e \ "message").as[String]).head shouldBe "Reporting window has been closed"

      errors.map(e => (e \ "code").as[String])(1)    shouldBe "OBLIGATION_CLOSED"
      errors.map(e => (e \ "message").as[String])(1) shouldBe "Obligation closed"
    }

    "return 400 Bad Request when request fails validation with invalid submissionPeriod" in {
      val result = initiateRequest(invalidRequestJson)

      result.status                        shouldBe BAD_REQUEST
      (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
      (result.json \ "message").as[String] shouldBe "Bad request"

      val errors = (result.json \ "errors").as[Seq[JsValue]]
      errors.map(e => (e \ "code").as[String] shouldBe "VALIDATION_ERROR")
      errors.map(e => (e \ "path").as[String] shouldBe "/submissionPeriod")
      errors.map(e => (e \ "message").as[String] shouldBe "Invalid month provided")
    }

    "return 400 Bad Request when request fails validation submissionPeriod provided is an integer" in {
      val result = initiateRequest(
        validRequestJson +
          ("submissionPeriod" -> JsNumber(1))
      )

      result.status                        shouldBe BAD_REQUEST
      (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
      (result.json \ "message").as[String] shouldBe "Bad request"

      val errors = (result.json \ "errors").as[Seq[JsValue]]
      errors.map(e => (e \ "code").as[String] shouldBe "VALIDATION_ERROR")
      errors.map(e => (e \ "path").as[String] shouldBe "/submissionPeriod")
      errors.map(e => (e \ "message").as[String] shouldBe "Invalid month provided must be a string")
    }

    "return 400 Bad Request when request fails validation with invalid tax year - tax year in future" in {
      val result = initiateRequest(validRequestJson + ("taxYear" -> JsNumber(LocalDateTime.now().plusYears(10).get(YEAR))))

      result.status                        shouldBe BAD_REQUEST
      (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
      (result.json \ "message").as[String] shouldBe "Bad request"

      val errors = (result.json \ "errors").as[Seq[JsValue]]
      errors.map(e => (e \ "code").as[String] shouldBe "INVALID_YEAR")
      errors.map(e => (e \ "path").as[String] shouldBe "/taxYear")
      errors.map(e => (e \ "message").as[String] shouldBe "Tax year must be the current tax year")
    }
  }

  "return 400 Bad Request when request fails validation with tax year not a whole number" in {
    val invalidRequestJson: JsObject = Json.obj(
      "totalRecords"     -> 5000,
      "submissionPeriod" -> "JAN",
      "taxYear"          -> 2024.1
    )

    val result = initiateRequest(invalidRequestJson)
    result.status                        shouldBe BAD_REQUEST
    (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
    (result.json \ "message").as[String] shouldBe "Bad request"

    val errors = (result.json \ "errors").as[Seq[JsValue]]
    errors.map(e => (e \ "code").as[String]).head    shouldBe "INVALID_YEAR"
    errors.map(e => (e \ "message").as[String]).head shouldBe "Tax year must be a valid whole number"
    errors.map(e => (e \ "path").as[String]).head    shouldBe "/taxYear"

  }

  "return 400 Bad Request when request fails validation with tax year not an integer" in {
    val invalidRequestJson: JsObject = Json.obj(
      "totalRecords"     -> 5000,
      "submissionPeriod" -> "JAN",
      "taxYear"          -> "2025"
    )

    val result = initiateRequest(invalidRequestJson)
    result.status                        shouldBe BAD_REQUEST
    (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
    (result.json \ "message").as[String] shouldBe "Bad request"

    val errors = (result.json \ "errors").as[Seq[JsValue]]
    errors.map(e => (e \ "code").as[String]).head    shouldBe "INVALID_YEAR"
    errors.map(e => (e \ "message").as[String]).head shouldBe "Tax year must be a number"
    errors.map(e => (e \ "path").as[String]).head    shouldBe "/taxYear"

  }

  "return 400 Bad Request when request fails validation with tax year is in the past" in {
    val invalidRequestJson: JsObject = Json.obj(
      "totalRecords"     -> 5000,
      "submissionPeriod" -> "JAN",
      "taxYear"          -> 2020
    )

    val result = initiateRequest(invalidRequestJson)
    result.status                        shouldBe BAD_REQUEST
    (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
    (result.json \ "message").as[String] shouldBe "Bad request"

    val errors = (result.json \ "errors").as[Seq[JsValue]]
    errors.map(e => (e \ "code").as[String]).head    shouldBe "INVALID_YEAR"
    errors.map(e => (e \ "message").as[String]).head shouldBe "Tax year cannot be in the past"
    errors.map(e => (e \ "path").as[String]).head    shouldBe "/taxYear"

  }

  //Total records validation

  "return 400 Bad Request when request fails validation with a negative value for totalRecords" in {
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
    errors.map(e => (e \ "code").as[String]).head    shouldBe "VALIDATION_ERROR"
    errors.map(e => (e \ "path").as[String]).head    shouldBe "/totalRecords"
    errors.map(e => (e \ "message").as[String]).head shouldBe "This field must be greater than or equal to 0"
  }

  "return 400 Bad Request when request fails validation with a missing fields" in {
    val invalidRequestJson: JsObject = Json.obj(
      "submissionPeriod" -> "JAN",
      "taxYear"          -> 2025
    )

    val result = initiateRequest(invalidRequestJson)
    result.status                        shouldBe BAD_REQUEST
    (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
    (result.json \ "message").as[String] shouldBe "Bad request"

    val errors = (result.json \ "errors").as[Seq[JsValue]]
    errors.map(e => (e \ "code").as[String]).head    shouldBe "MISSING_FIELD"
    errors.map(e => (e \ "message").as[String]).head shouldBe "This field is required"
    errors.map(e => (e \ "path").as[String]).head    shouldBe "/totalRecords"
  }

  "return 400 Bad Request when request fails validation with totalRecords not a valid number" in {
    val invalidRequestJson: JsObject = Json.obj(
      "totalRecords"     -> "5000",
      "submissionPeriod" -> "JAN",
      "taxYear"          -> 2025
    )

    val result = initiateRequest(invalidRequestJson)
    result.status                        shouldBe BAD_REQUEST
    (result.json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
    (result.json \ "message").as[String] shouldBe "Bad request"

    val errors = (result.json \ "errors").as[Seq[JsValue]]
    errors.map(e => (e \ "code").as[String]).head    shouldBe "VALIDATION_ERROR"
    errors.map(e => (e \ "message").as[String]).head shouldBe "This field must be greater than or equal to 0"
    errors.map(e => (e \ "path").as[String]).head    shouldBe "/totalRecords"
  }

  "return 400 Bad Request when request fails with two validation errors: totalRecords and taxYear" in {
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
    errors.map(e => (e \ "code").as[String]).head    shouldBe "INVALID_YEAR"
    errors.map(e => (e \ "path").as[String]).head    shouldBe "/taxYear"
    errors.map(e => (e \ "message").as[String]).head shouldBe "Tax year cannot be in the past"

    errors.map(e => (e \ "code").as[String])(1)    shouldBe "VALIDATION_ERROR"
    errors.map(e => (e \ "path").as[String])(1)    shouldBe "/totalRecords"
    errors.map(e => (e \ "message").as[String])(1) shouldBe "This field must be greater than or equal to 0"
  }

  "return 400 Bad Request when request fails validation with three validation errors: totalRecords, taxYear and submissionPeriod" in {
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
    errors.map(e => (e \ "code").as[String]).head    shouldBe "INVALID_YEAR"
    errors.map(e => (e \ "path").as[String]).head    shouldBe "/taxYear"
    errors.map(e => (e \ "message").as[String]).head shouldBe "Tax year cannot be in the past"

    errors.map(e => (e \ "code").as[String])(1)    shouldBe "VALIDATION_ERROR"
    errors.map(e => (e \ "path").as[String])(1)    shouldBe "/submissionPeriod"
    errors.map(e => (e \ "message").as[String])(1) shouldBe "Invalid month provided"

    errors.map(e => (e \ "code").as[String])(2)    shouldBe "VALIDATION_ERROR"
    errors.map(e => (e \ "path").as[String])(2)    shouldBe "/totalRecords"
    errors.map(e => (e \ "message").as[String])(2) shouldBe "This field must be greater than or equal to 0"
  }

  "return 500 Internal Server Error when upstream 500 Server Error returned from PPNS" in {
    stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
    stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false))
    stubFor(
      get(urlEqualTo(s"/box?clientId=$testClientId&boxName=obligations/declaration/isa/return%23%231.0%23%23callbackUrl"))
        .willReturn(serverError)
    )
    val result = initiateRequest(validRequestJson)

    result.status                        shouldBe INTERNAL_SERVER_ERROR
    (result.json \ "code").as[String]    shouldBe "INTERNAL_SERVER_ERROR"
    (result.json \ "message").as[String] shouldBe "There has been an issue processing your request"
  }

  "return 500 Internal Server Error when upstream 503 serviceUnavailable returned from PPNS" in {
    stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
    stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false))
    stubFor(
      get(urlEqualTo(s"/box?clientId=$testClientId&boxName=obligations/declaration/isa/return%23%231.0%23%23callbackUrl"))
        .willReturn(serverError)
    )
    val result = initiateRequest(validRequestJson)

    result.status                        shouldBe INTERNAL_SERVER_ERROR
    (result.json \ "code").as[String]    shouldBe "INTERNAL_SERVER_ERROR"
    (result.json \ "message").as[String] shouldBe "There has been an issue processing your request"
  }

  def initiateRequest(
    requestBody:         JsObject,
    headers:             Seq[(String, String)] = testHeaders,
    isaManagerReference: String = isaManagerRef
  ): WSResponse = {
    stubAuth()
    mongo.dropCollection()
    await(
      ws.url(
        s"http://localhost:$port/monthly/$isaManagerReference/init"
      ).withFollowRedirects(follow = false)
        .withHttpHeaders(
          headers: _*
        )
        .post(requestBody)
    )
  }
}
