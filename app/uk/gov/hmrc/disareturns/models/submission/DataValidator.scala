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

package uk.gov.hmrc.disareturns.models.submission

import play.api.libs.json.{JsPath, JsValue, JsonValidationError}
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, NinoOrAccountNumMissingErr, SecondLevelValidationError}
import uk.gov.hmrc.disareturns.models.submission.isaAccounts.IsaAccount
import scala.collection.Seq


object DataValidator {
  private val ninoRegex = "^[A-CEGHJ-PR-TW-Z]{2}\\d{6}[A-D]$".r
  private val accountNumberRegex = "^[a-zA-Z0-9 :/-]{1,20}$".r //TODO: need to update with actual regex - stolen from lisa
  private val dateRegex = "^\\d{4}-\\d{2}-\\d{2}$".r
  private val decimalRegex = "^\\d+\\.\\d{2}$".r

  def FirstLevelValidatorExtractNinoAndAccount(json: JsValue): Either[ErrorResponse, (String, String)] = {
    val nino = (json \ "nino").asOpt[String]
    val accountNumber = (json \ "accountNumber").asOpt[String]

    (nino, accountNumber) match {
      case (Some(nino), Some(acc)) => Right((nino, acc))
      case _ => Left(NinoOrAccountNumMissingErr)
    }
  }

  def validateNino(nino: String, accountNumber: String): Either[ErrorResponse, Unit] =
    if (ninoRegex.matches(nino)) Right(())
    else Left(SecondLevelValidationError(nino = nino, accountNumber = accountNumber, code = "INVALID_NINO", message = "The NINO provided is invalid"))

  def validateAccountNumber(nino: String, accountNumber: String): Either[ErrorResponse, Unit] =
    if (accountNumberRegex.matches(accountNumber)) Right(())
    else
      Left(
        SecondLevelValidationError(
          nino = nino,
          accountNumber = accountNumber,
          code = "INVALID_ACCOUNT_NUMBER",
          message = "The ACCOUNT_NUMBER provided is invalid"
        )
      )

  def validateAccount(account: IsaAccount): Either[ErrorResponse, Unit] =
    for {
      _ <- validateNino(account.nino, account.accountNumber)
      _ <- validateAccountNumber(account.nino, account.accountNumber)
    } yield ()

  def jsErrorToDomainError(
                            jsErrors: Seq[(JsPath, Seq[JsonValidationError])],
                            nino: String,
                            accountNumber: String
                          ): Seq[SecondLevelValidationError] = {
    jsErrors.collect {
      case (path, errors) if errors.exists(_.messages.contains("error.path.missing")) =>
        val fieldName = path.toString().stripPrefix("/")

        val (code, message) = fieldName match {
          case "firstName" => ("MISSING_FIRST_NAME", "First name field is missing")
          case "lastName" => ("MISSING_LAST_NAME", "Last name field is missing")
          case "dateOfBirth" => ("MISSING_DOB", "Date of birth is missing")
          case "isaType" => ("MISSING_ISA_TYPE", "ISA type is missing")
          case "reportingATransfer" => ("MISSING_REPORTING_A_TRANSFER", "Reporting a transfer is missing")
          case "dateOfFirstSubscription" => ("MISSING_DATE_OF_FIRST_SUBSCRIPTION", "Date of first subscription is missing")
          case "dateOfLastSubscription" => ("MISSING_DATE_OF_LAST_SUBSCRIPTION", "Date of last subscription is missing")
          case "totalCurrentYearSubscriptionsToDate" => ("MISSING_TOTAL_CURRENT_YEAR_SUBSCRIPTION_TO_DATE", "Total current year subscription to date is missing")
          case "marketValueOfAccount" => ("MISSING_MARKET_VALUE_OF_ACCOUNT", "Market value of account is missing")
          case "accountNumberOfTransferringAccount" => ("MISSING_ACCOUNT_NUMBER_OF_TRANSFERRING_ACCOUNT", "Account number of transferring account is missing")
          case "amountTransferred" => ("MISSING_AMOUNT_TRANSFERRED", "Account transferred is missing")
          case "flexibleIsa" => ("MISSING_FLEXIBLE_ISA", "Flexible ISA is missing")
          case "lisaQualifyingAddition" => ("MISSING_LISA_QUALIFYING_ADDITION", "LISA qualifying addition is missing")
          case "lisaBonusClaim" => ("MISSING_LISA_BONUS_CLAIM", "Flexible ISA is missing")
          case "closureDate" => ("MISSING_CLOSURE_DATE", "Closure date is missing")
          case "reasonForClosure" => ("MISSING_REASON_FOR_CLOSURE", "Reason for closure is missing")
          case other =>
            ("MISSING_UNKNOWN", s"Missing required field: $other")
        }

        SecondLevelValidationError(
          nino = nino,
          accountNumber = accountNumber,
          code = code,
          message = message
        )
    }
  }
}
