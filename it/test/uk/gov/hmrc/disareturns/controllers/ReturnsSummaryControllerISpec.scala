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

import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.libs.json._
import play.api.libs.ws.WSResponse
import play.api.test.Helpers._
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InvalidIsaManagerRef, InvalidMonth, InvalidTaxYear, Month, MultipleErrorResponse}
import uk.gov.hmrc.disareturns.models.summary.repository.MonthlyReturnsSummary
import uk.gov.hmrc.disareturns.repositories.MonthlyReturnsSummaryRepository
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec

class ReturnsSummaryControllerISpec extends BaseIntegrationSpec {

  private lazy val repo      = app.injector.instanceOf[MonthlyReturnsSummaryRepository]
  private lazy val appConfig = app.injector.instanceOf[AppConfig]

  override def beforeAll(): Unit = {
    super.beforeAll()
    await(repo.collection.drop().toFuture())
  }

  private val isaManagerRef = "Z1234"
  private val taxYear       = "2025-26"
  private val monthEnum     = Month.SEP
  private val monthToken    = monthEnum.toString
  private val totalRecords  = 3
  val invalidZRef           = "Z1111000000000"
  val invalidTaxYear        = "2025-27"
  val invalidMonth          = "SEPT"

  private val returnsSummaryJson = Json.obj("totalRecords" -> totalRecords)

  "POST /callback/monthly/:zRef/:year/:month" should {

    "return 204 and persist a MonthlyReturnsSummary document" in {
      val result = returnsSummaryCallbackRequest(requestBody = returnsSummaryJson)

      result.status mustBe NO_CONTENT
      result.body mustBe empty

      val stored = await(repo.collection.find().toFuture())

      stored must have size 1
      val doc = stored.head
      doc.zRef mustBe isaManagerRef
      doc.taxYear mustBe taxYear
      doc.month mustBe monthEnum
      doc.totalRecords mustBe totalRecords
    }

    "return 400 with aggregated issues when zRef, taxYear and month are all invalid" in {

      val res = returnsSummaryCallbackRequest(Json.obj("totalRecords" -> 1), invalidZRef, invalidTaxYear, invalidMonth)

      res.status mustBe BAD_REQUEST
      res.json
        .as[ErrorResponse] shouldBe MultipleErrorResponse(code = "BAD_REQUEST", errors = Seq(InvalidIsaManagerRef, InvalidTaxYear, InvalidMonth))
    }

    "return 400 with correct error response when an invalid isaManagerReference is provided" in {
      val res = returnsSummaryCallbackRequest(Json.obj("totalRecords" -> 1), isaManagerReference = invalidZRef)

      res.status mustBe BAD_REQUEST
      res.json
        .as[ErrorResponse] shouldBe InvalidIsaManagerRef
    }

    "return 400 with correct error response when an invalid taxYear is provided" in {
      val res = returnsSummaryCallbackRequest(Json.obj("totalRecords" -> 1), taxYear = invalidTaxYear)

      res.status mustBe BAD_REQUEST
      res.json
        .as[ErrorResponse] shouldBe InvalidTaxYear
    }

    "return 400 with correct error response when an invalid month is provided" in {
      val res = returnsSummaryCallbackRequest(Json.obj("totalRecords" -> 1), month = invalidMonth)

      res.status mustBe BAD_REQUEST
      res.json
        .as[ErrorResponse] shouldBe InvalidMonth
    }

    "return 400 when the JSON body is invalid or missing required fields" in {
      val res1 = returnsSummaryCallbackRequest(Json.obj())
      res1.status mustBe BAD_REQUEST

      val res2 = returnsSummaryCallbackRequest(Json.obj("totalRecords" -> "three"))
    }
  }

  "GET /monthly/:zRef/:year/:month/results/summary" should {

    "return 200 and a ReturnResultsSummary when the summary exists" in {
      await(repo.collection.drop().toFuture())
      await(repo.collection.insertOne(MonthlyReturnsSummary(isaManagerRef, taxYear, monthEnum, totalRecords)).toFuture())

      stubAuth()
      val res: WSResponse =
        await(
          ws.url(s"http://localhost:$port/monthly/$isaManagerRef/$taxYear/$monthToken/results/summary")
            .withFollowRedirects(follow = false)
            .withHttpHeaders(testHeaders: _*)
            .get()
        )

      res.status mustBe OK
      (res.json \ "returnResultsLocation").as[String] mustBe s"${appConfig.selfHost}${routes.ReconciliationResultController.retrieveReconciliationReportPage(isaManagerRef, taxYear, monthToken).url}"
      (res.json \ "numberOfPages").as[Int] mustBe appConfig.getNoOfPagesForReturnResults(totalRecords).get
      (res.json \ "totalRecords").as[Int] mustBe totalRecords
    }

    "return 404 when no summary is found" in {
      val res = retrieveReturnsSummaryRequest()

      res.status mustBe NOT_FOUND
      (res.json \ "code").as[String] mustBe "RETURN_NOT_FOUND"
      (res.json \ "message").as[String] mustBe "No return found for Z1234 for SEP 2025-26"
    }

    "return 400 with aggregated issues when zRef, taxYear and month are invalid" in {
      val res = retrieveReturnsSummaryRequest(
        isaManagerReference = invalidZRef,
        taxYear = invalidTaxYear,
        month = invalidMonth
      )
      res.status mustBe BAD_REQUEST
      res.json
        .as[ErrorResponse] shouldBe MultipleErrorResponse(code = "BAD_REQUEST", errors = Seq(InvalidIsaManagerRef, InvalidTaxYear, InvalidMonth))
    }
  }

  def retrieveReturnsSummaryRequest(
    headers:             Seq[(String, String)] = testHeaders,
    isaManagerReference: String = isaManagerRef,
    taxYear:             String = taxYear,
    month:               String = monthEnum.toString
  ): WSResponse = {
    stubAuth()
    await(repo.collection.drop().toFuture())
    await(
      ws.url(
        s"http://localhost:$port/monthly/$isaManagerReference/$taxYear/$month/results/summary"
      ).withFollowRedirects(follow = false)
        .withHttpHeaders(
          headers: _*
        )
        .get()
    )
  }

  def returnsSummaryCallbackRequest(
    requestBody:         JsObject,
    isaManagerReference: String = isaManagerRef,
    taxYear:             String = taxYear,
    month:               String = monthEnum.toString,
    headers:             Seq[(String, String)] = Seq("Authorization" -> "mock-bearer-token")
  ): WSResponse = {
    await(repo.collection.drop().toFuture())
    await(
      ws.url(
        s"http://localhost:$port/callback/monthly/$isaManagerReference/$taxYear/$month"
      ).withFollowRedirects(follow = false)
        .withHttpHeaders(
          headers: _*
        )
        .post(requestBody)
    )
  }
}
