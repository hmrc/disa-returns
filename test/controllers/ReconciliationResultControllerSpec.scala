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

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{verify, when}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.disareturns.controllers.ReconciliationResultController
import uk.gov.hmrc.disareturns.models.common.*
import uk.gov.hmrc.disareturns.models.returnResults.{IssueWithMessage, ReconciliationReportPage, ReturnResults}
import utils.BaseUnitSpec

import scala.concurrent.Future

class ReconciliationResultControllerSpec extends BaseUnitSpec {
  private val controller = app.injector.instanceOf[ReconciliationResultController]
  private val report     = ReconciliationReportPage(0, 1, 1, 1, Seq(ReturnResults("1", "A", IssueWithMessage("code", "message"))))

  "retrieveReconciliationReportPage" should {
    "use the internally generated reporting period" in {
      authorizationForZRef()
      when(mockNPSService.retrieveReconciliationReportPage(any, any, any, any)(any)).thenReturn(Future.successful(Right(report)))

      val result = controller.retrieveReconciliationReportPage(validZReference, "0")(FakeRequest(GET, s"/monthly/$validZReference/results?page=0"))

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(report)
      verify(mockNPSService).retrieveReconciliationReportPage(eqTo(validZReference), eqTo(validTaxYear), eqTo(validMonth), eqTo(0))(any)
    }

    "retain Z-reference and page validation" in {
      val result = controller.retrieveReconciliationReportPage("invalid", "-1")(FakeRequest(GET, "/monthly/invalid/results?page=-1"))
      status(result) shouldBe BAD_REQUEST
      contentAsJson(result).as[ErrorResponse] shouldBe
        MultipleErrorResponse(code = "BAD_REQUEST", errors = Seq(InvalidZReference, InvalidPageErr))
    }

    "map downstream errors" in {
      authorizationForZRef()
      when(mockNPSService.retrieveReconciliationReportPage(any, any, any, any)(any))
        .thenReturn(Future.successful(Left(ReportPageNotFoundErr(0))))
      val result = controller.retrieveReconciliationReportPage(validZReference, "0")(FakeRequest(GET, s"/monthly/$validZReference/results?page=0"))
      status(result) shouldBe NOT_FOUND
    }
  }
}
