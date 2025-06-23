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

package uk.gov.hmrc.disareturns.models.isaAccounts

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.disareturns.models.IsaType.IsaType


case class LifetimeIsaClosure(
                               accountNumber: String,
                               nino: String,
                               firstName: String,
                               middleName: Option[String],
                               lastName: String,
                               dateOfBirth: String,
                               isaType: IsaType,
                               reportingATransfer: Boolean,
                               dateOfLastSubscription: String,
                               totalCurrentYearSubscriptionsToDate: BigDecimal,
                               marketValueOfAccount: BigDecimal,
                               dateOfFirstSubscription: String,
                               closureDate: String,
                               reasonForClosure: String,
                               lisaQualifyingAddition: BigDecimal,
                               lisaBonusClaim: Boolean
                             ) extends IsaAccount

object LifetimeIsaClosure {
  implicit val format: OFormat[LifetimeIsaClosure] = Json.format[LifetimeIsaClosure]
}