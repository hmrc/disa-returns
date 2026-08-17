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

package models.helpers

import uk.gov.hmrc.disareturns.models.common.*
import uk.gov.hmrc.disareturns.utils.ValidationHelper
import utils.BaseUnitSpec

class ValidationHelperSpec extends BaseUnitSpec {

  "ValidationHelper.validateParams" should {
    "normalise a valid lowercase Z-reference" in {
      ValidationHelper.validateParams(validZReference.toLowerCase) shouldBe Right((validZReference, None))
    }

    "parse a valid page" in {
      ValidationHelper.validateParams(validZReference, Some("1")) shouldBe Right((validZReference, Some(1)))
    }

    "reject an invalid Z-reference" in {
      ValidationHelper.validateParams("Invalid") shouldBe Left(InvalidZReference)
    }

    "reject an invalid page" in {
      ValidationHelper.validateParams(validZReference, Some("-1")) shouldBe Left(InvalidPageErr)
    }

    "aggregate invalid Z-reference and page errors" in {
      ValidationHelper.validateParams("1234", Some("-1")) shouldBe
        Left(MultipleErrorResponse(code = "BAD_REQUEST", errors = Seq(InvalidZReference, InvalidPageErr)))
    }
  }
}
