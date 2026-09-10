/*
 * Copyright 2026 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.{verify, when}
import play.api.libs.json.{JsNull, JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.disareturns.testOnly.controllers.TestOnlyMonthlyReturnController
import utils.BaseUnitSpec

import scala.concurrent.Future

class TestOnlyMonthlyReturnControllerSpec extends BaseUnitSpec {
  private val controller = app.injector.instanceOf[TestOnlyMonthlyReturnController]

  "delete" should {
    "normalize and delete valid Z-references" in {
      when(mockReconciliationReportReadyRepository.deleteByZReferences(eqTo(Seq("Z1234", "Z5678"))))
        .thenReturn(Future.successful(()))

      val body: JsValue = Json.obj("zReferences" -> Json.arr("z1234", "Z5678"))
      val request = FakeRequest(POST, "/test-only/monthly").withBody(body)
      val result  = controller.delete()(request)

      status(result) shouldBe NO_CONTENT
      verify(mockReconciliationReportReadyRepository).deleteByZReferences(Seq("Z1234", "Z5678"))
    }

    "reject invalid and null Z-references" in {
      Seq(Json.arr("|1234"), Json.arr(JsNull)).foreach { zReferences =>
        val body: JsValue = Json.obj("zReferences" -> zReferences)
        val request = FakeRequest(POST, "/test-only/monthly").withBody(body)

        status(controller.delete()(request)) shouldBe BAD_REQUEST
      }
    }
  }
}
