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
      println(Console.YELLOW + errorMessages + Console.RESET)
      val (code, message) = errorMessages.toList match {
        case msgs if msgs.exists(_.contains("error.path.missing")) =>
          fieldName match {
            case field => buildMissingFieldError(field)
            case _     => ("MISSING_FIELD", "Not js field found")
          }
        case msgs if msgs.exists(_.contains("error.expected")) =>
          fieldName match {
            case field => buildInvalidFieldError(field)
            case _     => ("MISSING_FIELD", "Not js field found")
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
  //TODO: look at Half up

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
    case lifetimeIsaClosure: LifetimeIsaClosure =>
      validateField(
        lifetimeIsaClosure.lisaQualifyingAddition,
        validateMoneyDecimal,
        "INVALID_LISA_QUALIFYING_ADDITION",
        "LISA qualifying addition must have 2 decimal places",
        lifetimeIsaClosure.nino,
        lifetimeIsaClosure.accountNumber
      )

    case lifetimeIsaNewSubscription: LifetimeIsaNewSubscription =>
      validateField(
        lifetimeIsaNewSubscription.lisaQualifyingAddition,
        validateMoneyDecimal,
        "INVALID_LISA_QUALIFYING_ADDITION",
        "LISA qualifying addition must have 2 decimal places",
        lifetimeIsaNewSubscription.nino,
        lifetimeIsaNewSubscription.accountNumber
      )

    case lifetimeIsaTransfer: LifetimeIsaTransfer =>
      val validations = Seq(
        validateField(
          lifetimeIsaTransfer.lisaQualifyingAddition,
          validateMoneyDecimal,
          "INVALID_LISA_QUALIFYING_ADDITION",
          "LISA qualifying addition must have 2 decimal places",
          lifetimeIsaTransfer.nino,
          lifetimeIsaTransfer.accountNumber
        ),
        validateField(
          lifetimeIsaTransfer.amountTransferred,
          validateMoneyDecimal,
          "INVALID_AMOUNT_TRANSFERRED",
          "Amount transferred must have 2 decimal places",
          lifetimeIsaTransfer.nino,
          lifetimeIsaTransfer.accountNumber
        )
      )
      validations.collectFirst { case Left(err) => Left(err) }.getOrElse(Right(()))

    case lifetimeIsaTransferAndClosure: LifetimeIsaTransferAndClosure =>
      val validations = Seq(
        validateField(
          lifetimeIsaTransferAndClosure.lisaQualifyingAddition,
          validateMoneyDecimal,
          "INVALID_LISA_QUALIFYING_ADDITION",
          "LISA qualifying addition must have 2 decimal places",
          lifetimeIsaTransferAndClosure.nino,
          lifetimeIsaTransferAndClosure.accountNumber
        ),
        validateField(
          lifetimeIsaTransferAndClosure.amountTransferred,
          validateMoneyDecimal,
          "INVALID_AMOUNT_TRANSFERRED",
          "Amount transferred must have 2 decimal places",
          lifetimeIsaTransferAndClosure.nino,
          lifetimeIsaTransferAndClosure.accountNumber
        )
      )
      validations.collectFirst { case Left(err) => Left(err) }.getOrElse(Right(()))

    case standardIsaNewSubscription: StandardIsaNewSubscription =>
      Right(()) // no additional BigDecimal fields beyond common ones validated already

    case standardIsaTransfer: StandardIsaTransfer =>
      validateField(
        standardIsaTransfer.amountTransferred,
        validateMoneyDecimal,
        "INVALID_AMOUNT_TRANSFERRED",
        "Amount transferred must have 2 decimal places",
        standardIsaTransfer.nino,
        standardIsaTransfer.accountNumber
      )
  }

  def validate(account: IsaAccount): Either[SecondLevelValidationError, Unit] =
    for {
      _ <- validateAccount(account) // validates shared/common fields
      _ <- validateIsaAccountUniqueFields(account) // validates subtype-specific fields
    } yield ()
}
