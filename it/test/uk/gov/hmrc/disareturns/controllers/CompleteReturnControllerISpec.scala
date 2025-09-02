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
import play.api.http.Status._
import play.api.libs.json._
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.common.Month
import uk.gov.hmrc.disareturns.models.initiate.inboundRequest.{SubmissionRequest, TaxYear}
import uk.gov.hmrc.disareturns.models.initiate.mongo.ReturnMetadata
import uk.gov.hmrc.disareturns.models.submission.isaAccounts.{IsaType, LifetimeIsaClosure, ReasonForClosure}
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec

import java.time.LocalDate

class CompleteReturnControllerISpec extends BaseIntegrationSpec {
  val isaManagerReference        = "Z1111"
  val invalidIsaManagerReference = "Z1111000000000"
  val returnId                   = "Return-1234"
  val invalidReturnId            = "invalid"
  val returnSummaryLocation      = s"/monthly/$isaManagerReference/$returnId/results/summary"

  "POST /monthly/:isaManagerReferenceNumber/:returnId/complete" should {

    "return 200 OK for a successful request" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerReference)
      stubCloseEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> true), isaManagerRef = isaManagerReference)

      val result = completeRequest()

      result.status shouldBe OK

      (result.json \ "returnResultsSummaryLocation").as[String] shouldBe returnSummaryLocation
    }

    "return 400 BadRequest for an invalid isaManagerReference" in {
      val result = completeRequest(isaManagerReference = invalidIsaManagerReference)

      result.status shouldBe BAD_REQUEST

      (result.json \ "message").as[String] shouldBe "ISA Manager Reference Number format is invalid"
    }

    "return 404 NotFound when the provided returnId does not exist" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerReference)

      val result = completeRequest(returnId = invalidReturnId)

      result.status shouldBe NOT_FOUND

      (result.json \ "code").as[String]    shouldBe "RETURN_ID_NOT_FOUND"
      (result.json \ "message").as[String] shouldBe "The provided returnId could not be found"
    }

    "return 403 Forbidden when the reporting window is closed" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> false))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerReference)

      val result = completeRequest()

      result.status shouldBe FORBIDDEN

      (result.json \ "code").as[String]    shouldBe "REPORTING_WINDOW_CLOSED"
      (result.json \ "message").as[String] shouldBe "Reporting window has been closed"
    }

    "return 403 Forbidden when the obligation status is closed" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> true), isaManagerRef = isaManagerReference)

      val result = completeRequest()

      result.status shouldBe FORBIDDEN

      (result.json \ "code").as[String]    shouldBe "OBLIGATION_CLOSED"
      (result.json \ "message").as[String] shouldBe "Obligation closed"
    }
    "return 403 Forbidden when the obligation status and reporting window are closed" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> false))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> true), isaManagerRef = isaManagerReference)

      val result = completeRequest()

      result.status shouldBe FORBIDDEN

      (result.json \ "code").as[String]    shouldBe "FORBIDDEN"
      (result.json \ "message").as[String] shouldBe "Multiple issues found regarding your submission"

      val errors = (result.json \ "errors").as[Seq[JsValue]]
      errors.map(e => (e \ "code").as[String]).head    shouldBe "REPORTING_WINDOW_CLOSED"
      errors.map(e => (e \ "message").as[String]).head shouldBe "Reporting window has been closed"

      errors.map(e => (e \ "code").as[String])(1)    shouldBe "OBLIGATION_CLOSED"
      errors.map(e => (e \ "message").as[String])(1) shouldBe "Obligation closed"
    }

    "return 400 BadRequest Mismatch when number of records declared in the header does not match the number submitted" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerReference)

      val result = completeRequest(isaManagerReference = isaManagerReference, returnId = returnId, submissions = false)
      result.status shouldBe BAD_REQUEST

      (result.json \ "code").as[String]    shouldBe "MISMATCH_EXPECTED_VS_RECEIVED"
      (result.json \ "message").as[String] shouldBe "Number of records declared in the header does not match the number submitted."
    }

    "return 500 Internal server error when Etmp returns a server error" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubFor(
        get(urlEqualTo(s"/etmp/check-obligation-status/$isaManagerReference"))
          .willReturn(serverError)
      )

      val result = completeRequest()

      result.status shouldBe INTERNAL_SERVER_ERROR

      (result.json \ "code").as[String]    shouldBe "INTERNAL_SERVER_ERROR"
      (result.json \ "message").as[String] shouldBe "There has been an issue processing your request"
    }

    "return 500 Internal server error when Etmp closeReturnsObligationStatus returns a server error" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerReference)
      stubFor(
        post(urlEqualTo(s"/etmp/close-obligation-status/$isaManagerReference"))
          .willReturn(serverError)
      )

      val result = completeRequest()

      result.status shouldBe INTERNAL_SERVER_ERROR

      (result.json \ "code").as[String]    shouldBe "INTERNAL_SERVER_ERROR"
      (result.json \ "message").as[String] shouldBe "There has been an issue processing your request"
    }

  }

  def completeRequest(
    requestBody:         String = "",
    headers:             Seq[(String, String)] = testHeaders,
    isaManagerReference: String = isaManagerReference,
    returnId:            String = returnId,
    submissions:         Boolean = true
  ): WSResponse = {
    stubAuth()
    await(returnMetadataRepository.dropCollection())
    await(reportingMetadataRepository.dropCollection())
    setupReturnMetadata()
    if (submissions) setupMonthlySubmissions()
    await(
      ws.url(
        s"http://localhost:$port/monthly/$isaManagerReference/$returnId/complete"
      ).withFollowRedirects(follow = false)
        .withHttpHeaders(
          headers: _*
        )
        .post(requestBody)
    )
  }

  def setupReturnMetadata(): String =
    await(
      returnMetadataRepository.insert(
        ReturnMetadata(
          returnId = returnId,
          boxId = "box-id",
          submissionRequest = SubmissionRequest(totalRecords = 1, submissionPeriod = Month.JAN, taxYear = TaxYear(LocalDate.now.getYear)),
          isaManagerReference = isaManagerReference
        )
      )
    )

  def setupMonthlySubmissions(): Unit = await(
    reportingMetadataRepository.insertBatch(
      isaManagerId = isaManagerReference,
      returnId = returnId,
      reports = Seq(
        LifetimeIsaClosure(
          accountNumber = "STD000001",
          nino = "AB000001C",
          firstName = "First1",
          middleName = None,
          lastName = "Last1",
          dateOfBirth = LocalDate.parse("1980-01-02"),
          isaType = IsaType.LIFETIME_CASH,
          reportingATransfer = false,
          dateOfLastSubscription = LocalDate.parse("2025-06-01"),
          totalCurrentYearSubscriptionsToDate = BigDecimal(2500.00),
          marketValueOfAccount = BigDecimal(10000.00),
          dateOfFirstSubscription = LocalDate.parse("2025-06-01"),
          closureDate = LocalDate.parse("2025-06-01"),
          reasonForClosure = ReasonForClosure.CANCELLED,
          lisaQualifyingAddition = BigDecimal(10000.00),
          lisaBonusClaim = BigDecimal(10000.00)
        )
      )
    )
  )
}
