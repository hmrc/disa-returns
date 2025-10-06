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
import uk.gov.hmrc.disareturns.models.common.Month
import uk.gov.hmrc.disareturns.models.summary.repository.MonthlyReturnsSummary
import uk.gov.hmrc.disareturns.repositories.MonthlyReturnsSummaryRepository
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec

class ReturnsSummaryControllerISpec extends BaseIntegrationSpec {

  private lazy val repo  = app.injector.instanceOf[MonthlyReturnsSummaryRepository]
  private lazy val appConfig = app.injector.instanceOf[AppConfig]

  override def beforeAll(): Unit = {
    super.beforeAll()
    await(repo.collection.drop().toFuture())
  }

  private val zRef        = "Z1234"
  private val taxYear     = "2025-26"
  private val monthEnum   = Month.SEP
  private val monthToken  = monthEnum.toString
  private val totalRecords = 3

  private val returnsSummaryJson = Json.obj("totalRecords" -> totalRecords)

  "POST /callback/monthly/:zRef/:year/:month" should {

    "return 204 and persist a MonthlyReturnsSummary document" in {
      val result = returnsSummaryCallbackRequest(returnsSummaryJson, zRef, taxYear, monthToken)

      result.status mustBe NO_CONTENT
      result.body mustBe empty

      val stored = await(repo.collection.find().toFuture())

      stored must have size 1
      val doc = stored.head
      doc.zRef         mustBe zRef
      doc.taxYear   mustBe taxYear
      doc.month        mustBe monthEnum
      doc.totalRecords mustBe totalRecords
    }

    "return 400 with aggregated issues when zRef, taxYear and month are all invalid" in {
      val invalidZRef    = "Z1111000000000"
      val invalidTaxYear = "2025-27"
      val invalidMonth   = "SEPT"

      val res = returnsSummaryCallbackRequest(Json.obj("totalRecords" -> 1), invalidZRef, invalidTaxYear, invalidMonth)

      res.status mustBe BAD_REQUEST

      (res.json \ "code").as[String]    mustBe "BAD_REQUEST"
      (res.json \ "message").as[String] mustBe "Issue(s) with your request"

      val errors = (res.json \ "errors").as[Seq[JsValue]]
      errors.map(e => (e \ "message").as[String]) must contain ("ISA Manager Reference Number format is invalid")
      errors.map(e => (e \ "message").as[String]) must contain ("Invalid parameter for tax year")
      errors.map(e => (e \ "message").as[String]) must contain ("Invalid parameter for month")
    }

    "return 400 when the JSON body is invalid or missing required fields" in {
      val res1 = returnsSummaryCallbackRequest(Json.obj(), zRef, taxYear, monthToken)
      res1.status mustBe BAD_REQUEST

      val res2 = returnsSummaryCallbackRequest(Json.obj("totalRecords" -> "three"), zRef, taxYear, monthToken)
      res2.status mustBe BAD_REQUEST
    }
  }

  "GET /monthly/:zRef/:year/:month/results/summary" should {

    "return 200 and a ReturnResultsSummary when the summary exists" in {
      await(repo.collection.drop().toFuture())
      await(repo.collection.insertOne(MonthlyReturnsSummary(zRef, taxYear, monthEnum, totalRecords)).toFuture())

      stubAuth()
      val res: WSResponse =
        await(
          ws.url(s"http://localhost:$port/monthly/$zRef/$taxYear/$monthToken/results/summary")
            .withFollowRedirects(follow = false)
            .withHttpHeaders(testHeaders: _*)
            .get()
        )

      res.status mustBe OK
      (res.json \ "returnResultsLocation").as[String]         mustBe appConfig.getReturnResultsLocation(zRef, taxYear, monthEnum)
      (res.json \ "numberOfPages").as[Int]        mustBe appConfig.returnResultsNumberOfPages
      (res.json \ "totalRecords").as[Int]    mustBe totalRecords
    }

    "return 404 when no summary is found" in {
      val res = retrieveReturnsSummaryRequest(
        isaManagerReference = zRef,
        taxYear             = taxYear,
        month               = monthToken
      )

      res.status mustBe NOT_FOUND
      (res.json \ "code").as[String] mustBe "RETURN_NOT_FOUND"
      (res.json \ "message").as[String] mustBe "No return found for Z1234 for SEP 2025-26"
    }

    "return 400 with aggregated issues when zRef, taxYear and month are invalid" in {
      val res = retrieveReturnsSummaryRequest(
        isaManagerReference = "Z1111000000000",
        taxYear             = "2025-27",
        month               = "SEPT"
      )

      res.status mustBe BAD_REQUEST
      (res.json \ "code").as[String]    mustBe "BAD_REQUEST"
      (res.json \ "message").as[String] mustBe "Issue(s) with your request"

      val errors = (res.json \ "errors").as[Seq[JsValue]]
      errors.map(e => (e \ "message").as[String]) must contain ("ISA Manager Reference Number format is invalid")
      errors.map(e => (e \ "message").as[String]) must contain ("Invalid parameter for tax year")
      errors.map(e => (e \ "message").as[String]) must contain ("Invalid parameter for month")
    }
  }

  def retrieveReturnsSummaryRequest(
                       headers:             Seq[(String, String)] = testHeaders,
                       isaManagerReference: String,
                       taxYear: String,
                       month: String
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
                                     requestBody: JsObject,
                                     isaManagerReference: String,
                                     taxYear: String,
                                     month: String,
                                     headers:             Seq[(String, String)] = Seq("Authorization" -> "mock-bearer-token"),
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