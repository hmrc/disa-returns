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
import uk.gov.hmrc.disareturns.controllers.ReconciliationReportReadyCallbackController
import uk.gov.hmrc.disareturns.models.common.InternalServerErr
import uk.gov.hmrc.disareturns.models.callback.{ReconciliationReportReady, ReconciliationReportReadyCallbackRequest}
import uk.gov.hmrc.disareturns.models.ppns.ReconciliationReportReadyNotification
import utils.BaseUnitSpec

import scala.concurrent.Future

class ReconciliationReportReadyCallbackControllerSpec extends BaseUnitSpec {
  private val controller   = app.injector.instanceOf[ReconciliationReportReadyCallbackController]
  private val totalRecords = 3
  private val notification = ReconciliationReportReadyNotification("url", totalRecords, 1)
  private val callbackBody = Json.toJson(ReconciliationReportReadyCallbackRequest(totalRecords))

  "callback" should {
    "store the callback and send a notification" in {
      when(mockReconciliationReportReadyCallbackService.save(any)).thenReturn(Future.successful(Right(())))
      when(mockReconciliationReportReadyCallbackService.buildNotification(any)).thenReturn(Future.successful(Right(notification)))
      when(mockPPNSService.sendReconciliationReportReadyNotification(any, any)(any)).thenReturn(Future.successful(()))

      val result = controller.callback(validZReference)(
        FakeRequest(POST, s"/callback/monthly/$validZReference").withBody(callbackBody)
      )

      status(result) shouldBe NO_CONTENT
      verify(mockReconciliationReportReadyCallbackService).save(
        argThat[ReconciliationReportReady](reportReady => reportReady.zRef == validZReference && reportReady.totalRecords == totalRecords)
      )
    }

    "return no content when notification data cannot be retrieved" in {
      when(mockReconciliationReportReadyCallbackService.save(any)).thenReturn(Future.successful(Right(())))
      when(mockReconciliationReportReadyCallbackService.buildNotification(any)).thenReturn(Future.successful(Left(InternalServerErr())))
      val result = controller.callback(validZReference)(FakeRequest(POST, "/").withBody(callbackBody))
      status(result) shouldBe NO_CONTENT
    }

    "map save errors and reject invalid input" in {
      when(mockReconciliationReportReadyCallbackService.save(any)).thenReturn(Future.successful(Left(InternalServerErr())))
      status(controller.callback(validZReference)(FakeRequest(POST, "/").withBody(callbackBody))) shouldBe INTERNAL_SERVER_ERROR

      status(controller.callback("invalid")(FakeRequest(POST, "/").withBody(callbackBody)))                 shouldBe BAD_REQUEST
      status(controller.callback(validZReference)(FakeRequest(POST, "/").withBody(Json.obj("wrong" -> 1)))) shouldBe BAD_REQUEST
    }
  }
}
