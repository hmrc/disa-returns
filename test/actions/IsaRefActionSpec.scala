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

package actions

import play.api.http.Status.BAD_REQUEST
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, contentType, status}
import uk.gov.hmrc.disareturns.controllers.actionBuilders.IsaRefAction
import utils.BaseUnitSpec

import scala.concurrent.Future

class IsaRefActionSpec extends BaseUnitSpec {

  "IsaRefAction.isValidIsaRef" should {
    "consider valid ISA references as valid" in {
      val validRefs = Seq("Z1234", "Z123456")

      validRefs.foreach { ref =>
        val action = new IsaRefAction(ref)
        action.isValidIsaRef shouldBe true
      }
    }

    "consider invalid ISA references as invalid" in {
      val invalidRefs = Seq("Z123", "A1234", "Z12A456", "Z12345", "123456", "Z")

      invalidRefs.foreach { ref =>
        val action = new IsaRefAction(ref)
        action.isValidIsaRef shouldBe false
      }
    }
  }
  "IsaRefAction.filter" should {
    "return None for valid ISA reference (allow request to proceed)" in {
      val action = new IsaRefAction("Z123456")
      val request = FakeRequest()

      val resultF: Future[Option[Result]] = action.filter(request)

      whenReady(resultF) { result =>
        result shouldBe None
      }
    }

    "return BadRequest for invalid ISA reference" in {
      val action = new IsaRefAction("A123")
      val request = FakeRequest()

      whenReady(action.filter(request)) {
        case Some(result) =>
          val resultF = Future.successful(result)
          status(resultF) shouldBe BAD_REQUEST
          contentType(resultF) shouldBe Some("application/json")
          contentAsJson(resultF) shouldBe Json.obj("code" -> "BAD_REQUEST",
            "message" -> "ISA Manager Reference Number format is invalid")

        case None =>
          fail("Expected BadRequest but got None")
      }
    }
  }
}