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

package controllers

import org.mockito.ArgumentMatchers.{any, argThat, eq => eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{verify, when}
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.Play.materializer
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, NO_CONTENT}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{POST, contentAsJson, contentAsString, status}
import uk.gov.hmrc.disareturns.controllers.ReturnsSummaryController
import uk.gov.hmrc.disareturns.models.common.{InternalServerErr, Month}
import uk.gov.hmrc.disareturns.models.summary.repository.MonthlyReturnsSummary
import uk.gov.hmrc.disareturns.models.summary.repository.SaveReturnsSummaryResult.{Error, Saved}
import uk.gov.hmrc.disareturns.models.summary.request.MonthlyReturnsSummaryReq
import utils.BaseUnitSpec

import scala.concurrent.Future

class ReturnsSummaryControllerSpec extends BaseUnitSpec {

  private val controller = app.injector.instanceOf[ReturnsSummaryController]

  private val validZRef          = "Z1234"
  private val validMonth         = Month.SEP
  private val validYearStr       = "2025-26"
  private val expectedTaxEndYear = 2026
  private val totalRecords       = 3

  private val sampleBody = MonthlyReturnsSummaryReq(
    totalRecords = totalRecords
  )

  override def beforeEach(): Unit = Mockito.reset(mockReturnsSummaryService)

  "ReturnsSummaryController#returnsSummaryCallback" should {

    "returns 204 NoContent when the summary is stored" in {
      val req = FakeRequest(POST, s"/callback/monthly/$validZRef/$validYearStr/$validMonth")
        .withBody(Json.toJson(sampleBody))

      when(mockReturnsSummaryService.saveReturnsSummary(any)).thenReturn(Future.successful(Saved))

      val res = controller.returnsSummaryCallback(validZRef, validYearStr, validMonth.toString).apply(req)
      status(res) mustBe NO_CONTENT
      contentAsString(res) mustBe empty
    }

    "returns 500 with a custom InternalServerErr message when repo signals Error(msg)" in {
      val msg = "Downstream write failed"

      val req = FakeRequest(POST, s"/callback/monthly/$validZRef/$validYearStr/$validMonth")
        .withBody(Json.toJson(sampleBody))

      when(mockReturnsSummaryService.saveReturnsSummary(any)).thenReturn(Future.successful(Error(msg)))

      val res = controller.returnsSummaryCallback(validZRef, validYearStr, validMonth.toString).apply(req)

      status(res) mustBe INTERNAL_SERVER_ERROR
      (contentAsJson(res) \ "code").as[String] mustBe InternalServerErr().code
      (contentAsJson(res) \ "message").as[String] mustBe msg

      verify(mockReturnsSummaryService).saveReturnsSummary(argThat[MonthlyReturnsSummary] { summary =>
        summary.zRef == validZRef &&
        summary.taxYearEnd == expectedTaxEndYear &&
        summary.month == validMonth &&
        summary.totalRecords == totalRecords
      })
    }

    "return 400 BAD_REQUEST when Z-ref is invalid" in {
      val badZRef = "Z28973019"
      val req = FakeRequest(POST, s"/callback/monthly/$badZRef/$validYearStr/$validMonth")
        .withBody(Json.toJson(sampleBody))

      val res = controller.returnsSummaryCallback(badZRef, validYearStr, validMonth.toString).apply(req)

      status(res) mustBe BAD_REQUEST
    }

    "return 400 BAD_REQUEST when year token is invalid" in {
      val badYear = "2008"
      val req = FakeRequest(POST, s"/callback/monthly/$validZRef/$badYear/$validMonth")
        .withBody(Json.toJson(sampleBody))

      val res = controller.returnsSummaryCallback(validZRef, badYear, validMonth.toString).apply(req)

      status(res) mustBe BAD_REQUEST
    }

    "return 400 BAD_REQUEST when month token is invalid" in {
      val badMonth = "NOPE"

      val req = FakeRequest(POST, s"/callback/monthly/$validZRef/$validYearStr/$badMonth")
        .withBody(Json.toJson(sampleBody))

      val res = controller.returnsSummaryCallback(validZRef, validYearStr, badMonth).apply(req)

      status(res) mustBe BAD_REQUEST
    }

    "return 400 BAD_REQUEST when body is invalid" in {
      val req = FakeRequest(POST, s"/callback/monthly/$validZRef/$validYearStr/$validMonth")
        .withBody(Json.toJson("totalEntries" -> "fifty"))

      val res = controller.returnsSummaryCallback(validZRef, validYearStr, validMonth.toString).apply(req)

      status(res) mustBe BAD_REQUEST
    }

    "propagate correct params into the service (zRef, tax-year string, Month enum, totalRecords)" in {
      val req = FakeRequest(POST, s"/callback/monthly/$validZRef/$validYearStr/$validMonth")
        .withBody(Json.toJson(sampleBody))

      when(mockReturnsSummaryService.saveReturnsSummary(any))
        .thenReturn(Future.successful(Saved))

      val _ = controller.returnsSummaryCallback(validZRef, validYearStr, validMonth.toString).apply(req)

      verify(mockReturnsSummaryService).saveReturnsSummary(argThat[MonthlyReturnsSummary] { summary =>
        summary.zRef == validZRef &&
        summary.taxYearEnd == expectedTaxEndYear &&
        summary.month == validMonth &&
        summary.totalRecords == totalRecords
      })
    }
  }
}
