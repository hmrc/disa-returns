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
        Left(NinoOrAccountNumInvalidErr)
    }
  }

  def toErrorCodePrefix(fieldName: String): String =
    fieldName
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
      .replaceAll("([a-z\\d])([A-Z])", "$1_$2")
      .toUpperCase

  def humanizeFieldName(fieldName: String): String = {
    val acronymFix = fieldName
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2") // e.g. "ATransfer" -> "A_Transfer"
      .replaceAll("([a-z\\d])([A-Z])", "$1_$2") // camelCase to snake_case
      .toLowerCase
      .split("_")

    acronymFix.head.capitalize + acronymFix.tail.map(" " + _).mkString
  }

  def buildMissingFieldError(fieldName: String): (String, String) = {
    val code    = s"MISSING_${toErrorCodePrefix(fieldName)}"
    val message = s"${humanizeFieldName(fieldName)} field is missing"
    (code, message)
  }

  def buildInvalidFieldError(fieldName: String): (String, String) = {
    val code    = s"INVALID_${toErrorCodePrefix(fieldName)}"
    val message = s"${humanizeFieldName(fieldName)} is not formatted correctly"
    (code, message)
  }

  def jsErrorToDomainError(
    jsErrors:      Seq[(JsPath, Seq[JsonValidationError])],
    nino:          String,
    accountNumber: String
  ): Seq[SecondLevelValidationError] =
    jsErrors.map { case (path, errors) =>
      val fieldName     = path.toString.stripPrefix("/").split("/").last
      val errorMessages = errors.flatMap(_.messages)
      val (code, message) = errorMessages.toList match {
        case msgs if msgs.exists(_.contains("error.path.missing")) =>
          fieldName match {
            case field => buildMissingFieldError(field)
          }
        case msgs if msgs.exists(_.contains("error.expected")) =>
          fieldName match {
            case field => buildInvalidFieldError(field)
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

  // --- Validators ---

  case class AccountIdentifiers(nino: String, accountNumber: String)

  def validateField[A](
    value:       A,
    isValid:     A => Boolean,
    errorCode:   String,
    errorMsg:    String,
    identifiers: AccountIdentifiers
  ): Either[SecondLevelValidationError, Unit] =
    if (isValid(value)) Right(())
    else
      Left(
        SecondLevelValidationError(
          nino = identifiers.nino,
          accountNumber = identifiers.accountNumber,
          code = errorCode,
          message = errorMsg
        )
      )

  def validateNonEmptyString(
    value:       String,
    fieldName:   String,
    identifiers: AccountIdentifiers
  ): Either[SecondLevelValidationError, Unit] =
    validateField[String](
      value,
      (v: String) => v.trim.nonEmpty,
      s"INVALID_${fieldName.toUpperCase}",
      s"${humanizeFieldName(fieldName)} must not be empty",
      identifiers
    )

  def validateTwoDecimal(
    value:       BigDecimal,
    fieldName:   String,
    identifiers: AccountIdentifiers
  ): Either[SecondLevelValidationError, Unit] =
    validateField[BigDecimal](
      value,
      (v: BigDecimal) => v.scale == 2,
      s"INVALID_${fieldName.toUpperCase}",
      s"${humanizeFieldName(fieldName)} must have 2 decimal places (e.g. 123.45)",
      identifiers
    )

  def validateRegex(
    value:       String,
    regex:       scala.util.matching.Regex,
    errorCode:   String,
    errorMsg:    String,
    identifiers: AccountIdentifiers
  ): Either[SecondLevelValidationError, Unit] =
    validateField(value, regex.matches, errorCode, errorMsg, identifiers)

  def runValidations(
    validations: Seq[Either[SecondLevelValidationError, Unit]]
  ): Either[SecondLevelValidationError, Unit] =
    validations.collectFirst { case Left(err) => Left(err) }.getOrElse(Right(()))

  // --- Main Validators ---

  def validateAccount(account: IsaAccount): Either[SecondLevelValidationError, Unit] = {
    val identifiers = AccountIdentifiers(account.nino, account.accountNumber)

    runValidations(
      Seq(
        validateRegex(account.nino, ninoRegex, "INVALID_NINO", "The NINO provided is invalid", identifiers),
        validateRegex(account.accountNumber, accountNumberRegex, "INVALID_ACCOUNT_NUMBER", "The ACCOUNT_NUMBER provided is invalid", identifiers),
        validateNonEmptyString(account.firstName, "first_name", identifiers),
        validateNonEmptyString(account.lastName, "last_name", identifiers),
        validateTwoDecimal(account.totalCurrentYearSubscriptionsToDate, "total_current_year_subscriptions_to_date", identifiers),
        validateTwoDecimal(account.marketValueOfAccount, "market_value_of_account", identifiers)
      )
    )
  }

  def validateIsaAccountUniqueFields(account: IsaAccount): Either[SecondLevelValidationError, Unit] = account match {

    case a: LifetimeIsaClosure =>
      val ids = AccountIdentifiers(a.nino, a.accountNumber)
      runValidations(
        Seq(
          validateTwoDecimal(a.lisaQualifyingAddition, "lisa_qualifying_addition", ids),
          validateTwoDecimal(a.lisaBonusClaim, "lisa_bonus_claim", ids)
        )
      )

    case a: LifetimeIsaNewSubscription =>
      val ids = AccountIdentifiers(a.nino, a.accountNumber)
      runValidations(
        Seq(
          validateTwoDecimal(a.lisaQualifyingAddition, "lisa_qualifying_addition", ids),
          validateTwoDecimal(a.lisaBonusClaim, "lisa_bonus_claim", ids)
        )
      )

    case a: LifetimeIsaTransfer =>
      val ids = AccountIdentifiers(a.nino, a.accountNumber)
      runValidations(
        Seq(
          validateTwoDecimal(a.lisaQualifyingAddition, "lisa_qualifying_addition", ids),
          validateTwoDecimal(a.amountTransferred, "amount_transferred", ids),
          validateTwoDecimal(a.lisaBonusClaim, "lisa_bonus_claim", ids)
        )
      )

    case a: LifetimeIsaTransferAndClosure =>
      val ids = AccountIdentifiers(a.nino, a.accountNumber)
      runValidations(
        Seq(
          validateTwoDecimal(a.lisaQualifyingAddition, "lisa_qualifying_addition", ids),
          validateTwoDecimal(a.amountTransferred, "amount_transferred", ids),
          validateTwoDecimal(a.lisaBonusClaim, "lisa_bonus_claim", ids)
        )
      )

    case _: StandardIsaNewSubscription =>
      Right(()) // No additional validations

    case a: StandardIsaTransfer =>
      val ids = AccountIdentifiers(a.nino, a.accountNumber)
      validateTwoDecimal(a.amountTransferred, "amount_transferred", ids)
  }

  def validate(account: IsaAccount): Either[SecondLevelValidationError, Unit] =
    for {
      _ <- validateAccount(account)
      _ <- validateIsaAccountUniqueFields(account)
    } yield ()
}
