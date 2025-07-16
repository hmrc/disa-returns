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
import uk.gov.hmrc.disareturns.utils.RequestHelper

class RequestHelperSpec extends AnyWordSpec with Matchers {

  object TestHelper extends RequestHelper

  "makeQueryString" should {

    "return an empty string when no parameters are provided" in {
      val result = TestHelper.makeQueryString(Seq.empty)
      result shouldBe ""
    }

    "return a query string for a single parameter" in {
      val result = TestHelper.makeQueryString(Seq("foo" -> "bar"))
      result shouldBe "?foo=bar"
    }

    "return a query string for multiple parameters" in {
      val result = TestHelper.makeQueryString(Seq("clientId" -> "test-client-Id", "boxName" -> "box1"))
      result shouldBe "?clientId=test-client-Id&boxName=box1"
    }

    "URL encode special characters in parameter values" in {
      val result = TestHelper.makeQueryString(Seq("q" -> "hello world", "x" -> "10&20"))
      result shouldBe "?q=hello+world&x=10%2620"
    }

  }
}
