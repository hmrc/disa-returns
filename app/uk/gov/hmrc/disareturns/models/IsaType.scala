package uk.gov.hmrc.disareturns.models

import play.api.libs.json.{Json, OFormat}

import java.time.LocalDate

sealed trait IsaType


object AccountType extends Enumeration {
  type AccountType = Value

  val CASH = Value("CASH")
  val STOCKS_AND_SHARES = Value("STOCKS_AND_SHARES")
  val INNOVATIVE_FINANCE = Value("INNOVATIVE_FINANCE")
  val LIFETIME_CASH = Value("LIFETIME_CASH")
  val LIFETIME_STOCKS_AND_SHARES = Value("LIFETIME_STOCKS_AND_SHARES")
}

case class IsaAccount(
                       accountNumber: String,
                       nino: String,
                       firstName: String,
                       middleName: Option[String],
                       lastName: String,
                       dateOfBirth: LocalDate,
                       isaType: IsaType,
                       reportingATransfer: Boolean,
                       dateOfLastSubscription: LocalDate,
                       totalCurrentYearSubscriptionsToDate: BigDecimal,
                       marketValueOfAccount: BigDecimal,
                       flexibleIsa: Boolean
                     )

object IsaAccount {
  implicit val format: OFormat[IsaAccount] = Json.format[IsaAccount]
}