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

import org.mockito.ArgumentMatchers.{any, argThat}
import org.mockito.Mockito
import org.mockito.Mockito.{verify, when}
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.Play.materializer
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, POST, contentAsJson, contentAsString, status}
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.disareturns.controllers.ReturnsSummaryController
import uk.gov.hmrc.disareturns.models.common.{InternalServerErr, ReturnNotFoundErr}
import uk.gov.hmrc.disareturns.models.summary.repository.MonthlyReturnsSummary
import uk.gov.hmrc.disareturns.models.summary.request.MonthlyReturnsSummaryReq
import uk.gov.hmrc.disareturns.models.summary.ReturnSummaryResults
import utils.BaseUnitSpec

import scala.concurrent.Future

class ReturnsSummaryControllerSpec extends BaseUnitSpec {

  private val controller = app.injector.instanceOf[ReturnsSummaryController]

  private val totalRecords          = 3
  private val returnResultsLocation = "url"
  private val numberOfPages         = 1

  private val returnSummaryResult = ReturnSummaryResults(returnResultsLocation, totalRecords, numberOfPages)

  private val callbackRequest = MonthlyReturnsSummaryReq(
    totalRecords = totalRecords
  )

  override def beforeEach(): Unit = Mockito.reset(mockReturnsSummaryService)

  "ReturnsSummaryController#retrieveReturnSummary" should {

    "return 200 with return result summary" in {
      val req = FakeRequest(GET, s"/monthly/$validZRef/$validTaxYear/$validMonth/results/summary")

      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockReturnsSummaryService.retrieveReturnSummary(any, any, any)).thenReturn(Future.successful(Right(returnSummaryResult)))

      val res = controller.retrieveReturnSummary(validZRef, validTaxYear, validMonth.toString).apply(req)

      status(res) mustBe OK
      contentAsJson(res) shouldBe Json.obj(
        "returnResultsLocation" -> returnResultsLocation,
        "totalRecords"          -> totalRecords,
        "numberOfPages"         -> numberOfPages
      )
    }

    "return 404 Return Not Found when no summary is available" in {
      val req = FakeRequest(GET, s"/monthly/$validZRef/$validTaxYear/$validMonth/results/summary")

      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockReturnsSummaryService.retrieveReturnSummary(any, any, any)).thenReturn(Future.successful(Left(ReturnNotFoundErr("not found"))))

      val res = controller.retrieveReturnSummary(validZRef, validTaxYear, validMonth.toString).apply(req)

      status(res) mustBe NOT_FOUND
      contentAsJson(res) shouldBe Json.toJson(ReturnNotFoundErr("not found"))
    }

    "return 500 Internal server error when a server issue occurs" in {
      val req = FakeRequest(GET, s"/monthly/$validZRef/$validTaxYear/$validMonth/results/summary")

      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockReturnsSummaryService.retrieveReturnSummary(any, any, any)).thenReturn(Future.successful(Left(InternalServerErr())))

      val res = controller.retrieveReturnSummary(validZRef, validTaxYear, validMonth.toString).apply(req)

      status(res) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(res) shouldBe Json.toJson(InternalServerErr())
    }

    "return 400 BAD_REQUEST when Z-ref is invalid" in {
      val badZRef = "Z28973019"
      val req = FakeRequest(GET, s"/monthly/$badZRef/$validTaxYear/$validMonth/results/summary")
        .withBody(Json.toJson(callbackRequest))

      val res = controller.retrieveReturnSummary(badZRef, validTaxYear, validMonth.toString).apply(req)

      status(res) mustBe BAD_REQUEST
    }

    "return 400 BAD_REQUEST when year token is invalid" in {
      val badYear = "2008"
      val req = FakeRequest(GET, s"/monthly/$validZRef/$badYear/$validMonth/results/summary")
        .withBody(Json.toJson(callbackRequest))

      val res = controller.retrieveReturnSummary(validZRef, badYear, validMonth.toString).apply(req)

      status(res) mustBe BAD_REQUEST
    }

    "return 400 BAD_REQUEST when month token is invalid" in {
      val badMonth = "NOPE"

      val req = FakeRequest(GET, s"/monthly/$validZRef/$validTaxYear/$badMonth/results/summary")
        .withBody(Json.toJson(callbackRequest))

      val res = controller.retrieveReturnSummary(validZRef, validTaxYear, badMonth).apply(req)

      status(res) mustBe BAD_REQUEST
    }
  }
  "ReturnsSummaryController#returnsSummaryCallback" should {

    "returns 204 NoContent when the summary is stored" in {
      val req = FakeRequest(POST, s"/callback/monthly/$validZRef/$validTaxYear/$validMonth")
        .withBody(Json.toJson(callbackRequest))

      when(mockReturnsSummaryService.saveReturnsSummary(any)).thenReturn(Future.successful(Right(())))

      val res = controller.returnsSummaryCallback(validZRef, validTaxYear, validMonth.toString).apply(req)
      status(res) mustBe NO_CONTENT
      contentAsString(res) mustBe empty
    }

    "returns 500 with a custom InternalServerErr message when repo signals Error(msg)" in {
      val msg = "Downstream write failed"

      val req = FakeRequest(POST, s"/callback/monthly/$validZRef/$validTaxYear/$validMonth")
        .withBody(Json.toJson(callbackRequest))

      when(mockReturnsSummaryService.saveReturnsSummary(any)).thenReturn(Future.successful(Left(InternalServerErr(msg))))

      val res = controller.returnsSummaryCallback(validZRef, validTaxYear, validMonth.toString).apply(req)

      status(res) mustBe INTERNAL_SERVER_ERROR
      (contentAsJson(res) \ "code").as[String] mustBe InternalServerErr().code
      (contentAsJson(res) \ "message").as[String] mustBe msg

      verify(mockReturnsSummaryService).saveReturnsSummary(argThat[MonthlyReturnsSummary] { summary =>
        summary.zRef == validZRef &&
        summary.taxYear == validTaxYear &&
        summary.month == validMonth &&
        summary.totalRecords == totalRecords
      })
    }

    "return 400 BAD_REQUEST when Z-ref is invalid" in {
      val badZRef = "Z28973019"
      val req = FakeRequest(POST, s"/callback/monthly/$badZRef/$validTaxYear/$validMonth")
        .withBody(Json.toJson(callbackRequest))

      val res = controller.returnsSummaryCallback(badZRef, validTaxYear, validMonth.toString).apply(req)

      status(res) mustBe BAD_REQUEST
    }

    "return 400 BAD_REQUEST when year token is invalid" in {
      val badYear = "2008"
      val req = FakeRequest(POST, s"/callback/monthly/$validZRef/$badYear/$validMonth")
        .withBody(Json.toJson(callbackRequest))

      val res = controller.returnsSummaryCallback(validZRef, badYear, validMonth.toString).apply(req)

      status(res) mustBe BAD_REQUEST
    }

    "return 400 BAD_REQUEST when month token is invalid" in {
      val badMonth = "NOPE"

      val req = FakeRequest(POST, s"/callback/monthly/$validZRef/$validTaxYear/$badMonth")
        .withBody(Json.toJson(callbackRequest))

      val res = controller.returnsSummaryCallback(validZRef, validTaxYear, badMonth).apply(req)

      status(res) mustBe BAD_REQUEST
    }

    "return 400 BAD_REQUEST when body is invalid" in {
      val req = FakeRequest(POST, s"/callback/monthly/$validZRef/$validTaxYear/$validMonth")
        .withBody(Json.toJson("totalEntries" -> "fifty"))

      val res = controller.returnsSummaryCallback(validZRef, validTaxYear, validMonth.toString).apply(req)

      status(res) mustBe BAD_REQUEST
    }

    "propagate correct params into the service (zRef, tax-year string, Month enum, totalRecords)" in {
      val req = FakeRequest(POST, s"/callback/monthly/$validZRef/$validTaxYear/$validMonth")
        .withBody(Json.toJson(callbackRequest))

      when(mockReturnsSummaryService.saveReturnsSummary(any))
        .thenReturn(Future.successful(Right(())))

      val _ = controller.returnsSummaryCallback(validZRef, validTaxYear, validMonth.toString).apply(req)

      verify(mockReturnsSummaryService).saveReturnsSummary(argThat[MonthlyReturnsSummary] { summary =>
        summary.zRef == validZRef &&
        summary.taxYear == validTaxYear &&
        summary.month == validMonth &&
        summary.totalRecords == totalRecords
      })
    }
  }
}
