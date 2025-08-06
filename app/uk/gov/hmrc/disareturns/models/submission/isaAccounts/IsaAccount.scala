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
    val reportingATransferPath = (__ \ "reportingATransfer").read[Boolean].reads(json)

    val reasonForClosurePath        = (__ \ "reasonForClosure").read[JsValue].reads(json).asOpt
    val lisaBonusClaimPath          = (__ \ "lisaBonusClaim").read[JsValue].reads(json).asOpt
    val flexibleIsaPath             = (__ \ "flexibleIsa").read[JsValue].reads(json).asOpt
    val closureDatePath             = (__ \ "closureDate").read[JsValue].reads(json).asOpt
    val dateOfFirstSubscriptionPath = (__ \ "dateOfFirstSubscription").read[JsValue].reads(json).asOpt
    val lisaQualifyingAdditionPath  = (__ \ "lisaQualifyingAddition").read[JsValue].reads(json).asOpt

    reportingATransferPath.flatMap {
      case true =>
        (reasonForClosurePath, lisaBonusClaimPath, flexibleIsaPath) match {
          case (Some(reasonForClosure), Some(lisaBonusClaim), None) =>
            println(Console.YELLOW + "LifetimeIsaTransferAndClosure" + Console.RESET)
            json.validate[LifetimeIsaTransferAndClosure]
          case _ =>
            (reasonForClosurePath, lisaBonusClaimPath, flexibleIsaPath, closureDatePath, lisaQualifyingAdditionPath) match {
              case (_, Some(lisaBonusClaim), None, Some(closureDatePath), _) =>
                println(Console.YELLOW + "LifetimeIsaTransferAndClosure" + Console.RESET)
                json.validate[LifetimeIsaTransferAndClosure]
              case (None, Some(lisaBonusClaimPath), None, None, _) =>
                println(Console.YELLOW + "LifetimeIsaTransfer" + Console.RESET)
                json.validate[LifetimeIsaTransfer]
              case (None, _, None, None, Some(lisaQualifyingAddition)) =>
                println(Console.YELLOW + "LifetimeIsaTransfer" + Console.RESET)
                json.validate[LifetimeIsaTransfer]
              case _ =>
                println(Console.YELLOW + "StandardIsaTransfer" + Console.RESET)
                json.validate[StandardIsaTransfer]
            }
        }
      case false =>
        (reasonForClosurePath, lisaBonusClaimPath, flexibleIsaPath) match {
          case (Some(reasonForClosure), Some(lisaBonusClaim), None) =>
            println(Console.YELLOW + "LifetimeIsaClosure" + Console.RESET)
            json.validate[LifetimeIsaClosure]
          case _ =>
            (reasonForClosurePath, lisaBonusClaimPath, flexibleIsaPath, closureDatePath, dateOfFirstSubscriptionPath) match {
              case (_, Some(lisaBonusClaim), None, Some(closureDatePath), Some(dateOfFirstSubscriptionPath)) =>
                println(Console.YELLOW + "LifetimeIsaClosure" + Console.RESET)
                json.validate[LifetimeIsaClosure]
              case (None, None, _, None, None) =>
                println(Console.YELLOW + "StandardIsaNewSubscription" + Console.RESET)
                json.validate[StandardIsaNewSubscription]
              case _ =>
                json.validate[LifetimeIsaNewSubscription]
            }
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

  implicit val format: Format[IsaAccount] = Format(isaAccountReads, writes)
}
