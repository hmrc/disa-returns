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
import uk.gov.hmrc.disareturns.utils.{LooseZReferenceValidator, StrictZReferenceValidator, ValidationHelper}
import utils.BaseUnitSpec

class ValidationHelperSpec extends BaseUnitSpec {
  private val strictValidationHelper = new ValidationHelper(new StrictZReferenceValidator)
  private val looseValidationHelper  = new ValidationHelper(new LooseZReferenceValidator)

  "ValidationHelper.validateParams" should {
    "normalise a valid lowercase Z-reference" in {
      strictValidationHelper.validateParams(validZReference.toLowerCase) shouldBe Right((validZReference, None))
    }

    "reject invalid and null Z-references" in {
      Seq("Invalid", "|1234", null).foreach { zReference =>
        strictValidationHelper.validateParams(zReference) shouldBe Left(InvalidZReference)
      }
    }

    "accept exactly four digits with strict validation" in {
      strictValidationHelper.validateParams("Z1234") shouldBe Right(("Z1234", None))
      Seq("Z123", "Z12345").foreach { zReference =>
        strictValidationHelper.validateParams(zReference) shouldBe Left(InvalidZReference)
      }
    }

    "accept between four and eight digits with loose validation" in {
      Seq("Z1234", "Z12345", "Z12345678").foreach { zReference =>
        looseValidationHelper.validateParams(zReference) shouldBe Right((zReference, None))
      }
      Seq("Z123", "Z123456789").foreach { zReference =>
        looseValidationHelper.validateParams(zReference) shouldBe Left(InvalidZReference)
      }
    }
  }

  "ValidationHelper.validatePaginationParams" should {
    "use the default limit when one is not supplied" in {
      strictValidationHelper.validatePaginationParams(validZReference, None, 200, 1000) shouldBe Right((validZReference, 200))
    }

    "accept a positive limit up to the maximum" in {
      strictValidationHelper.validatePaginationParams(validZReference, Some("1000"), 200, 1000) shouldBe Right((validZReference, 1000))
    }

    "reject non-numeric, zero, negative, and over-maximum limits" in {
      Seq("nope", "0", "-1", "1001").foreach { limit =>
        strictValidationHelper.validatePaginationParams(validZReference, Some(limit), 200, 1000) shouldBe Left(InvalidLimitErr)
      }
    }

    "aggregate invalid Z-reference and limit errors" in {
      strictValidationHelper.validatePaginationParams("1234", Some("-1"), 200, 1000) shouldBe
        Left(MultipleErrorResponse(code = "BAD_REQUEST", errors = Seq(InvalidZReference, InvalidLimitErr)))
    }
  }
}
