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

package parsers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{Materializer, SystemMaterializer}
import org.apache.pekko.util.ByteString
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test._
import uk.gov.hmrc.disareturns.controllers.parsers.StrictOptionalJsonBodyParser
import uk.gov.hmrc.disareturns.models.common.DuplicateNilReturnField

import scala.concurrent.{ExecutionContext, Future}

class StrictOptionalJsonBodyParserSpec extends AnyWordSpec with Matchers with ScalaFutures {

  implicit val system:       ActorSystem      = ActorSystem("StrictOptionalJsonBodyParserSpec")
  implicit val materializer: Materializer     = SystemMaterializer(system).materializer
  implicit val ec:           ExecutionContext = system.dispatcher

  private val parser = new StrictOptionalJsonBodyParser()

  "StrictOptionalJsonBodyParser" should {

    "return None when request body is empty" in {
      val request = FakeRequest("POST", "/").withHeaders("Content-Type" -> "application/json")
      val acc     = parser.apply(request)

      val result = await(acc.run())
      result shouldBe Right(None)
    }

    "parse valid JSON into Some(JsValue)" in {
      val rawJson = """{ "nilReturn": true }"""
      val bytes   = ByteString(rawJson)
      val requestHeader = FakeRequest("POST", "/")
        .withHeaders("Content-Type" -> "application/json")

      val acc    = parser(requestHeader)
      val result = await(acc.run(bytes))

      result match {
        case Right(Some(js)) =>
          (js \ "nilReturn").as[Boolean] shouldBe true
        case other =>
          fail(s"Expected Right(Some(JsValue)) but got $other")
      }
    }

    "return BadRequest when JSON has duplicate fields" in {
      val rawJson = """{ "nilReturn": true, "nilReturn": false }"""
      val bytes   = ByteString(rawJson)
      val requestHeader = FakeRequest("POST", "/")
        .withHeaders("Content-Type" -> "application/json")

      val acc    = parser.apply(requestHeader)
      val result = await(acc.run(bytes))

      result.isLeft shouldBe true
      val badRequestResult = result.left.get
      status(Future.successful(badRequestResult))        shouldBe BAD_REQUEST
      contentAsJson(Future.successful(badRequestResult)) shouldBe Json.toJson(DuplicateNilReturnField)
    }

    "parse JSON with extra fields (no duplicates) correctly" in {
      val rawJson = """{ "nilReturn": true, "extraField": "value" }"""
      val bytes   = ByteString(rawJson)
      val requestHeader = FakeRequest("POST", "/")
        .withHeaders("Content-Type" -> "application/json")
      val acc    = parser.apply(requestHeader)
      val result = await(acc.run(bytes))

      result match {
        case Right(Some(js)) =>
          (js \ "nilReturn").as[Boolean] shouldBe true
          (js \ "extraField").as[String] shouldBe "value"
        case other =>
          fail(s"Expected Right(Some(JsValue)) but got $other")
      }
    }

    "return None when Content-Type is not JSON and body is empty" in {
      val request = FakeRequest("POST", "/")
      val acc     = parser.apply(request)
      val result  = await(acc.run())
      result shouldBe Right(None)
    }
  }
}
