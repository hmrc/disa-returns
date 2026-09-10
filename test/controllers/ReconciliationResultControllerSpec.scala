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
import uk.gov.hmrc.disareturns.models.returnResults.{IssueWithMessage, ReconciliationReport, ReturnResults}
import utils.BaseUnitSpec

import scala.concurrent.Future

class ReconciliationResultControllerSpec extends BaseUnitSpec {
  private val controller = app.injector.instanceOf[ReconciliationResultController]
  private val report = ReconciliationReport(
    Seq(ReturnResults("1", "A", IssueWithMessage("code", "message"))),
    Some("encrypted-cursor")
  )

  private def stubLimits(): Unit = {
    when(mockAppConfig.returnResultsDefaultLimit).thenReturn(200)
    when(mockAppConfig.returnResultsMaxLimit).thenReturn(1000)
  }

  "retrieveReconciliationReport" should {
    "use the reporting period and default limit when pagination parameters are omitted" in {
      authorizationForZRef()
      stubLimits()
      when(mockNPSService.retrieveReconciliationReport(any, any, any, any, any)(any)).thenReturn(Future.successful(Right(report)))

      val result = controller.retrieveReconciliationReport(validZReference, None, None)(FakeRequest(GET, s"/monthly/$validZReference/results"))

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(report)
      verify(mockReportingPeriodSource).get(eqTo(validZReference))(any)
      verify(mockNPSService)
        .retrieveReconciliationReport(eqTo(validZReference), eqTo(validTaxYear), eqTo(validMonth), eqTo(None), eqTo(200))(any)
    }

    "pass an optional cursor and valid limit to the service" in {
      authorizationForZRef()
      stubLimits()
      when(mockNPSService.retrieveReconciliationReport(any, any, any, any, any)(any)).thenReturn(Future.successful(Right(report)))

      val result = controller.retrieveReconciliationReport(validZReference, Some("opaque-cursor"), Some("1000"))(
        FakeRequest(GET, s"/monthly/$validZReference/results?cursor=opaque-cursor&limit=1000")
      )

      status(result) shouldBe OK
      verify(mockNPSService)
        .retrieveReconciliationReport(eqTo(validZReference), eqTo(validTaxYear), eqTo(validMonth), eqTo(Some("opaque-cursor")), eqTo(1000))(any)
    }

    "aggregate Z-reference and limit validation errors" in {
      stubLimits()
      val result = controller.retrieveReconciliationReport("invalid", None, Some("1001"))(
        FakeRequest(GET, "/monthly/invalid/results?limit=1001")
      )

      status(result) shouldBe BAD_REQUEST
      contentAsJson(result).as[ErrorResponse] shouldBe
        MultipleErrorResponse(code = "BAD_REQUEST", errors = Seq(InvalidZReference, InvalidLimitErr))
    }

    "map downstream cursor errors" in {
      authorizationForZRef()
      stubLimits()
      when(mockNPSService.retrieveReconciliationReport(any, any, any, any, any)(any))
        .thenReturn(Future.successful(Left(InvalidCursorErr)))

      val result = controller.retrieveReconciliationReport(validZReference, Some("bad"), None)(FakeRequest(GET, "/"))
      status(result) shouldBe BAD_REQUEST
    }
  }
}
