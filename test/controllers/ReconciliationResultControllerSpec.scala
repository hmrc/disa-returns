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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsJson, status}
import uk.gov.hmrc.auth.core.InvalidBearerToken
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.disareturns.controllers.ReconciliationResultController
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.returnResults.{IssueWithMessage, ReconciliationReportPage, ReturnResults}
import utils.BaseUnitSpec

import scala.concurrent.Future

class ReconciliationResultControllerSpec extends BaseUnitSpec {

  private val controller = app.injector.instanceOf[ReconciliationResultController]

  private val validPageIndex = "0"
  private val reconciliationReportPage = ReconciliationReportPage(
    validPageIndex.toInt,
    2,
    3,
    2,
    Seq(
      ReturnResults("1", "A", IssueWithMessage("code", "message")),
      ReturnResults("2", "B", IssueWithMessage("code", "message"))
    )
  )

  "controller.retieveReconciliationReportPage" should {

    "return 200 with a page of results" in {
      val req = FakeRequest(GET, s"/monthly/$validZReference/$validTaxYear/$validMonth/results?page=$validPageIndex")

      authorizationForZRef()
      when(mockNPSService.retrieveReconciliationReportPage(any, any, any, any)(any)).thenReturn(Future.successful(Right(reconciliationReportPage)))

      val res = controller.retrieveReconciliationReportPage(validZReference, validTaxYear, validMonthStr, validPageIndex).apply(req)

      status(res) mustBe OK
      contentAsJson(res) shouldBe Json.toJson(reconciliationReportPage)
    }

    "return 400 with a validation error when a parameter is invalid" in {
      val req = FakeRequest(GET, s"/monthly/$validZReference/$validTaxYear/$validMonth/results?page=-1")

      authorizationForZRef()

      val res = controller.retrieveReconciliationReportPage(validZReference, validTaxYear, validMonthStr, "-1").apply(req)

      status(res) mustBe BAD_REQUEST
      contentAsJson(res).as[ErrorResponse] shouldBe InvalidPageErr
    }

    "return 400 with multiple validation errors when more than one parameter is invalid" in {
      val req = FakeRequest(GET, s"/monthly/$validZReference/$validTaxYear/$validMonth/results?page=-1")

      authorizationForZRef()

      val res = controller.retrieveReconciliationReportPage(validZReference, validTaxYear, "month", "-1").apply(req)

      status(res) mustBe BAD_REQUEST
      contentAsJson(res) shouldBe Json.toJson(MultipleErrorResponse(code = "BAD_REQUEST", errors = Seq(InvalidMonth, InvalidPageErr)))
    }

    "return 404 with an error when the report page is not found" in {
      val req = FakeRequest(GET, s"/monthly/$validZReference/$validTaxYear/$validMonth/results?page=$validPageIndex")

      authorizationForZRef()
      when(mockNPSService.retrieveReconciliationReportPage(any, any, any, any)(any))
        .thenReturn(Future.successful(Left(ReportPageNotFoundErr(validPageIndex))))

      val res = controller.retrieveReconciliationReportPage(validZReference, validTaxYear, validMonthStr, validPageIndex).apply(req)

      status(res) mustBe NOT_FOUND
      contentAsJson(res) shouldBe Json.toJson(ReportPageNotFoundErr(validPageIndex))
    }

    "return 401 with an error when auth fails" in {
      val req = FakeRequest(GET, s"/monthly/$validZReference/$validTaxYear/$validMonth/results?page=$validPageIndex")

      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.failed(InvalidBearerToken()))

      val res = controller.retrieveReconciliationReportPage(validZReference, validTaxYear, validMonthStr, validPageIndex).apply(req)

      status(res) mustBe UNAUTHORIZED
      contentAsJson(res).as[ErrorResponse] shouldBe UnauthorisedErr
    }

    "return 500 with an error whens something unexpected occurs" in {
      val req = FakeRequest(GET, s"/monthly/$validZReference/$validTaxYear/$validMonth/results?page=$validPageIndex")

      authorizationForZRef()
      when(mockNPSService.retrieveReconciliationReportPage(any, any, any, any)(any)).thenReturn(Future.successful(Left(InternalServerErr())))

      val res = controller.retrieveReconciliationReportPage(validZReference, validTaxYear, validMonthStr, validPageIndex).apply(req)

      status(res) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(res) shouldBe Json.toJson(InternalServerErr())
    }
  }

}
