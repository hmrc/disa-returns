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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import uk.gov.hmrc.disareturns.models.common.Month

class MonthSpec extends AnyWordSpec with Matchers {

  "Month enumeration" should {

    "serialize all months to JSON" in {
      Month.values.foreach { month =>
        Json.toJson(month) shouldBe JsString(month.toString)
      }
    }

    "deserialize valid JSON strings to Month enum" in {
      Month.values.foreach { month =>
        JsString(month.toString).validate[Month.Value] shouldBe JsSuccess(month)
      }
    }

    "fail to deserialize invalid month strings" in {
      val invalidJson = JsString("INVALID_MONTH")
      val result      = invalidJson.validate[Month.Value]

      result.isError                                          shouldBe true
      result.asEither.left.get.head._2.head.message.toLowerCase should include("error.expected.validenumvalue")
    }

    "fail to deserialize non-string JSON values" in {
      val nonStringJson = JsNumber(123)
      val result        = nonStringJson.validate[Month.Value]

      result.isError                                          shouldBe true
      result.asEither.left.get.head._2.head.message.toLowerCase should include("error.expected.enumstring")
    }
  }
}
