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

trait TestMocks {

  import play.api.libs.json._

  val lifetimeIsaSubscriptionJson: JsObject = Json.obj(
    "accountNumber"                       -> "STD000001",
    "nino"                                -> "AB000001C",
    "firstName"                           -> "First1",
    "middleName"                          -> "Middle1",
    "lastName"                            -> "Last1",
    "dateOfBirth"                         -> "1980-01-02",
    "isaType"                             -> "LIFETIME",
    "amountTransferredIn"                 -> 2500.11,
    "amountTransferredOut"                -> 2500.11,
    "dateOfFirstSubscription"             -> "2025-06-01",
    "dateOfLastSubscription"              -> "2025-06-01",
    "totalCurrentYearSubscriptionsToDate" -> 2500.11,
    "marketValueOfAccount"                -> 10000.11,
    "lisaQualifyingAddition"              -> -5000.11,
    "lisaBonusClaim"                      -> 5000.11
  )

  val lifetimeIsaClosureJson: JsObject = Json.obj(
    "accountNumber"                       -> "STD000001",
    "nino"                                -> "AB000001C",
    "firstName"                           -> "First1",
    "middleName"                          -> "Middle1",
    "lastName"                            -> "Last1",
    "dateOfBirth"                         -> "1980-01-02",
    "isaType"                             -> "LIFETIME",
    "amountTransferredIn"                 -> 2500.11,
    "amountTransferredOut"                -> 2500.11,
    "dateOfFirstSubscription"             -> "2025-06-01",
    "dateOfLastSubscription"              -> "2025-06-01",
    "totalCurrentYearSubscriptionsToDate" -> 2500.11,
    "marketValueOfAccount"                -> 10000.11,
    "lisaQualifyingAddition"              -> 5000.11,
    "lisaBonusClaim"                      -> -5000.11,
    "reasonForClosure"                    -> "CANCELLED",
    "closureDate"                         -> "2025-06-01"
  )

  val standardIsaSubscriptionJson: JsObject = Json.obj(
    "accountNumber"                       -> "STD000001",
    "nino"                                -> "AB000001C",
    "firstName"                           -> "First1",
    "middleName"                          -> "Middle1",
    "lastName"                            -> "Last1",
    "dateOfBirth"                         -> "1980-01-02",
    "isaType"                             -> "STOCKS_AND_SHARES",
    "amountTransferredIn"                 -> 2500.11,
    "amountTransferredOut"                -> 2500.11,
    "dateOfLastSubscription"              -> "2025-06-01",
    "totalCurrentYearSubscriptionsToDate" -> 2500.11,
    "marketValueOfAccount"                -> 10000.11,
    "flexibleIsa"                         -> false
  )

  val standardIsaClosureJson: JsObject = Json.obj(
    "accountNumber"                       -> "STD000001",
    "nino"                                -> "AB000001C",
    "firstName"                           -> "First1",
    "middleName"                          -> "Middle1",
    "lastName"                            -> "Last1",
    "dateOfBirth"                         -> "1980-01-02",
    "isaType"                             -> "STOCKS_AND_SHARES",
    "amountTransferredIn"                 -> 2500.11,
    "amountTransferredOut"                -> 2500.11,
    "dateOfLastSubscription"              -> "2025-06-01",
    "totalCurrentYearSubscriptionsToDate" -> 2500.11,
    "marketValueOfAccount"                -> 10000.11,
    "reasonForClosure"                    -> "CANCELLED",
    "closureDate"                         -> "2025-06-01",
    "flexibleIsa"                         -> false
  )
}
