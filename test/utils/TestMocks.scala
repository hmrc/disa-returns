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

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.disareturns.models.isaAccounts.{IsaType, ReasonForClosure}

trait TestMocks {

  def lifetimeIsaClosureJson: JsValue = Json.obj(
    "accountNumber"                       -> "ACC123",
    "nino"                                -> "AB123456C",
    "firstName"                           -> "John",
    "middleName"                          -> "middleName",
    "lastName"                            -> "Doe",
    "dateOfBirth"                         -> "1980-01-01",
    "isaType"                             -> "LIFETIME_CASH",
    "reportingATransfer"                  -> false,
    "dateOfLastSubscription"              -> "2025-01-01",
    "totalCurrentYearSubscriptionsToDate" -> 1000.00,
    "marketValueOfAccount"                -> 5000.00,
    "dateOfFirstSubscription"             -> "2020-01-01",
    "closureDate"                         -> "2025-06-01",
    "reasonForClosure"                    -> ReasonForClosure.VOID,
    "lisaQualifyingAddition"              -> 1000.00,
    "lisaBonusClaim"                      -> 1000.00
  )

  def lifetimeIsaNewSubscriptionJson: JsValue = Json.obj(
    "accountNumber"                       -> "ACC124",
    "nino"                                -> "AB123456D",
    "firstName"                           -> "Alice",
    "middleName"                          -> "M",
    "lastName"                            -> "Smith",
    "dateOfBirth"                         -> "1990-02-02",
    "isaType"                             -> "LIFETIME_CASH",
    "reportingATransfer"                  -> false,
    "dateOfFirstSubscription"             -> "2023-01-01",
    "dateOfLastSubscription"              -> "2025-01-01",
    "totalCurrentYearSubscriptionsToDate" -> 500.00,
    "marketValueOfAccount"                -> 2000.00,
    "lisaQualifyingAddition"              -> 500.00,
    "lisaBonusClaim"                      -> 1000.00
  )

  def lifetimeIsaTransferJson: JsValue = Json.obj(
    "accountNumber"                       -> "ACC125",
    "nino"                                -> "AB123456E",
    "firstName"                           -> "Bob",
    "middleName"                          -> "middleName",
    "lastName"                            -> "Brown",
    "dateOfBirth"                         -> "1975-03-03",
    "isaType"                             -> "LIFETIME_CASH",
    "reportingATransfer"                  -> true,
    "dateOfFirstSubscription"             -> "2018-01-01",
    "dateOfLastSubscription"              -> "2025-01-01",
    "totalCurrentYearSubscriptionsToDate" -> 300.00,
    "marketValueOfAccount"                -> 7000.00,
    "accountNumberOfTransferringAccount"  -> "TRF00001",
    "amountTransferred"                   -> 1000.00,
    "lisaQualifyingAddition"              -> 200.00,
    "lisaBonusClaim"                      -> 1000.00
  )

  def lifetimeIsaTransferAndClosureJson: JsValue = Json.obj(
    "accountNumber"                       -> "ACC126",
    "nino"                                -> "AB123456F",
    "firstName"                           -> "Carol",
    "middleName"                          -> "A",
    "lastName"                            -> "Johnson",
    "dateOfBirth"                         -> "1985-04-04",
    "isaType"                             -> IsaType.LIFETIME_CASH,
    "reportingATransfer"                  -> true,
    "dateOfFirstSubscription"             -> "2019-01-01",
    "dateOfLastSubscription"              -> "2025-01-01",
    "closureDate"                         -> "2025-07-01",
    "totalCurrentYearSubscriptionsToDate" -> 400.00,
    "marketValueOfAccount"                -> 8000.00,
    "accountNumberOfTransferringAccount"  -> "TRF00002",
    "amountTransferred"                   -> 2500.00,
    "reasonForClosure"                    -> ReasonForClosure.VOID,
    "lisaQualifyingAddition"              -> 300.00,
    "lisaBonusClaim"                      -> 1000.00
  )

  def standardIsaNewSubscriptionJson: JsValue = Json.obj(
    "accountNumber"                       -> "ACC127",
    "nino"                                -> "AB123456G",
    "firstName"                           -> "David",
    "middleName"                          -> "middleName",
    "lastName"                            -> "Miller",
    "dateOfBirth"                         -> "1995-05-05",
    "isaType"                             -> "STOCKS_AND_SHARES",
    "reportingATransfer"                  -> false,
    "dateOfLastSubscription"              -> "2025-01-01",
    "totalCurrentYearSubscriptionsToDate" -> 1000.00,
    "marketValueOfAccount"                -> 6000.00,
    "flexibleIsa"                         -> true
  )

  def standardIsaTransferJson: JsValue = Json.obj(
    "accountNumber"                       -> "ACC128",
    "nino"                                -> "AB123456H",
    "firstName"                           -> "Eva",
    "middleName"                          -> "B",
    "lastName"                            -> "Davis",
    "dateOfBirth"                         -> "1988-06-06",
    "isaType"                             -> "STOCKS_AND_SHARES",
    "reportingATransfer"                  -> true,
    "dateOfLastSubscription"              -> "2025-01-01",
    "totalCurrentYearSubscriptionsToDate" -> 1500.00,
    "marketValueOfAccount"                -> 7500.00,
    "accountNumberOfTransferringAccount"  -> "TRF00003",
    "amountTransferred"                   -> 5000.00,
    "flexibleIsa"                         -> false
  )

}
