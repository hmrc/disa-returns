package uk.gov.hmrc.disareturns.models.submission

import uk.gov.hmrc.disareturns.models.common.{BadRequestErr, ErrorResponse}
import uk.gov.hmrc.disareturns.models.submission.isaAccounts.IsaAccount

object DataValidator {
  private val ninoRegex = "^[A-CEGHJ-PR-TW-Z]{2}\\d{6}[A-D]$".r
  private val accountNumberRegex = "".r

  def isValidNino(nino: String): Boolean =
    ninoRegex.matches(nino)

  def isValidAccountNumber(accountNumber: String): Boolean =
    accountNumberRegex.matches(accountNumber)


  def validateNino(nino: String): Either[ErrorResponse, Unit] =
    if (ninoRegex.matches(nino)) Right(())
    else Left(BadRequestErr(s"NINO format is invalid: $nino"))

  def validateAccountNumber(accountNumber: String): Either[ErrorResponse, Unit] =
    if (accountNumberRegex.matches(accountNumber)) Right(())
    else Left(BadRequestErr(s"Account number format is invalid: $accountNumber"))

  def validateAccount(account: IsaAccount): Either[ErrorResponse, Unit] =
    for {
      _ <- validateNino(account.nino)
      _ <- validateAccountNumber(account.accountNumber)
    } yield ()
}