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

package utils

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.utils.HttpHelper

import scala.concurrent.Future

class HttpHelperSpec extends AnyWordSpec with Matchers {

  "HttpHelper.toHttpError" should {

    "return InternalServerError (500) for InternalServerErr" in {
      val result: Result = HttpHelper.toHttpError(InternalServerErr())
      result.header.status shouldBe INTERNAL_SERVER_ERROR
      val json = contentAsJson(Future.successful(result))
      (json \ "code").as[String]    shouldBe "INTERNAL_SERVER_ERROR"
      (json \ "message").as[String] shouldBe "There has been an issue processing your request"
    }

    "return Unauthorized (401) for Unauthorised" in {
      val result: Result = HttpHelper.toHttpError(UnauthorisedErr)
      result.header.status shouldBe UNAUTHORIZED
      val json = contentAsJson(Future.successful(result))
      (json \ "code").as[String]    shouldBe "UNAUTHORISED"
      (json \ "message").as[String] shouldBe "Unauthorised"
    }

    "return Unauthorized (403) for Forbidden" in {
      val result: Result = HttpHelper.toHttpError(ObligationClosed)
      result.header.status shouldBe FORBIDDEN
      val json = contentAsJson(Future.successful(result))
      (json \ "code").as[String]    shouldBe "OBLIGATION_CLOSED"
      (json \ "message").as[String] shouldBe "Obligation closed"
    }

    "return NotFound (404) for ReturnNotFoundErr" in {
      val err    = ReturnNotFoundErr("")
      val result = HttpHelper.toHttpError(err)

      result.header.status                     shouldBe NOT_FOUND
      contentAsJson(Future.successful(result)) shouldBe Json.toJson(err)
    }

    "return NotFound (404) for ReportPageNotFoundErr" in {
      val err    = ReportPageNotFoundErr(1)
      val result = HttpHelper.toHttpError(err)

      result.header.status                     shouldBe NOT_FOUND
      contentAsJson(Future.successful(result)) shouldBe Json.toJson(err)
    }

    "return BadRequest (400) for InvalidPageErr" in {
      val err    = InvalidPageErr
      val result = HttpHelper.toHttpError(err)

      result.header.status                     shouldBe BAD_REQUEST
      contentAsJson(Future.successful(result)) shouldBe Json.toJson(err)
    }
  }
}
