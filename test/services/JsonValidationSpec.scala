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
import play.api.libs.json._
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaType
import uk.gov.hmrc.disareturns.utils.JsonValidation

import java.time.LocalDate

class JsonValidationSpec extends AnyWordSpec with Matchers {

  "JsonValidation.firstLevelValidatorExtractNinoAndAccount" should {

    "return Right when both nino and accountNumber are valid non-empty strings" in {
      val json   = Json.obj("nino" -> "AB123456C", "accountNumber" -> "ACC123")
      val result = JsonValidation.firstLevelValidatorExtractNinoAndAccount(json)
      result shouldBe Right(("AB123456C", "ACC123"))
    }

    "return Left(NinoOrAccountNumMissingErr) when nino field is missing" in {
      val json   = Json.obj("accountNumber" -> "ACC123")
      val result = JsonValidation.firstLevelValidatorExtractNinoAndAccount(json)
      result shouldBe Left(NinoOrAccountNumMissingErr)
    }

    "return Left(NinoOrAccountNumMissingErr) when accountNumber field is missing" in {
      val json   = Json.obj("nino" -> "AB123456C")
      val result = JsonValidation.firstLevelValidatorExtractNinoAndAccount(json)
      result shouldBe Left(NinoOrAccountNumMissingErr)
    }

    "return Left(NinoOrAccountNumInvalidErr) when one of the fields is empty string" in {
      val json   = Json.obj("nino" -> "", "accountNumber" -> "ACC123")
      val result = JsonValidation.firstLevelValidatorExtractNinoAndAccount(json)
      result shouldBe Left(NinoOrAccountNumInvalidErr)
    }

    "return Left(NinoOrAccountNumInvalidErr) when both fields are empty" in {
      val json   = Json.obj("nino" -> "", "accountNumber" -> "")
      val result = JsonValidation.firstLevelValidatorExtractNinoAndAccount(json)
      result shouldBe Left(NinoOrAccountNumInvalidErr)
    }
  }

  "JsonValidation.findDuplicateFields" should {

    "return JsSuccess when there are no duplicate fields" in {
      val jsonLine = """{"field1":"value1","field2":"value2"}"""
      val result   = JsonValidation.findDuplicateFields(jsonLine)
      result shouldBe a[JsSuccess[_]]
    }

    "return JsError when a duplicate field is found" in {
      val jsonLine = """{"field1":"value1","field1":"value2"}"""
      val result   = JsonValidation.findDuplicateFields(jsonLine)
      result                                                   shouldBe a[JsError]
      result.asInstanceOf[JsError].errors.head._2.head.message shouldBe "error.duplicateField"
    }
  }

  "JsonValidation.twoDecimalNum" should {

    "return JsSuccess for numbers with exactly two decimal places" in {
      val json = JsNumber(BigDecimal("12.34"))
      JsonValidation.twoDecimalNum.reads(json) shouldBe JsSuccess(BigDecimal("12.34"))
    }

    "return JsError for numbers with more than two decimal places" in {
      val json = JsNumber(BigDecimal("12.345"))
      JsonValidation.twoDecimalNum.reads(json).isError shouldBe true
    }

    "return JsError for non-numeric JSON values" in {
      val json = JsString("12.34")
      JsonValidation.twoDecimalNum.reads(json).isError shouldBe true
    }
  }

  "JsonValidation.twoDecimalNumNonNegative" should {

    "return JsSuccess for valid non-negative numbers with two decimals" in {
      val json = JsNumber(BigDecimal("99.99"))
      JsonValidation.twoDecimalNumNonNegative.reads(json) shouldBe JsSuccess(BigDecimal("99.99"))
    }

    "return JsError for negative numbers" in {
      val json = JsNumber(BigDecimal("-1.00"))
      JsonValidation.twoDecimalNumNonNegative.reads(json).isError shouldBe true
    }

    "return JsError for numbers with more than two decimal places" in {
      val json = JsNumber(BigDecimal("1.234"))
      JsonValidation.twoDecimalNumNonNegative.reads(json).isError shouldBe true
    }

    "return JsError for non-numeric values" in {
      val json = JsString("notANumber")
      JsonValidation.twoDecimalNumNonNegative.reads(json).isError shouldBe true
    }
  }

  "JsonValidation.stringPattern" should {

    "return JsSuccess when the string matches the provided regex" in {
      val regex  = "^[A-Z]{3}$".r
      val reader = JsonValidation.stringPattern(regex, "INVALID_CODE")
      reader.reads(JsString("ABC")) shouldBe JsSuccess("ABC")
    }

    "return JsError when the string does not match the regex" in {
      val regex  = "^[A-Z]{3}$".r
      val reader = JsonValidation.stringPattern(regex, "INVALID_CODE")
      val result = reader.reads(JsString("ABCD"))
      result.isError shouldBe true
    }

    "return JsError when value is not a JsString" in {
      val regex  = "^[A-Z]{3}$".r
      val reader = JsonValidation.stringPattern(regex, "INVALID_CODE")
      val result = reader.reads(JsNumber(123))
      result.isError shouldBe true
    }
  }

  "JsonValidation.strictLocalDateReads" should {

    "successfully parse a valid ISO date string" in {
      val json = JsString("2024-12-31")
      JsonValidation.strictLocalDateReads.reads(json) shouldBe JsSuccess(LocalDate.of(2024, 12, 31))
    }

    "return JsError for invalid date format" in {
      val json = JsString("31-12-2024")
      JsonValidation.strictLocalDateReads.reads(json).isError shouldBe true
    }

    "return JsError for non-string values" in {
      val json = JsNumber(12345)
      JsonValidation.strictLocalDateReads.reads(json).isError shouldBe true
    }
  }

  "JsonValidation.nonEmptyStringReads" should {

    "return JsSuccess for a non-empty string" in {
      JsonValidation.nonEmptyStringReads.reads(JsString("abc")) shouldBe JsSuccess("abc")
    }

    "return JsError for an empty string" in {
      JsonValidation.nonEmptyStringReads.reads(JsString("")) shouldBe a[JsError]
    }

    "return JsError for whitespace-only string" in {
      JsonValidation.nonEmptyStringReads.reads(JsString("   ")) shouldBe a[JsError]
    }

    "return JsError for non-string JSON" in {
      JsonValidation.nonEmptyStringReads.reads(JsNumber(123)) shouldBe a[JsError]
    }
  }

  "JsonValidation.nino" should {

    "return JsSuccess for a valid NINO" in {
      JsonValidation.ninoReads.reads(JsString("AB123456C")) shouldBe JsSuccess("AB123456C")
    }

    "return JsError for an invalid NINO" in {
      val result = JsonValidation.ninoReads.reads(JsString("123456"))
      result.isError                                           shouldBe true
      result.asInstanceOf[JsError].errors.head._2.head.message shouldBe "INVALID_NINO"
    }
  }

  "JsonValidation.accountNumber" should {

    "return JsSuccess for a valid account number" in {
      JsonValidation.accountNumberReads.reads(JsString("ACC-1234/56")) shouldBe JsSuccess("ACC-1234/56")
    }

    "return JsError for an invalid account number" in {
      val result = JsonValidation.accountNumberReads.reads(JsString("!@#$%^"))
      result.isError                                           shouldBe true
      result.asInstanceOf[JsError].errors.head._2.head.message shouldBe "INVALID_ACCOUNT_NUMBER"
    }
  }

  "JsonValidation.lifetimeIsaTypeReads" should {

    "return JsSuccess when isaType is LIFETIME" in {
      val json = JsString("LIFETIME")
      JsonValidation.lifetimeIsaTypeReads.reads(json) shouldBe JsSuccess(IsaType.LIFETIME)
    }

    "return JsError when isaType is a different valid IsaType" in {
      val json   = JsString("CASH")
      val result = JsonValidation.lifetimeIsaTypeReads.reads(json)
      result.isError shouldBe true
      result.toString  should include("error.expected.lifetime.isatype")
    }

    "return JsError when isaType is an unrecognised string" in {
      val json   = JsString("UNKNOWN_TYPE")
      val result = JsonValidation.lifetimeIsaTypeReads.reads(json)
      result.isError shouldBe true
      result.toString  should include("error.expected.lifetime.isatype")
    }

    "return JsError when isaType is not a JsString" in {
      val json   = JsNumber(123)
      val result = JsonValidation.lifetimeIsaTypeReads.reads(json)
      result.isError shouldBe true
      result.toString  should include("error.expected.lifetime.isatype")
    }
  }

  "JsonValidation.standardIsaTypeReads" should {

    "return JsSuccess for non-LIFETIME ISA types" in {
      val validTypes = Seq("CASH", "STOCKS_AND_SHARES", "INNOVATIVE_FINANCE")
      validTypes.foreach { t =>
        JsonValidation.standardIsaTypeReads.reads(JsString(t)) shouldBe JsSuccess(IsaType.withName(t))
      }
    }

    "return JsError when isaType is LIFETIME" in {
      val json   = JsString("LIFETIME")
      val result = JsonValidation.standardIsaTypeReads.reads(json)
      result.isError shouldBe true
      result.toString  should include("error.expected.standard.isatype")
    }

    "return JsError for unrecognised isaType strings" in {
      val json   = JsString("UNKNOWN_TYPE")
      val result = JsonValidation.standardIsaTypeReads.reads(json)
      result.isError shouldBe true
      result.toString  should include("error.expected.standard.isatype")
    }

    "return JsError for non-string values" in {
      val json   = JsNumber(123)
      val result = JsonValidation.standardIsaTypeReads.reads(json)
      result.isError shouldBe true
      result.toString  should include("error.expected.standard.isatype")
    }
  }
}
