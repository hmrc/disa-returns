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

package uk.gov.hmrc.disareturns.services.validation

import play.api.libs.json.{JsError, JsPath, JsSuccess, JsUndefined, JsValue, JsonValidationError}
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, NinoOrAccountNumInvalidErr, NinoOrAccountNumMissingErr, SecondLevelValidationError}
import uk.gov.hmrc.disareturns.models.submission.isaAccounts._

import scala.collection.Seq

object DataValidator {
  private val ninoRegex          = "^[A-CEGHJ-PR-TW-Z]{2}\\d{6}[A-D]$".r
  private val accountNumberRegex = "^[a-zA-Z0-9 :/-]{1,20}$".r //TODO: need to update with actual regex - stolen from lisa

  def FirstLevelValidatorExtractNinoAndAccount(json: JsValue): Either[ErrorResponse, (String, String)] = {
    val ninoPath    = json \ "nino"
    val accountPath = json \ "accountNumber"

    val ninoResult          = ninoPath.validate[String]
    val accountNumberResult = accountPath.validate[String]

    (ninoResult, accountNumberResult) match {
      case (JsSuccess(nino, _), JsSuccess(accountNumber, _)) =>
        Right((nino, accountNumber))
      case (JsError(_), JsError(_)) if ninoPath.isInstanceOf[JsUndefined] && accountPath.isInstanceOf[JsUndefined] =>
        Left(NinoOrAccountNumMissingErr)
      case (JsError(_), _) if ninoPath.isInstanceOf[JsUndefined] =>
        Left(NinoOrAccountNumMissingErr)
      case (_, JsError(_)) if accountPath.isInstanceOf[JsUndefined] =>
        Left(NinoOrAccountNumMissingErr)
      case _ =>
        println(Console.YELLOW + "Herre" + Console.RESET)
        Left(NinoOrAccountNumInvalidErr)
    }
  }

  def jsErrorToDomainError(
    jsErrors:      Seq[(JsPath, Seq[JsonValidationError])],
    nino:          String,
    accountNumber: String
  ): Seq[SecondLevelValidationError] =
    jsErrors.map { case (path, errors) =>
      val fieldName     = path.toString.stripPrefix("/").split("/").last
      val errorMessages = errors.flatMap(_.messages).toSet
      // We could make this a bit better so it builds the error code/message based on field name instead of matching on each?
      // Only thing it we loose visibility on what's actually defined?

      val (code, message) = errorMessages.toList match {
        case msgs if msgs.exists(_.contains("error.path.missing")) =>
          fieldName match {
            case "firstName"               => ("MISSING_FIRST_NAME", "First name field is missing")
            case "lastName"                => ("MISSING_LAST_NAME", "Last name field is missing")
            case "dateOfBirth"             => ("MISSING_DOB", "Date of birth is missing")
            case "isaType"                 => ("MISSING_ISA_TYPE", "ISA type is missing")
            case "reportingATransfer"      => ("MISSING_REPORTING_A_TRANSFER", "Reporting a transfer is missing")
            case "dateOfFirstSubscription" => ("MISSING_DATE_OF_FIRST_SUBSCRIPTION", "Date of first subscription is missing")
            case "dateOfLastSubscription"  => ("MISSING_DATE_OF_LAST_SUBSCRIPTION", "Date of last subscription is missing")
            case "totalCurrentYearSubscriptionsToDate" =>
              ("MISSING_TOTAL_CURRENT_YEAR_SUBSCRIPTION_TO_DATE", "Total current year subscription to date is missing")
            case "marketValueOfAccount" => ("MISSING_MARKET_VALUE_OF_ACCOUNT", "Market value of account is missing")
            case "accountNumberOfTransferringAccount" =>
              ("MISSING_ACCOUNT_NUMBER_OF_TRANSFERRING_ACCOUNT", "Account number of transferring account is missing")
            case "amountTransferred"      => ("MISSING_AMOUNT_TRANSFERRED", "Amount transferred is missing")
            case "flexibleIsa"            => ("MISSING_FLEXIBLE_ISA", "Flexible ISA is missing")
            case "lisaQualifyingAddition" => ("MISSING_LISA_QUALIFYING_ADDITION", "LISA qualifying addition is missing")
            case "lisaBonusClaim"         => ("MISSING_LISA_BONUS_CLAIM", "LISA bonus claim is missing")
            case "closureDate"            => ("MISSING_CLOSURE_DATE", "Closure date is missing")
            case "reasonForClosure"       => ("MISSING_REASON_FOR_CLOSURE", "Reason for closure is missing")
            case other                    => ("MISSING_UNKNOWN", s"Missing required field: $other")
          }
        case msgs if msgs.exists(_.contains("error.expected.date.isoformat")) =>
          fieldName match {
            case "dateOfBirth"             => ("INVALID_DOB_FORMAT", "Date of birth must be in YYYY-MM-DD format")
            case "dateOfFirstSubscription" => ("INVALID_DATE_OF_FIRST_SUBSCRIPTION_FORMAT", "Date of first subscription must be in YYYY-MM-DD format")
            case "dateOfLastSubscription"  => ("INVALID_DATE_OF_LAST_SUBSCRIPTION_FORMAT", "Date of last subscription must be in YYYY-MM-DD format")
            case "closureDate"             => ("INVALID_CLOSURE_DATE_FORMAT", "Closure date must be in YYYY-MM-DD format")
            case other                     => ("INVALID_DATE_FORMAT", s"Invalid date format for field: $other")
          }
        case msgs if msgs.exists(_.contains("error.expected.jsboolean")) =>
          fieldName match {
            case "reportingATransfer" => ("INVALID_REPORTING_A_TRANSFER_FORMAT", "Reporting a transfer must be a boolean value (true or false)")
            case "flexibleIsa"        => ("INVALID_FLEXIBLE_ISA", "Flexible ISA must be a boolean value (true or false)")
            case other                => ("INVALID_BOOLEAN_FORMAT", s"Invalid boolean format for field: $other")
          }
        case msgs if msgs.exists(_.contains("error.expected.validenumvalue")) =>
          fieldName match {
            case "isaType" =>
              (
                "INVALID_ISA_TYPE",
                "ISA type must be one of: CASH, STOCKS_AND_SHARES, INNOVATIVE_FINANCE, LIFETIME_CASH, or LIFETIME_STOCKS_AND_SHARES"
              )
            case "reasonForClosure" =>
              (
                "INVALID_REASON_FOR_CLOSURE",
                "Reason for closure must be one of: CANCELLED, CLOSED, VOID, TRANSFERRED_IN_FULL, or ALL_FUNDS_WITHDRAWN"
              )
            case other => ("INVALID_ENUM_FORMAT", s"Invalid enum format for field: $other")
          }

        case msgs if msgs.exists(_.contains("error.expected.jsstring")) =>
          fieldName match {
            case "accountNumber" => ("INVALID_ACCOUNT_NUMBER", "Account number must be a valid string")
            case "nino"          => ("INVALID_NINO", "NINO must be a valid string")
            case "firstName"     => ("INVALID_FIRST_NAME", "First name must be a valid string")
            case "middleName"    => ("INVALID_MIDDLE_NAME", "Middle name must be a valid string")
            case "lastName"      => ("INVALID_LAST_NAME", "Last name must be a valid string")
            case "accountNumberOfTransferringAccount" =>
              ("INVALID_ACCOUNT_NUMBER_OF_TRANSFERRING_ACCOUNT", "Account number of transferring account must be a valid string")
            case other => ("INVALID_STRING_FORMAT", s"Invalid string format for field: $other")
          }

        case _ =>
          ("UNKNOWN_ERROR", s"Validation failed for field: $fieldName")
      }

      SecondLevelValidationError(
        nino = nino,
        accountNumber = accountNumber,
        code = code,
        message = message
      )
    }

  def validateField[A](
    value:         A,
    isValid:       A => Boolean,
    errorCode:     String,
    errorMsg:      String,
    nino:          String,
    accountNumber: String
  ): Either[SecondLevelValidationError, Unit] =
    if (isValid(value)) Right(())
    else
      Left(SecondLevelValidationError(nino = nino, accountNumber = accountNumber, code = errorCode, message = errorMsg))

  def validateNinoField(nino: String, accountNumber: String): Either[SecondLevelValidationError, Unit] =
    validateField(nino, ninoRegex.matches, "INVALID_NINO", "The NINO provided is invalid", nino, accountNumber)

  def validateAccountNumberField(nino: String, accountNumber: String): Either[SecondLevelValidationError, Unit] =
    validateField(accountNumber, accountNumberRegex.matches, "INVALID_ACCOUNT_NUMBER", "The ACCOUNT_NUMBER provided is invalid", nino, accountNumber)

  def validateMoneyDecimal(value: BigDecimal): Boolean =
    value.scale == 2
  //Half up

  def validateString(value: String): Boolean =
    value.trim.nonEmpty

  def validateAccount(account: IsaAccount): Either[SecondLevelValidationError, Unit] = {
    import account._

    val validations = Seq(
      validateNinoField(nino, accountNumber),
      validateAccountNumberField(nino, accountNumber),
      validateField(firstName, validateString, "INVALID_FIRST_NAME", "First name must not be empty", nino, accountNumber),
      validateField(lastName, validateString, "INVALID_LAST_NAME", "Last name must not be empty", nino, accountNumber),
      validateField(
        totalCurrentYearSubscriptionsToDate,
        validateMoneyDecimal,
        "INVALID_TOTAL_CURRENT_YEAR_SUBSCRIPTION_TO_DATE",
        "Total current year subscriptions to date must be decimal (e.g. 123.45)",
        nino,
        accountNumber
      ),
      validateField(
        marketValueOfAccount,
        validateMoneyDecimal,
        "INVALID_MARKET_VALUE_OF_ACCOUNT",
        "Market value of account must be decimal (e.g. 123.45)",
        nino,
        accountNumber
      )
    )

    validations.collectFirst { case Left(err) => Left(err) }.getOrElse(Right(()))
  }

  def validateIsaAccountUniqueFields(account: IsaAccount): Either[SecondLevelValidationError, Unit] = account match {
    case lc: LifetimeIsaClosure =>
      validateField(
        lc.lisaQualifyingAddition,
        validateMoneyDecimal,
        "INVALID_LISA_QUALIFYING_ADDITION",
        "LISA qualifying addition must have 2 decimal places",
        lc.nino,
        lc.accountNumber
      )

    case lns: LifetimeIsaNewSubscription =>
      validateField(
        lns.lisaQualifyingAddition,
        validateMoneyDecimal,
        "INVALID_LISA_QUALIFYING_ADDITION",
        "LISA qualifying addition must have 2 decimal places",
        lns.nino,
        lns.accountNumber
      )

    case lt: LifetimeIsaTransfer =>
      val validations = Seq(
        validateField(
          lt.lisaQualifyingAddition,
          validateMoneyDecimal,
          "INVALID_LISA_QUALIFYING_ADDITION",
          "LISA qualifying addition must have 2 decimal places",
          lt.nino,
          lt.accountNumber
        ),
        validateField(
          lt.amountTransferred,
          validateMoneyDecimal,
          "INVALID_AMOUNT_TRANSFERRED",
          "Amount transferred must have 2 decimal places",
          lt.nino,
          lt.accountNumber
        )
      )
      validations.collectFirst { case Left(err) => Left(err) }.getOrElse(Right(()))

    case ltc: LifetimeIsaTransferAndClosure =>
      val validations = Seq(
        validateField(
          ltc.lisaQualifyingAddition,
          validateMoneyDecimal,
          "INVALID_LISA_QUALIFYING_ADDITION",
          "LISA qualifying addition must have 2 decimal places",
          ltc.nino,
          ltc.accountNumber
        ),
        validateField(
          ltc.amountTransferred,
          validateMoneyDecimal,
          "INVALID_AMOUNT_TRANSFERRED",
          "Amount transferred must have 2 decimal places",
          ltc.nino,
          ltc.accountNumber
        )
      )
      validations.collectFirst { case Left(err) => Left(err) }.getOrElse(Right(()))

    case sns: StandardIsaNewSubscription =>
      Right(()) // no additional BigDecimal fields beyond common ones validated already

    case st: StandardIsaTransfer =>
      validateField(
        st.amountTransferred,
        validateMoneyDecimal,
        "INVALID_AMOUNT_TRANSFERRED",
        "Amount transferred must have 2 decimal places",
        st.nino,
        st.accountNumber
      )
  }

  def validate(account: IsaAccount): Either[SecondLevelValidationError, Unit] =
    for {
      _ <- validateAccount(account) // validates shared/common fields
      _ <- validateIsaAccountUniqueFields(account) // validates subtype-specific fields
    } yield ()
}
