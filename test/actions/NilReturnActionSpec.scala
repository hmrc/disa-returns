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

import play.api.libs.json.Json
import play.api.mvc.{AnyContent, AnyContentAsJson}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.disareturns.controllers.actionBuilders.NilReturnAction
import uk.gov.hmrc.disareturns.models.common.{DeclarationRequest, MalformedJsonFailureErr}
import uk.gov.hmrc.disareturns.models.declaration.ReportingNilReturn
import utils.BaseUnitSpec

import scala.concurrent.Future

class NilReturnActionSpec extends BaseUnitSpec {

  private val testClientId = "client-123"
  private val action       = new NilReturnAction()

  private def buildRequest(body: AnyContent = AnyContent("")): DeclarationRequest[AnyContent] =
    DeclarationRequest(FakeRequest().withBody(body), clientId = testClientId)

  "NilReturnAction.refine" should {

    "overwrite nilReturnReported to true when JSON reports nil return" in {
      val json    = Json.toJson(ReportingNilReturn(nilReturn = true))
      val request = buildRequest(AnyContentAsJson(json))

      whenReady(action.refine(request)) { result =>
        result.isRight                        shouldBe true
        result.toOption.get.nilReturnReported shouldBe true
      }
    }

    "overwrite nilReturnReported to false when JSON reports no nil return" in {
      val json    = Json.toJson(ReportingNilReturn(nilReturn = false))
      val request = buildRequest(AnyContentAsJson(json)).copy(nilReturnReported = true) // ensure overwrite

      whenReady(action.refine(request)) { result =>
        result.isRight                        shouldBe true
        result.toOption.get.nilReturnReported shouldBe false
      }
    }

    "return BadRequest when JSON is malformed" in {
      val invalidJson = Json.obj("invalid" -> "field")
      val request     = buildRequest(AnyContentAsJson(invalidJson))

      whenReady(action.refine(request)) { result =>
        result.isLeft shouldBe true
        val response = result.left.get
        status(Future.successful(response))        shouldBe BAD_REQUEST
        contentType(Future.successful(response))   shouldBe Some("application/json")
        contentAsJson(Future.successful(response)) shouldBe Json.toJson(MalformedJsonFailureErr)
      }
    }

    "keep nilReturnReported as false when body is not JSON" in {
      val request = buildRequest()

      whenReady(action.refine(request)) { result =>
        result.isRight                        shouldBe true
        result.toOption.get.nilReturnReported shouldBe false
      }
    }
  }
}
