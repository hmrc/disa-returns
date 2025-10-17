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
import uk.gov.hmrc.disareturns.models.common.Month
import uk.gov.hmrc.disareturns.models.helpers.ValidationHelper

class ValidationHelperSpec extends AnyWordSpec with Matchers {

  "ValidationHelper.validateParams" should {

    "return Right when all parameters are valid" in {
      val result = ValidationHelper.validateParams("Z1234", "2023-24", "FEB")
      result shouldBe Right("Z1234", "2023-24", Month.FEB)
    }

    "return error when ISA Manager Reference Number is invalid" in {
      val result = ValidationHelper.validateParams("Invalid", "2023-24", "JAN")
      result                                                                                                       shouldBe a[Left[_, _]]
      result.left.toOption.get.errors.exists(_.message.contains("ISA Manager Reference Number format is invalid")) shouldBe true
    }

    "return error when tax year is invalid" in {
      val result = ValidationHelper.validateParams("Z1234", "20-24", "MAY")
      result                                                                                       shouldBe a[Left[_, _]]
      result.left.toOption.get.errors.exists(_.message.contains("Invalid parameter for tax year")) shouldBe true
    }

    "return error when month is invalid" in {
      val result = ValidationHelper.validateParams("Z1234", "2023-24", "13")
      result                                                                                    shouldBe a[Left[_, _]]
      result.left.toOption.get.errors.exists(_.message.contains("Invalid parameter for month")) shouldBe true
    }

    "return multiple errors when all parameters are invalid" in {
      val result = ValidationHelper.validateParams("1234", "20-24", "13")
      result                                       shouldBe a[Left[_, _]]
      result.left.toOption.get.errors.size         shouldBe 3
      result.left.toOption.get.errors.map(_.message) should contain allOf (
        "ISA Manager Reference Number format is invalid",
        "Invalid parameter for tax year",
        "Invalid parameter for month"
      )
    }
  }
}
