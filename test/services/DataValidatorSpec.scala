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

import play.api.libs.json.{JsPath, JsValue, Json, JsonValidationError}
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.submission.isaAccounts.{IsaType, LifetimeIsaClosure, LifetimeIsaNewSubscription, LifetimeIsaTransfer, LifetimeIsaTransferAndClosure, ReasonForClosure, StandardIsaNewSubscription, StandardIsaTransfer}
import uk.gov.hmrc.disareturns.services.validation.DataValidator
import utils.BaseUnitSpec

import java.time.LocalDate

class DataValidatorSpec extends BaseUnitSpec {

  val validJson: JsValue = Json.parse("""
      |{
      |  "accountNumber": "STD000001",
      |  "nino": "AB123456C"
      |}
      |""".stripMargin)

  val invalidJsonMissingFields: JsValue = Json.parse("""
      |{
      |  "nino": "AB123456C"
      |}
      |""".stripMargin)

  "FirstLevelValidatorExtractNinoAndAccount" should {
    "extract nino and accountNumber when both are present and valid" in {
      val result = DataValidator.FirstLevelValidatorExtractNinoAndAccount(validJson)
      result shouldBe Right(("AB123456C", "STD000001"))
    }

    "return NinoOrAccountNumMissingErr when a required field is missing" in {
      val result = DataValidator.FirstLevelValidatorExtractNinoAndAccount(invalidJsonMissingFields)
      result shouldBe Left(NinoOrAccountNumMissingErr)
    }

    "return NinoOrAccountNumInvalidErr when values are wrong type" in {
      val malformed = Json.parse("""
          |{
          |  "accountNumber": 123,
          |  "nino": false
          |}
          |""".stripMargin)
      val result = DataValidator.FirstLevelValidatorExtractNinoAndAccount(malformed)
      result shouldBe Left(NinoOrAccountNumInvalidErr)
    }
  }

  "Malformed JSON" should {
    "throw JsResultException and return NinoOrAccountNumInvalidErr" in {
      val invalidString = """{ "accountNumber": STD000001, "nino": "AB123456C" }"""
      val result =
        try {
          Json.parse(invalidString)
          fail("Expected JsonParseException")
        } catch {
          case _: com.fasterxml.jackson.core.JsonParseException =>
            Left(NinoOrAccountNumInvalidErr)
          case other => fail(s"Unexpected exception: $other")
        }
      result shouldBe Left(NinoOrAccountNumInvalidErr)
    }
  }

  "validateMoneyDecimal" should {
    "accept valid 2-decimal places" in {
      DataValidator.validateMoneyDecimal(BigDecimal("123.45")) shouldBe true
    }

    "reject more than 2 decimals" in {
      DataValidator.validateMoneyDecimal(BigDecimal("123.456")) shouldBe false
    }
  }

  "validateString" should {
    "accept non-empty" in {
      DataValidator.validateString("hello") shouldBe true
    }

    "reject blank" in {
      DataValidator.validateString("   ") shouldBe false
    }
  }

  "jsErrorToDomainError" should {
    "map expected missing fields" in {
      val errors = Seq(
        (JsPath \ "firstName", Seq(JsonValidationError("error.path.missing"))),
        (JsPath \ "lastName", Seq(JsonValidationError("error.path.missing"))),
        (JsPath \ "dateOfBirth", Seq(JsonValidationError("error.path.missing"))),
        (JsPath \ "isaType", Seq(JsonValidationError("error.path.missing"))),
        (JsPath \ "reportingATransfer", Seq(JsonValidationError("error.path.missing"))),
        (JsPath \ "dateOfFirstSubscription", Seq(JsonValidationError("error.path.missing"))),
        (JsPath \ "dateOfLastSubscription", Seq(JsonValidationError("error.path.missing"))),
        (JsPath \ "totalCurrentYearSubscriptionsToDate", Seq(JsonValidationError("error.path.missing"))),
        (JsPath \ "marketValueOfAccount", Seq(JsonValidationError("error.path.missing"))),
        (JsPath \ "accountNumberOfTransferringAccount", Seq(JsonValidationError("error.path.missing"))),
        (JsPath \ "amountTransferred", Seq(JsonValidationError("error.path.missing"))),
        (JsPath \ "flexibleIsa", Seq(JsonValidationError("error.path.missing"))),
        (JsPath \ "lisaQualifyingAddition", Seq(JsonValidationError("error.path.missing"))),
        (JsPath \ "lisaBonusClaim", Seq(JsonValidationError("error.path.missing"))),
        (JsPath \ "closureDate", Seq(JsonValidationError("error.path.missing"))),
        (JsPath \ "reasonForClosure", Seq(JsonValidationError("error.path.missing")))
      )

      val result = DataValidator.jsErrorToDomainError(errors, "AB123456C", "STD000001")
      result shouldBe Seq(
        SecondLevelValidationError("AB123456C", "STD000001", "MISSING_FIRST_NAME", "First name field is missing"),
        SecondLevelValidationError("AB123456C", "STD000001", "MISSING_LAST_NAME", "Last name field is missing"),
        SecondLevelValidationError("AB123456C", "STD000001", "MISSING_DATE_OF_BIRTH", "Date of birth field is missing"),
        SecondLevelValidationError("AB123456C", "STD000001", "MISSING_ISA_TYPE", "Isa type field is missing"),
        SecondLevelValidationError("AB123456C", "STD000001", "MISSING_REPORTING_A_TRANSFER", "Reporting a transfer field is missing"),
        SecondLevelValidationError("AB123456C", "STD000001", "MISSING_DATE_OF_FIRST_SUBSCRIPTION", "Date of first subscription field is missing"),
        SecondLevelValidationError("AB123456C", "STD000001", "MISSING_DATE_OF_LAST_SUBSCRIPTION", "Date of last subscription field is missing"),
        SecondLevelValidationError(
          "AB123456C",
          "STD000001",
          "MISSING_TOTAL_CURRENT_YEAR_SUBSCRIPTIONS_TO_DATE",
          "Total current year subscriptions to date field is missing"
        ),
        SecondLevelValidationError("AB123456C", "STD000001", "MISSING_MARKET_VALUE_OF_ACCOUNT", "Market value of account field is missing"),
        SecondLevelValidationError(
          "AB123456C",
          "STD000001",
          "MISSING_ACCOUNT_NUMBER_OF_TRANSFERRING_ACCOUNT",
          "Account number of transferring account field is missing"
        ),
        SecondLevelValidationError("AB123456C", "STD000001", "MISSING_AMOUNT_TRANSFERRED", "Amount transferred field is missing"),
        SecondLevelValidationError("AB123456C", "STD000001", "MISSING_FLEXIBLE_ISA", "Flexible isa field is missing"),
        SecondLevelValidationError("AB123456C", "STD000001", "MISSING_LISA_QUALIFYING_ADDITION", "Lisa qualifying addition field is missing"),
        SecondLevelValidationError("AB123456C", "STD000001", "MISSING_LISA_BONUS_CLAIM", "Lisa bonus claim field is missing"),
        SecondLevelValidationError("AB123456C", "STD000001", "MISSING_CLOSURE_DATE", "Closure date field is missing"),
        SecondLevelValidationError("AB123456C", "STD000001", "MISSING_REASON_FOR_CLOSURE", "Reason for closure field is missing")
      )
    }

    "map enum and type errors" in {
      val errors = Seq(
        (JsPath \ "isaType", Seq(JsonValidationError("error.expected.validenumvalue"))),
        (JsPath \ "flexibleIsa", Seq(JsonValidationError("error.expected.jsboolean")))
      )
      val result = DataValidator.jsErrorToDomainError(errors, "AB123456C", "STD000001")
      result should contain allOf (
        SecondLevelValidationError(
          "AB123456C",
          "STD000001",
          "INVALID_ISA_TYPE",
          "Isa type is not formatted correctly"
        ),
        SecondLevelValidationError("AB123456C", "STD000001", "INVALID_FLEXIBLE_ISA", "Flexible isa is not formatted correctly")
      )
    }

    "fallback to UNKNOWN_ERROR for unknown validation" in {
      val result = DataValidator.jsErrorToDomainError(Seq((JsPath \ "foo", Seq(JsonValidationError("unexpected")))), "AB123456C", "STD000001")
      result should contain(SecondLevelValidationError("AB123456C", "STD000001", "UNKNOWN_ERROR", "Validation failed for field: foo"))
    }
  }

  "validateIsaAccountUniqueFields" should {

    "return Right(()) for valid LifetimeIsaClosure" in {
      val account = LifetimeIsaClosure(
        accountNumber = "STD000001",
        nino = "AB123456C",
        firstName = "John",
        middleName = None,
        lastName = "Doe",
        dateOfBirth = LocalDate.parse("1980-01-01"),
        isaType = IsaType.LIFETIME_CASH,
        reportingATransfer = false,
        dateOfLastSubscription = LocalDate.parse("2025-06-01"),
        totalCurrentYearSubscriptionsToDate = BigDecimal("1000.00"),
        marketValueOfAccount = BigDecimal("5000.00"),
        dateOfFirstSubscription = LocalDate.parse("2020-01-01"),
        closureDate = LocalDate.parse("2025-06-30"),
        reasonForClosure = ReasonForClosure.VOID,
        lisaQualifyingAddition = BigDecimal("1000.00"),
        lisaBonusClaim = BigDecimal("1000.00")
      )

      DataValidator.validateIsaAccountUniqueFields(account) shouldBe Right(())
    }

    "return Left for invalid LifetimeIsaClosure lisaQualifyingAddition (too many decimals)" in {
      val account = LifetimeIsaClosure(
        accountNumber = "STD000001",
        nino = "AB123456C",
        firstName = "John",
        middleName = None,
        lastName = "Doe",
        dateOfBirth = LocalDate.parse("1980-01-01"),
        isaType = IsaType.LIFETIME_CASH,
        reportingATransfer = false,
        dateOfLastSubscription = LocalDate.parse("2025-06-01"),
        totalCurrentYearSubscriptionsToDate = BigDecimal("1000.00"),
        marketValueOfAccount = BigDecimal("5000.00"),
        dateOfFirstSubscription = LocalDate.parse("2020-01-01"),
        closureDate = LocalDate.parse("2025-06-30"),
        reasonForClosure = ReasonForClosure.VOID,
        lisaQualifyingAddition = BigDecimal("1000.001"),
        lisaBonusClaim = BigDecimal("1000.00")
      )

      DataValidator.validateIsaAccountUniqueFields(account) shouldBe Left(
        SecondLevelValidationError(
          "AB123456C",
          "STD000001",
          "INVALID_LISA_QUALIFYING_ADDITION",
          "LISA qualifying addition must have 2 decimal places"
        )
      )
    }

    "return Right(()) for valid LifetimeIsaNewSubscription" in {
      val account = LifetimeIsaNewSubscription(
        accountNumber = "STD000002",
        nino = "AB123456C",
        firstName = "Alice",
        middleName = Some("M"),
        lastName = "Smith",
        dateOfBirth = LocalDate.parse("1990-02-02"),
        isaType = IsaType.LIFETIME_CASH,
        reportingATransfer = true,
        dateOfFirstSubscription = LocalDate.parse("2023-01-01"),
        dateOfLastSubscription = LocalDate.parse("2025-05-01"),
        totalCurrentYearSubscriptionsToDate = BigDecimal("500.00"),
        marketValueOfAccount = BigDecimal("2000.00"),
        lisaQualifyingAddition = BigDecimal("500.00"),
        lisaBonusClaim = BigDecimal("1000.00")
      )

      DataValidator.validateIsaAccountUniqueFields(account) shouldBe Right(())
    }

    "return Left for invalid LifetimeIsaNewSubscription lisaQualifyingAddition" in {
      val account = LifetimeIsaNewSubscription(
        accountNumber = "STD000002",
        nino = "AB123456C",
        firstName = "Alice",
        middleName = Some("M"),
        lastName = "Smith",
        dateOfBirth = LocalDate.parse("1990-02-02"),
        isaType = IsaType.LIFETIME_CASH,
        reportingATransfer = true,
        dateOfFirstSubscription = LocalDate.parse("2023-01-01"),
        dateOfLastSubscription = LocalDate.parse("2025-05-01"),
        totalCurrentYearSubscriptionsToDate = BigDecimal("500.00"),
        marketValueOfAccount = BigDecimal("2000.00"),
        lisaQualifyingAddition = BigDecimal("500.001"),
        lisaBonusClaim = BigDecimal("1000.00")
      )

      DataValidator.validateIsaAccountUniqueFields(account) shouldBe Left(
        SecondLevelValidationError(
          "AB123456C",
          "STD000002",
          "INVALID_LISA_QUALIFYING_ADDITION",
          "LISA qualifying addition must have 2 decimal places"
        )
      )
    }

    "return Right(()) for valid LifetimeIsaTransfer" in {
      val account = LifetimeIsaTransfer(
        accountNumber = "STD000003",
        nino = "AB123456C",
        firstName = "Bob",
        middleName = None,
        lastName = "Brown",
        dateOfBirth = LocalDate.parse("1975-03-03"),
        isaType = IsaType.LIFETIME_CASH,
        reportingATransfer = true,
        dateOfFirstSubscription = LocalDate.parse("2018-01-01"),
        dateOfLastSubscription = LocalDate.parse("2025-06-01"),
        totalCurrentYearSubscriptionsToDate = BigDecimal("300.00"),
        marketValueOfAccount = BigDecimal("7000.00"),
        accountNumberOfTransferringAccount = "TRF00001",
        amountTransferred = BigDecimal("1000.00"),
        lisaQualifyingAddition = BigDecimal("200.00"),
        lisaBonusClaim = BigDecimal("1000.00")
      )

      DataValidator.validateIsaAccountUniqueFields(account) shouldBe Right(())
    }

    "return Left if LifetimeIsaTransfer has invalid lisaQualifyingAddition" in {
      val account = LifetimeIsaTransfer(
        accountNumber = "STD000003",
        nino = "AB123456C",
        firstName = "Bob",
        middleName = None,
        lastName = "Brown",
        dateOfBirth = LocalDate.parse("1975-03-03"),
        isaType = IsaType.LIFETIME_CASH,
        reportingATransfer = true,
        dateOfFirstSubscription = LocalDate.parse("2018-01-01"),
        dateOfLastSubscription = LocalDate.parse("2025-06-01"),
        totalCurrentYearSubscriptionsToDate = BigDecimal("300.00"),
        marketValueOfAccount = BigDecimal("7000.00"),
        accountNumberOfTransferringAccount = "TRF00001",
        amountTransferred = BigDecimal("1000.00"),
        lisaQualifyingAddition = BigDecimal("200.001"),
        lisaBonusClaim = BigDecimal("1000.00")
      )

      DataValidator.validateIsaAccountUniqueFields(account) shouldBe Left(
        SecondLevelValidationError(
          "AB123456C",
          "STD000003",
          "INVALID_LISA_QUALIFYING_ADDITION",
          "LISA qualifying addition must have 2 decimal places"
        )
      )
    }

    "return Left if LifetimeIsaTransfer has invalid amountTransferred" in {
      val account = LifetimeIsaTransfer(
        accountNumber = "STD000003",
        nino = "AB123456C",
        firstName = "Bob",
        middleName = None,
        lastName = "Brown",
        dateOfBirth = LocalDate.parse("1975-03-03"),
        isaType = IsaType.LIFETIME_CASH,
        reportingATransfer = true,
        dateOfFirstSubscription = LocalDate.parse("2018-01-01"),
        dateOfLastSubscription = LocalDate.parse("2025-06-01"),
        totalCurrentYearSubscriptionsToDate = BigDecimal("300.00"),
        marketValueOfAccount = BigDecimal("7000.00"),
        accountNumberOfTransferringAccount = "TRF00001",
        amountTransferred = BigDecimal("1000.001"),
        lisaQualifyingAddition = BigDecimal("200.00"),
        lisaBonusClaim = BigDecimal("1000.00")
      )

      DataValidator.validateIsaAccountUniqueFields(account) shouldBe Left(
        SecondLevelValidationError("AB123456C", "STD000003", "INVALID_AMOUNT_TRANSFERRED", "Amount transferred must have 2 decimal places")
      )
    }

    "return Right(()) for valid LifetimeIsaTransferAndClosure" in {
      val account = LifetimeIsaTransferAndClosure(
        accountNumber = "STD000004",
        nino = "AB123456C",
        firstName = "Carol",
        middleName = Some("A"),
        lastName = "Johnson",
        dateOfBirth = LocalDate.parse("1985-04-04"),
        isaType = IsaType.LIFETIME_CASH,
        reportingATransfer = true,
        dateOfFirstSubscription = LocalDate.parse("2019-01-01"),
        dateOfLastSubscription = LocalDate.parse("2025-06-01"),
        closureDate = LocalDate.parse("2025-07-01"),
        totalCurrentYearSubscriptionsToDate = BigDecimal("400.00"),
        marketValueOfAccount = BigDecimal("8000.00"),
        accountNumberOfTransferringAccount = "TRF00002",
        amountTransferred = BigDecimal("2500.00"),
        reasonForClosure = ReasonForClosure.CLOSED,
        lisaQualifyingAddition = BigDecimal("300.00"),
        lisaBonusClaim = BigDecimal("1000.00")
      )

      DataValidator.validateIsaAccountUniqueFields(account) shouldBe Right(())
    }

    "return Left if LifetimeIsaTransferAndClosure has invalid lisaQualifyingAddition" in {
      val account = LifetimeIsaTransferAndClosure(
        accountNumber = "STD000004",
        nino = "AB123456C",
        firstName = "Carol",
        middleName = Some("A"),
        lastName = "Johnson",
        dateOfBirth = LocalDate.parse("1985-04-04"),
        isaType = IsaType.LIFETIME_CASH,
        reportingATransfer = true,
        dateOfFirstSubscription = LocalDate.parse("2019-01-01"),
        dateOfLastSubscription = LocalDate.parse("2025-06-01"),
        closureDate = LocalDate.parse("2025-07-01"),
        totalCurrentYearSubscriptionsToDate = BigDecimal("400.00"),
        marketValueOfAccount = BigDecimal("8000.00"),
        accountNumberOfTransferringAccount = "TRF00002",
        amountTransferred = BigDecimal("2500.00"),
        reasonForClosure = ReasonForClosure.CLOSED,
        lisaQualifyingAddition = BigDecimal("300.001"),
        lisaBonusClaim = BigDecimal("1000.00")
      )

      DataValidator.validateIsaAccountUniqueFields(account) shouldBe Left(
        SecondLevelValidationError(
          "AB123456C",
          "STD000004",
          "INVALID_LISA_QUALIFYING_ADDITION",
          "LISA qualifying addition must have 2 decimal places"
        )
      )
    }

    "return Left if LifetimeIsaTransferAndClosure has invalid amountTransferred" in {
      val account = LifetimeIsaTransferAndClosure(
        accountNumber = "STD000004",
        nino = "AB123456C",
        firstName = "Carol",
        middleName = Some("A"),
        lastName = "Johnson",
        dateOfBirth = LocalDate.parse("1985-04-04"),
        isaType = IsaType.LIFETIME_CASH,
        reportingATransfer = true,
        dateOfFirstSubscription = LocalDate.parse("2019-01-01"),
        dateOfLastSubscription = LocalDate.parse("2025-06-01"),
        closureDate = LocalDate.parse("2025-07-01"),
        totalCurrentYearSubscriptionsToDate = BigDecimal("400.00"),
        marketValueOfAccount = BigDecimal("8000.00"),
        accountNumberOfTransferringAccount = "TRF00002",
        amountTransferred = BigDecimal("2500.001"),
        reasonForClosure = ReasonForClosure.CLOSED,
        lisaQualifyingAddition = BigDecimal("300.00"),
        lisaBonusClaim = BigDecimal("1000.00")
      )

      DataValidator.validateIsaAccountUniqueFields(account) shouldBe Left(
        SecondLevelValidationError("AB123456C", "STD000004", "INVALID_AMOUNT_TRANSFERRED", "Amount transferred must have 2 decimal places")
      )
    }

    "return Right(()) for valid StandardIsaNewSubscription (no additional checks)" in {
      val account = StandardIsaNewSubscription(
        accountNumber = "STD000005",
        nino = "AB123456C",
        firstName = "David",
        middleName = None,
        lastName = "Miller",
        dateOfBirth = LocalDate.parse("1995-05-05"),
        isaType = IsaType.CASH,
        reportingATransfer = false,
        dateOfLastSubscription = LocalDate.parse("2025-06-01"),
        totalCurrentYearSubscriptionsToDate = BigDecimal("1000.00"),
        marketValueOfAccount = BigDecimal("6000.00"),
        flexibleIsa = true
      )

      DataValidator.validateIsaAccountUniqueFields(account) shouldBe Right(())
    }

    "return Right(()) for valid StandardIsaTransfer" in {
      val account = StandardIsaTransfer(
        accountNumber = "STD000006",
        nino = "AB123456C",
        firstName = "Eva",
        middleName = Some("B"),
        lastName = "Davis",
        dateOfBirth = LocalDate.parse("1988-06-06"),
        isaType = IsaType.CASH,
        reportingATransfer = true,
        dateOfLastSubscription = LocalDate.parse("2025-06-01"),
        totalCurrentYearSubscriptionsToDate = BigDecimal("1500.00"),
        marketValueOfAccount = BigDecimal("7500.00"),
        accountNumberOfTransferringAccount = "TRF00003",
        amountTransferred = BigDecimal("5000.00"),
        flexibleIsa = false
      )

      DataValidator.validateIsaAccountUniqueFields(account) shouldBe Right(())
    }

    "return Left for invalid StandardIsaTransfer amountTransferred" in {
      val account = StandardIsaTransfer(
        accountNumber = "STD000006",
        nino = "AB123456C",
        firstName = "Eva",
        middleName = Some("B"),
        lastName = "Davis",
        dateOfBirth = LocalDate.parse("1988-06-06"),
        isaType = IsaType.CASH,
        reportingATransfer = true,
        dateOfLastSubscription = LocalDate.parse("2025-06-01"),
        totalCurrentYearSubscriptionsToDate = BigDecimal("1500.00"),
        marketValueOfAccount = BigDecimal("7500.00"),
        accountNumberOfTransferringAccount = "TRF00003",
        amountTransferred = BigDecimal("5000.123"),
        flexibleIsa = false
      )

      DataValidator.validateIsaAccountUniqueFields(account) shouldBe Left(
        SecondLevelValidationError("AB123456C", "STD000006", "INVALID_AMOUNT_TRANSFERRED", "Amount transferred must have 2 decimal places")
      )
    }
  }
}
