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

package models

import play.api.libs.json._
import utils.BaseUnitSpec
import java.time.LocalDate
import uk.gov.hmrc.disareturns.utils.JsonImplicits._

class JsonImplicitsSpec extends BaseUnitSpec {

  "strictLocalDateReads" should {
    "successfully parse a valid ISO date string" in {
      val json = JsString("2025-08-13")
      json.validate[LocalDate] shouldBe JsSuccess(LocalDate.of(2025, 8, 13))
    }

    "fail with error.expected.date.iso when string is not a valid date" in {
      val json = JsString("invalid-date")
      json.validate[LocalDate] shouldBe JsError("error.expected.date.iso")
    }

    "fail with error.expected.jsstring when value is not a JsString" in {
      val json = JsNumber(123)
      json.validate[LocalDate] shouldBe JsError("error.expected.jsstring")
    }
  }

  "nonEmptyStringReads" should {
    "successfully parse a non-empty string" in {
      val json = JsString("hello world")
      json.validate[String] shouldBe JsSuccess("hello world")
    }

    "fail with error.expected.jsstring when string is empty" in {
      val json = JsString("")
      json.validate[String] shouldBe JsError("error.expected.jsstring")
    }

    "fail with error.expected.jsstring when string is only whitespace" in {
      val json = JsString("   ")
      json.validate[String] shouldBe JsError("error.expected.jsstring")
    }

    "fail with error.expected.jsstring when value is not a JsString" in {
      val json = JsNumber(123)
      json.validate[String] shouldBe JsError("error.expected.jsstring")
    }
  }
}
