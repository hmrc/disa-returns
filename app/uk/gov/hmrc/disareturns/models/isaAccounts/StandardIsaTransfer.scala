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

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json._
import IsaType.IsaType
import uk.gov.hmrc.disareturns.utils.JsonImplicits._

import java.time.LocalDate

case class StandardIsaTransfer(
  accountNumber:                       String,
  nino:                                String,
  firstName:                           String,
  middleName:                          Option[String],
  lastName:                            String,
  dateOfBirth:                         LocalDate,
  isaType:                             IsaType,
  reportingATransfer:                  Boolean,
  dateOfLastSubscription:              LocalDate,
  totalCurrentYearSubscriptionsToDate: BigDecimal,
  marketValueOfAccount:                BigDecimal,
  accountNumberOfTransferringAccount:  String,
  amountTransferred:                   BigDecimal,
  flexibleIsa:                         Boolean
) extends IsaAccount

object StandardIsaTransfer {

  implicit val standardIsaTransferReads: Reads[StandardIsaTransfer] = (
    (__ \ "accountNumber").read[String] and
      (__ \ "nino").read[String] and
      (__ \ "firstName").read[String] and
      (__ \ "middleName").readNullable[String] and
      (__ \ "lastName").read[String] and
      (__ \ "dateOfBirth").read[LocalDate] and
      (__ \ "isaType").read[IsaType] and
      (__ \ "reportingATransfer").read[Boolean] and
      (__ \ "dateOfLastSubscription").read[LocalDate] and
      (__ \ "totalCurrentYearSubscriptionsToDate").read[BigDecimal] and
      (__ \ "marketValueOfAccount").read[BigDecimal] and
      (__ \ "accountNumberOfTransferringAccount").read[String] and
      (__ \ "amountTransferred").read[BigDecimal] and
      (__ \ "flexibleIsa").read[Boolean]
  )(StandardIsaTransfer.apply _)

  implicit val format: OFormat[StandardIsaTransfer] = Json.format[StandardIsaTransfer]
}
