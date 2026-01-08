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

import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.helpers.ValidationHelper
import utils.BaseUnitSpec

class ValidationHelperSpec extends BaseUnitSpec {

  "ValidationHelper.validateParams" should {

    "return Right for valid zReference(lowercase), taxYear and month" in {
      val result = ValidationHelper.validateParams(validZReference.toLowerCase, validTaxYear, validMonthStr)
      result shouldBe Right(validZReference, validTaxYear, validMonth, None)
    }

    "return Right for valid zReference(uppercase), taxYear and month" in {
      val result = ValidationHelper.validateParams(validZReference, validTaxYear, validMonthStr)
      result shouldBe Right((validZReference, validTaxYear, validMonth, None))
    }

    "return Right for valid zReference(uppercase), taxYear, month and page" in {
      val result = ValidationHelper.validateParams(validZReference, validTaxYear, validMonthStr, Some("1"))
      result shouldBe Right((validZReference, validTaxYear, validMonth, Some(1)))
    }

    "return InvalidZReference when ISA Manager Reference Number is invalid" in {
      val result = ValidationHelper.validateParams("Invalid", validTaxYear, validMonthStr)
      result shouldBe Left(InvalidZReference)
    }

    "return InvalidTaxYear when tax year is invalid" in {
      val result = ValidationHelper.validateParams(validZReference, "20-24", validMonthStr)
      result shouldBe Left(InvalidTaxYear)
    }

    "return InvalidMonth when month is invalid" in {
      val result = ValidationHelper.validateParams(validZReference, validTaxYear, "13")
      result shouldBe Left(InvalidMonth)
    }

    "return InvalidPageErr when page is invalid" in {
      val result = ValidationHelper.validateParams(validZReference, validTaxYear, validMonthStr, Some("-1"))
      result shouldBe Left(InvalidPageErr)
    }

    "return MultipleErrorResponse when all parameters are invalid" in {
      val result = ValidationHelper.validateParams("1234", "20-24", "13", Some("-12"))
      result.left.get shouldBe
        MultipleErrorResponse(code = "BAD_REQUEST", errors = Seq(InvalidZReference, InvalidTaxYear, InvalidMonth, InvalidPageErr))
    }

    "return MultipleErrorResponse when two parameters are invalid" in {
      val result = ValidationHelper.validateParams("Invalid", "20-24", "MAY")
      result.left.get shouldBe MultipleErrorResponse(code = "BAD_REQUEST", errors = Seq(InvalidZReference, InvalidTaxYear))
    }
  }
}
