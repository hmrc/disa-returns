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
import org.mockito.Mockito.{verify, when}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.disareturns.controllers.ReturnsSummaryController
import uk.gov.hmrc.disareturns.models.common.{BadRequestErr, InternalServerErr, ReturnNotFoundErr}
import uk.gov.hmrc.disareturns.models.summary.ReturnSummaryResults
import uk.gov.hmrc.disareturns.models.summary.repository.MonthlyReturnsSummary
import uk.gov.hmrc.disareturns.models.summary.request.MonthlyReturnsSummaryReq
import utils.BaseUnitSpec

import scala.concurrent.Future

class ReturnsSummaryControllerSpec extends BaseUnitSpec {
  private val controller      = app.injector.instanceOf[ReturnsSummaryController]
  private val totalRecords    = 3
  private val summaryResponse = ReturnSummaryResults("url", totalRecords, 1)
  private val callbackBody    = Json.toJson(MonthlyReturnsSummaryReq(totalRecords))

  "retrieveReturnSummary" should {
    "return the current summary" in {
      authorizationForZRef()
      when(mockReturnsSummaryService.retrieveReturnSummary(any)).thenReturn(Future.successful(Right(summaryResponse)))
      val result = controller.retrieveReturnSummary(validZReference)(FakeRequest(GET, s"/monthly/$validZReference/results/summary"))
      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(summaryResponse)
    }

    "retain Z-reference validation" in {
      val result = controller.retrieveReturnSummary("invalid")(FakeRequest(GET, "/monthly/invalid/results/summary"))
      status(result) shouldBe BAD_REQUEST
    }

    "map not found and service errors" in {
      authorizationForZRef()
      when(mockReturnsSummaryService.retrieveReturnSummary(any)).thenReturn(Future.successful(Left(ReturnNotFoundErr("not found"))))
      status(controller.retrieveReturnSummary(validZReference)(FakeRequest())) shouldBe NOT_FOUND

      when(mockReturnsSummaryService.retrieveReturnSummary(any)).thenReturn(Future.successful(Left(InternalServerErr())))
      status(controller.retrieveReturnSummary(validZReference)(FakeRequest())) shouldBe INTERNAL_SERVER_ERROR

      when(mockReturnsSummaryService.retrieveReturnSummary(any)).thenReturn(Future.successful(Left(BadRequestErr("unexpected"))))
      status(controller.retrieveReturnSummary(validZReference)(FakeRequest())) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "returnsSummaryCallback" should {
    "store one periodless summary and send a notification" in {
      when(mockReturnsSummaryService.saveReturnsSummary(any)).thenReturn(Future.successful(Right(())))
      when(mockReturnsSummaryService.retrieveReturnSummary(any)).thenReturn(Future.successful(Right(summaryResponse)))
      when(mockPPNSService.sendNotification(any, any)(any)).thenReturn(Future.successful(()))

      val result = controller.returnsSummaryCallback(validZReference)(
        FakeRequest(POST, s"/callback/monthly/$validZReference").withBody(callbackBody)
      )

      status(result) shouldBe NO_CONTENT
      verify(mockReturnsSummaryService).saveReturnsSummary(
        argThat[MonthlyReturnsSummary](summary => summary.zRef == validZReference && summary.totalRecords == totalRecords)
      )
    }

    "return no content when notification data cannot be retrieved" in {
      when(mockReturnsSummaryService.saveReturnsSummary(any)).thenReturn(Future.successful(Right(())))
      when(mockReturnsSummaryService.retrieveReturnSummary(any)).thenReturn(Future.successful(Left(InternalServerErr())))
      val result = controller.returnsSummaryCallback(validZReference)(FakeRequest(POST, "/").withBody(callbackBody))
      status(result) shouldBe NO_CONTENT
    }

    "map save errors and reject invalid input" in {
      when(mockReturnsSummaryService.saveReturnsSummary(any)).thenReturn(Future.successful(Left(InternalServerErr())))
      status(controller.returnsSummaryCallback(validZReference)(FakeRequest(POST, "/").withBody(callbackBody))) shouldBe INTERNAL_SERVER_ERROR

      status(controller.returnsSummaryCallback("invalid")(FakeRequest(POST, "/").withBody(callbackBody)))                 shouldBe BAD_REQUEST
      status(controller.returnsSummaryCallback(validZReference)(FakeRequest(POST, "/").withBody(Json.obj("wrong" -> 1)))) shouldBe BAD_REQUEST
    }
  }
}
