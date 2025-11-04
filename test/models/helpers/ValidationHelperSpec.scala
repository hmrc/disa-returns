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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.disareturns.models.common.Month._
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.helpers.ValidationHelper

class ValidationHelperSpec extends AnyWordSpec with Matchers {

  "ValidationHelper.validateParams" should {

    "return Right for valid isaManagerNumber(lowercase), taxYear and month" in {
      val result = ValidationHelper.validateParams("z1234", "2023-24", "FEB")
      result shouldBe Right("Z1234", "2023-24", Month.FEB)
    }

    "return Right for valid isaManagerNumber(uppercase), taxYear and month" in {
      val result = ValidationHelper.validateParams("Z1234", "2023-24", "FEB")
      result shouldBe Right(("Z1234", "2023-24", FEB))
    }

    "return InvalidIsaManagerRef when ISA Manager Reference Number is invalid" in {
      val result = ValidationHelper.validateParams("Invalid", "2023-24", "JAN")
      result shouldBe Left(InvalidIsaManagerRef)
    }

    "return InvalidTaxYear when tax year is invalid" in {
      val result = ValidationHelper.validateParams("Z1234", "20-24", "MAY")
      result shouldBe Left(InvalidTaxYear)
    }

    "return InvalidMonth when month is invalid" in {
      val result = ValidationHelper.validateParams("Z1234", "2023-24", "13")
      result shouldBe Left(InvalidMonth)
    }

    "return MultipleErrorResponse when all parameters are invalid" in {
      val result = ValidationHelper.validateParams("1234", "20-24", "13")
      result.left.get shouldBe
        MultipleErrorResponse(code = "BAD_REQUEST", errors = Seq(InvalidIsaManagerRef, InvalidTaxYear, InvalidMonth))
    }

    "return MultipleErrorResponse when two parameters are invalid" in {
      val result = ValidationHelper.validateParams("Invalid", "20-24", "MAY")
      result.left.get shouldBe MultipleErrorResponse(code = "BAD_REQUEST", errors = Seq(InvalidIsaManagerRef, InvalidTaxYear))
    }
  }
}
