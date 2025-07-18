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
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.disareturns.controllers.actionBuilders.ClientIdAction
import utils.BaseUnitSpec

import scala.concurrent.Future

class ClientIdActionSpec extends BaseUnitSpec {

  "ClientIdAction.refine" should {

    "allow request when X-Client-ID header is present" in {
      val action = new ClientIdAction()
      val request = FakeRequest().withHeaders("X-Client-ID" -> "client-123")

      whenReady(action.refine(request)) {
        case Right(clientIdRequest) =>
          clientIdRequest.clientId shouldBe "client-123"
          clientIdRequest.request shouldBe request

        case Left(_) =>
          fail("Expected request to be allowed but got BadRequest")
      }
    }

    "block request when X-Client-ID header is missing" in {
      val action = new ClientIdAction()
      val request = FakeRequest()

      whenReady(action.refine(request)) {
        case Left(result) =>
          val resultF = Future.successful(result)
          status(resultF) shouldBe BAD_REQUEST
          contentType(resultF) shouldBe Some("application/json")
          contentAsJson(resultF) shouldBe Json.obj("code" -> "BAD_REQUEST",
            "message" -> "Missing required header: X-Client-ID")

        case Right(_) =>
          fail("Expected BadRequest but got a successful refinement")
      }
    }
  }
}
