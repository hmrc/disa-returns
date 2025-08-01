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

package uk.gov.hmrc.disareturns.models.submission.isaAccounts

import play.api.libs.json._
import uk.gov.hmrc.disareturns.models.submission.isaAccounts.IsaType.IsaType

import java.time.LocalDate

trait IsaAccount {
  def accountNumber:                       String
  def nino:                                String
  def firstName:                           String
  def middleName:                          Option[String]
  def lastName:                            String
  def dateOfBirth:                         LocalDate
  def isaType:                             IsaType
  def reportingATransfer:                  Boolean
  def dateOfLastSubscription:              LocalDate
  def totalCurrentYearSubscriptionsToDate: BigDecimal
  def marketValueOfAccount:                BigDecimal
}

object IsaAccount {

  implicit val isaAccountReads: Reads[IsaAccount] = Reads { json =>
    (__ \ "reportingATransfer").read[Boolean].reads(json).flatMap {
      case true =>
        ((json \ "reasonForClosure").isDefined, (json \ "lisaBonusClaim").isDefined, (json \ "flexibleIsa").isDefined) match {
          case (true, _, _)         => json.validate[LifetimeIsaTransferAndClosure]
          case (false, true, _)     => json.validate[LifetimeIsaTransfer]
          case (false, false, true) => json.validate[StandardIsaTransfer]
          case _                    => JsError("Cannot determine IsaAccount subtype when reportingATransfer is true")
        }

      case false =>
        ((json \ "reasonForClosure").isDefined, (json \ "flexibleIsa").isDefined, (json \ "lisaBonusClaim").isDefined) match {
          case (true, _, _)         => json.validate[LifetimeIsaClosure]
          case (false, true, _)     => json.validate[StandardIsaNewSubscription]
          case (false, false, true) => json.validate[LifetimeIsaNewSubscription]
          case _                    => JsError("Cannot determine IsaAccount subtype when reportingATransfer is false")
        }
    }
  }

  implicit val writes: Writes[IsaAccount] = new Writes[IsaAccount] {
    def writes(report: IsaAccount): JsValue = report match {
      case lns: LifetimeIsaNewSubscription    => Json.toJson(lns)(LifetimeIsaNewSubscription.format)
      case lt:  LifetimeIsaTransfer           => Json.toJson(lt)(LifetimeIsaTransfer.format)
      case ltc: LifetimeIsaTransferAndClosure => Json.toJson(ltc)(LifetimeIsaTransferAndClosure.format)
      case lc:  LifetimeIsaClosure            => Json.toJson(lc)(LifetimeIsaClosure.format)
      case sns: StandardIsaNewSubscription    => Json.toJson(sns)(StandardIsaNewSubscription.format)
      case st:  StandardIsaTransfer           => Json.toJson(st)(StandardIsaTransfer.format)
    }
  }
}
