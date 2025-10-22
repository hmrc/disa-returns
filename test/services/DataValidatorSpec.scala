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

package services

import play.api.libs.json.{JsPath, Json, JsonValidationError}
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.isaAccounts._
import uk.gov.hmrc.disareturns.services.validation.DataValidator
import utils.BaseUnitSpec

import java.time.LocalDate

class DataValidatorSpec extends BaseUnitSpec {
  val validNino          = "AA123456A"
  val validAccountNumber = "ABC1234567"
  val validDate:       LocalDate  = LocalDate.parse("2000-01-01")
  val validBigDecimal: BigDecimal = BigDecimal("100.00")

  def lifetimeIsaSubscriptionAccount: LifetimeIsaSubscription = LifetimeIsaSubscription(
    accountNumber = validAccountNumber,
    nino = validNino,
    firstName = "John",
    middleName = Some("Middle"),
    lastName = "Doe",
    dateOfBirth = validDate,
    isaType = IsaType.LIFETIME,
    amountTransferredIn = BigDecimal(250.00),
    amountTransferredOut = BigDecimal(250.00),
    dateOfFirstSubscription = validDate,
    dateOfLastSubscription = validDate,
    totalCurrentYearSubscriptionsToDate = validBigDecimal,
    marketValueOfAccount = validBigDecimal,
    lisaQualifyingAddition = validBigDecimal,
    lisaBonusClaim = validBigDecimal
  )

  "FirstLevelValidatorExtractNinoAndAccount" should {
    "return Right when both fields exist and are strings" in {
      val json = Json.obj("nino" -> validNino, "accountNumber" -> validAccountNumber)
      DataValidator.firstLevelValidatorExtractNinoAndAccount(json) shouldBe Right(validNino -> validAccountNumber)
    }

    "return Missing error when both fields are missing" in {
      val json = Json.obj()
      DataValidator.firstLevelValidatorExtractNinoAndAccount(json) shouldBe Left(NinoOrAccountNumMissingErr)
    }

    "return Missing error when only nino is missing" in {
      val json = Json.obj("accountNumber" -> validAccountNumber)
      DataValidator.firstLevelValidatorExtractNinoAndAccount(json) shouldBe Left(NinoOrAccountNumMissingErr)
    }

    "return Invalid error when both fields are present but wrong types" in {
      val json = Json.obj("nino" -> 123, "accountNumber" -> true)
      DataValidator.firstLevelValidatorExtractNinoAndAccount(json) shouldBe Left(NinoOrAccountNumInvalidErr)
    }
  }

  "validateAccount" should {
    "pass for valid LifetimeIsaSubscription" in {
      DataValidator.validateAccount(lifetimeIsaSubscriptionAccount) shouldBe None
    }

    "fail for invalid nino" in {
      val invalid = lifetimeIsaSubscriptionAccount.copy(nino = "INVALID")
      val result  = DataValidator.validateAccount(invalid)
      result.get.code    shouldBe "INVALID_NINO"
      result.get.message shouldBe "The Nino provided is not formatted correctly"
    }

    "fail for 1-decimal totalCurrentYearSubscriptionsToDate" in {
      val invalid = lifetimeIsaSubscriptionAccount.copy(totalCurrentYearSubscriptionsToDate = BigDecimal("12.5"))
      val result  = DataValidator.validateAccount(invalid)
      result.get.code    shouldBe "INVALID_TOTAL_CURRENT_YEAR_SUBSCRIPTIONS_TO_DATE"
      result.get.message shouldBe "Total current year subscriptions to date is not formatted correctly (e.g. 123.45)"
    }
  }

  //    TODO: check scenario
  "validateIsaAccountUniqueFields" should {
    "validate LifetimeIsaClosure fields" in {
      val account = LifetimeIsaClosure(
        accountNumber = validAccountNumber,
        nino = validNino,
        firstName = "Jane",
        middleName = None,
        lastName = "Smith",
        dateOfBirth = validDate,
        isaType = IsaType.LIFETIME,
        amountTransferredIn = BigDecimal(250.00),
        amountTransferredOut = BigDecimal(250.00),
        dateOfFirstSubscription = validDate,
        dateOfLastSubscription = validDate,
        totalCurrentYearSubscriptionsToDate = validBigDecimal,
        marketValueOfAccount = validBigDecimal,
        closureDate = validDate,
        reasonForClosure = LisaReasonForClosure.CLOSED,
        lisaQualifyingAddition = validBigDecimal,
        lisaBonusClaim = validBigDecimal
      )
      DataValidator.validateIsaAccountUniqueFields(account) shouldBe None
    }

    "fail if lisaBonusClaim has too many decimals in LifetimeIsaClosure" in {
      val account = lifetimeIsaSubscriptionAccount.copy(lisaBonusClaim = BigDecimal("100.123"))
      DataValidator.validateIsaAccountUniqueFields(account) shouldBe Some(
        SecondLevelValidationError(
          validNino,
          validAccountNumber,
          "INVALID_LISA_BONUS_CLAIM",
          "Lisa bonus claim is not formatted correctly (e.g. 123.45)"
        )
      )
    }

//    TODO: check scenario
    "validate StandardIsaTransfer with negative amountTransferred" in {
      val account = StandardIsaSubscription(
        accountNumber = validAccountNumber,
        nino = validNino,
        firstName = "John",
        middleName = None,
        lastName = "Smith",
        dateOfBirth = validDate,
        isaType = IsaType.LIFETIME,
        amountTransferredIn = BigDecimal(250.00),
        amountTransferredOut = BigDecimal(250.00),
        dateOfLastSubscription = validDate,
        totalCurrentYearSubscriptionsToDate = validBigDecimal,
        marketValueOfAccount = validBigDecimal,
        flexibleIsa = false
      )
      DataValidator.validateIsaAccountUniqueFields(account) shouldBe
        Some(
          SecondLevelValidationError(
            validNino,
            validAccountNumber,
            "INVALID_AMOUNT_TRANSFERRED",
            "Amount transferred is not formatted correctly (e.g. 123.45)"
          )
        )
    }
  }

  "validate" should {
    "validate complete IsaAccount end-to-end" in {
      DataValidator.validate(lifetimeIsaSubscriptionAccount) shouldBe None
    }

    "fail if any top-level validation fails" in {
      val invalid = lifetimeIsaSubscriptionAccount.copy(accountNumber = "!")
      val result  = DataValidator.validate(invalid)
      result shouldBe Some(
        SecondLevelValidationError(validNino, "!", "INVALID_ACCOUNT_NUMBER", "The Account number provided is not formatted correctly")
      )
    }
  }

  "jsErrorToDomainError" should {
    "map JsError with missing field to domain error" in {
      val jsErrors = Seq(JsPath \ "firstName" -> Seq(JsonValidationError("error.path.missing")))
      val result   = DataValidator.jsErrorToDomainError(jsErrors, validNino, validAccountNumber)
      result shouldBe Seq(
        SecondLevelValidationError(
          validNino,
          validAccountNumber,
          "MISSING_FIRST_NAME",
          "First name field is missing"
        )
      )
    }

    "map JsError with invalid type to domain error" in {
      val jsErrors = Seq(JsPath \ "marketValueOfAccount" -> Seq(JsonValidationError("error.expected.jsnumber")))
      val result   = DataValidator.jsErrorToDomainError(jsErrors, validNino, validAccountNumber)
      result shouldBe Seq(
        SecondLevelValidationError(
          validNino,
          validAccountNumber,
          "INVALID_MARKET_VALUE_OF_ACCOUNT",
          "Market value of account is not formatted correctly"
        )
      )
    }

    "handle unknown error gracefully" in {
      val jsErrors = Seq(JsPath \ "firstName" -> Seq(JsonValidationError("some.random.error")))
      val result   = DataValidator.jsErrorToDomainError(jsErrors, validNino, validAccountNumber)
      result.head.code shouldBe "UNKNOWN_ERROR"
    }
  }
}
