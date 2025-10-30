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

package uk.gov.hmrc.disareturns.models.isaAccounts

import play.api.libs.json._
import uk.gov.hmrc.disareturns.models.common.{NinoOrAccountNumInvalidErr, NinoOrAccountNumMissingErr}
import uk.gov.hmrc.disareturns.utils.JsonValidation._
import utils.BaseUnitSpec

import java.time.LocalDate

class IsaAccountSpec extends BaseUnitSpec {

  "IsaAccount Reads" should {

    "deserialize LifetimeIsaClosure correctly" in {
      val result = lifetimeIsaClosureJson.validate[IsaAccount]
      result.isSuccess shouldBe true
      result.get       shouldBe lifetimeIsaClosureJson.as[IsaAccount]
    }

    "deserialize LifetimeIsaSubscription correctly" in {
      val result = lifetimeIsaSubscriptionJson.validate[IsaAccount]
      result.isSuccess shouldBe true
      result.get       shouldBe lifetimeIsaSubscriptionJson.as[IsaAccount]
    }

    "deserialize StandardIsaSubscription correctly" in {
      val result = standardIsaSubscriptionJson.validate[IsaAccount]
      result.isSuccess shouldBe true
      result.get       shouldBe standardIsaSubscriptionJson.as[IsaAccount]
    }

    "deserialize StandardIsaClosure correctly" in {
      val result = standardIsaClosureJson.validate[IsaAccount]
      result.isSuccess shouldBe true
      result.get       shouldBe standardIsaClosureJson.as[IsaAccount]
    }

    "fail to deserialize when a decimal has more than 2 digits" in {
      val invalidJson = lifetimeIsaSubscriptionJson ++ Json.obj("amountTransferredIn" -> 2500.123)
      val result      = invalidJson.validate[IsaAccount]
      result.isError shouldBe true
      result.toString  should include("error.expected.2decimal.nonnegative")
    }

    "fail to deserialize when a decimal is negative" in {
      val invalidJson = lifetimeIsaSubscriptionJson ++ Json.obj("amountTransferredIn" -> -0.01)
      val result      = invalidJson.validate[IsaAccount]
      result.isError shouldBe true
      result.toString  should include("error.expected.2decimal.nonnegative")
    }

    "fail to deserialize when a non-number is supplied" in {
      val invalidJson = lifetimeIsaSubscriptionJson ++ Json.obj("amountTransferredIn" -> "abc")
      val result      = invalidJson.validate[IsaAccount]
      result.isError shouldBe true
      result.toString  should include("error.expected.number")
    }
  }

  "IsaAccount Writes" should {

    "serialize LifetimeIsaClosure correctly" in {
      val account = lifetimeIsaClosureJson.as[IsaAccount]
      val json    = Json.toJson(account)
      json shouldBe lifetimeIsaClosureJson
    }

    "serialize LifetimeIsaSubscription correctly" in {
      val account = lifetimeIsaSubscriptionJson.as[IsaAccount]
      val json    = Json.toJson(account)
      json shouldBe lifetimeIsaSubscriptionJson
    }

    "serialize StandardIsaSubscription correctly" in {
      val account = standardIsaSubscriptionJson.as[IsaAccount]
      val json    = Json.toJson(account)
      json shouldBe standardIsaSubscriptionJson
    }

    "serialize StandardIsaClosure correctly" in {
      val account = standardIsaClosureJson.as[IsaAccount]
      val json    = Json.toJson(account)
      json shouldBe standardIsaClosureJson
    }
  }

  "JsonValidation.firstLevelValidatorExtractNinoAndAccount" should {

    "return Right with valid nino and accountNumber" in {
      val json = Json.obj("nino" -> "AB123456C", "accountNumber" -> "ACC12345")
      firstLevelValidatorExtractNinoAndAccount(json) shouldBe Right(("AB123456C", "ACC12345"))
    }

    "return NinoOrAccountNumMissingErr when nino or accountNumber is missing" in {
      val json1 = Json.obj("accountNumber" -> "ACC12345")
      val json2 = Json.obj("nino" -> "AB123456C")
      firstLevelValidatorExtractNinoAndAccount(json1) shouldBe Left(NinoOrAccountNumMissingErr)
      firstLevelValidatorExtractNinoAndAccount(json2) shouldBe Left(NinoOrAccountNumMissingErr)
    }

    "return NinoOrAccountNumInvalidErr when nino or accountNumber are invalid types" in {
      val json = Json.obj("nino" -> 123, "accountNumber" -> true)
      firstLevelValidatorExtractNinoAndAccount(json) shouldBe Left(NinoOrAccountNumInvalidErr)
    }
  }

  "JsonValidation.findDuplicateFields" should {

    "return JsSuccess when there are no duplicates" in {
      val line = """{ "a": 1, "b": 2 }"""
      findDuplicateFields(line) shouldBe JsSuccess(())
    }

    "return JsError when a duplicate field exists" in {
      val line   = """{ "a": 1, "a": 2 }"""
      val result = findDuplicateFields(line)
      result.isError shouldBe true
      result.toString  should include("Duplicate field detected: a")
    }
  }

  "twoDecimalNum Reads" should {

    "accept JsNumber with exactly 2 decimal places" in {
      val json = JsNumber(BigDecimal("123.45"))
      json.validate(twoDecimalNum) shouldBe JsSuccess(BigDecimal("123.45"))
    }

    "reject JsNumber with more than 2 decimal places" in {
      val json = JsNumber(BigDecimal("123.456"))
      json.validate(twoDecimalNum).isError shouldBe true
    }

    "reject JsString input" in {
      JsString("123.45").validate(twoDecimalNum).isError shouldBe true
    }
  }

  "twoDecimalNumNonNegative Reads" should {

    "accept JsNumber with 2 decimals and non-negative" in {
      JsNumber(BigDecimal("0.10")).validate(twoDecimalNumNonNegative) shouldBe JsSuccess(BigDecimal("0.10"))
    }

    "reject negative JsNumber" in {
      JsNumber(BigDecimal("-0.10")).validate(twoDecimalNumNonNegative).isError shouldBe true
    }

    "reject JsNumber with more than 2 decimals" in {
      JsNumber(BigDecimal("1.234")).validate(twoDecimalNumNonNegative).isError shouldBe true
    }

    "reject non-numeric JsValue" in {
      JsString("10.00").validate(twoDecimalNumNonNegative).isError shouldBe true
    }
  }

  "twoDecimalWrites" should {

    "serialize BigDecimal to JsNumber with 2 decimal places" in {
      Json.toJson(BigDecimal("123.4")) shouldBe JsNumber(BigDecimal("123.40"))
    }
  }

  "strictLocalDateReads" should {

    "parse valid ISO date string" in {
      JsString("2025-01-31").validate(strictLocalDateReads) shouldBe JsSuccess(LocalDate.of(2025, 1, 31))
    }

    "reject invalid date string" in {
      JsString("2025-13-31").validate(strictLocalDateReads).isError shouldBe true
    }

    "reject non-string JsValue" in {
      JsNumber(20250131).validate(strictLocalDateReads).isError shouldBe true
    }
  }

  "nonEmptyStringReads" should {

    "accept a non-empty string" in {
      JsString("Hello").validate(nonEmptyStringReads) shouldBe JsSuccess("Hello")
    }

    "reject an empty string" in {
      JsString("  ").validate(nonEmptyStringReads).isError shouldBe true
    }

    "reject non-string JsValue" in {
      JsNumber(123).validate(nonEmptyStringReads).isError shouldBe true
    }
  }

  "nino Reads" should {

    "accept a valid NINO" in {
      JsString("AB123456C").validate(ninoReads) shouldBe JsSuccess("AB123456C")
    }

    "reject invalid NINO" in {
      JsString("INVALID").validate(ninoReads).isError shouldBe true
    }

    "reject non-string JsValue" in {
      JsNumber(123).validate(ninoReads).isError shouldBe true
    }
  }

  "accountNumber Reads" should {

    "accept a valid account number" in {
      JsString("ACC12345").validate(accountNumberReads) shouldBe JsSuccess("ACC12345")
    }

    "reject invalid account number (too long or bad chars)" in {
      JsString("!@#").validate(accountNumberReads).isError shouldBe true
    }

    "reject non-string JsValue" in {
      JsNumber(123).validate(accountNumberReads).isError shouldBe true
    }
  }

  "stringPattern helper" should {

    "return JsSuccess when string matches regex" in {
      val pattern = "^[A-Z]+$".r
      JsString("ABC").validate(stringPattern(pattern, "INVALID")) shouldBe JsSuccess("ABC")
    }

    "return JsError when string does not match" in {
      val pattern = "^[A-Z]+$".r
      JsString("abc").validate(stringPattern(pattern, "INVALID")).isError shouldBe true
    }

    "return JsError when not a JsString" in {
      JsNumber(1).validate(stringPattern("^[A-Z]+$".r, "INVALID")).isError shouldBe true
    }
  }
}
