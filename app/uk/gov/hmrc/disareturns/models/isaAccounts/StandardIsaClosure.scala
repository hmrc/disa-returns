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

import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json._
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaType.IsaType
import uk.gov.hmrc.disareturns.models.isaAccounts.ReasonForClosure.ReasonForClosure
import uk.gov.hmrc.disareturns.utils.JsonValidation._

import java.time.LocalDate

case class StandardIsaClosure(
  accountNumber:                       String,
  nino:                                String,
  firstName:                           String,
  middleName:                          Option[String],
  lastName:                            String,
  dateOfBirth:                         LocalDate,
  amountTransferredIn:                 BigDecimal,
  amountTransferredOut:                BigDecimal,
  dateOfLastSubscription:              LocalDate,
  totalCurrentYearSubscriptionsToDate: BigDecimal,
  marketValueOfAccount:                BigDecimal,
  isaType:                             IsaType,
  closureDate:                         LocalDate,
  reasonForClosure:                    ReasonForClosure,
  flexibleIsa:                         Boolean
) extends IsaAccount

object StandardIsaClosure {

  implicit val standardIsaTransferReads: Reads[StandardIsaClosure] = (
    (__ \ "accountNumber").read(accountNumberReads) and
      (__ \ "nino").read(ninoReads) and
      (__ \ "firstName").read[String] and
      (__ \ "middleName").readNullable[String] and
      (__ \ "lastName").read[String] and
      (__ \ "dateOfBirth").read[LocalDate] and
      (__ \ "amountTransferredIn").read(twoDecimalNumNonNegative) and
      (__ \ "amountTransferredOut").read(twoDecimalNumNonNegative) and
      (__ \ "dateOfLastSubscription").read[LocalDate] and
      (__ \ "totalCurrentYearSubscriptionsToDate").read(twoDecimalNumNonNegative) and
      (__ \ "marketValueOfAccount").read(twoDecimalNumNonNegative) and
      (__ \ "isaType").read[IsaType] and
      (__ \ "closureDate").read[LocalDate] and
      (__ \ "reasonForClosure").read[ReasonForClosure] and
      (__ \ "flexibleIsa").read[Boolean]
  )(StandardIsaClosure.apply _)

  implicit val standardIsaClosureWrites: OWrites[StandardIsaClosure] = (
    (__ \ "accountNumber").write[String] and
      (__ \ "nino").write[String] and
      (__ \ "firstName").write[String] and
      (__ \ "middleName").writeNullable[String] and
      (__ \ "lastName").write[String] and
      (__ \ "dateOfBirth").write[LocalDate] and
      (__ \ "amountTransferredIn").write(twoDecimalWrites) and
      (__ \ "amountTransferredOut").write(twoDecimalWrites) and
      (__ \ "dateOfLastSubscription").write[LocalDate] and
      (__ \ "totalCurrentYearSubscriptionsToDate").write(twoDecimalWrites) and
      (__ \ "marketValueOfAccount").write(twoDecimalWrites) and
      (__ \ "isaType").write[IsaType] and
      (__ \ "closureDate").write[LocalDate] and
      (__ \ "reasonForClosure").write[ReasonForClosure] and
      (__ \ "flexibleIsa").write[Boolean]
  )(unlift(StandardIsaClosure.unapply))

}
