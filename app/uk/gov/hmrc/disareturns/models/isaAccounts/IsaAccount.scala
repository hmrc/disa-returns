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

import play.api.libs.json._
import IsaType.IsaType

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
    val closureDatePath        = (__ \ "closureDate").read[JsValue].reads(json).asOpt
    val amountTransferredPath  = (__ \ "amountTransferred").read[JsValue].reads(json).asOpt
    val isaTypePath            = (__ \ "isaType").read[IsaType].reads(json)
    val isaTypeOpt             = isaTypePath.asOpt
    val reasonForClosurePath   = (__ \ "reasonForClosure").read[JsValue].reads(json).asOpt

    def handleIsaTypeError: JsError = isaTypePath match {
      case JsError(errors) => JsError(errors)
      case _               => JsError("Unknown error")
    }
    def isLifetimeIsaType(opt: Option[IsaType]): Boolean = opt match {
      case Some(IsaType.LIFETIME_CASH | IsaType.LIFETIME_STOCKS_AND_SHARES) => true
      case _                                                                => false
    }

    reportingATransferPath.flatMap { reportingATransfer =>
      (reportingATransfer, isaTypeOpt, closureDatePath, amountTransferredPath, reasonForClosurePath) match {
        case (true, isaTypeOpt, Some(_), Some(_), _) if isLifetimeIsaType(isaTypeOpt) =>
          json.validate[LifetimeIsaTransferAndClosure]

        case (true, isaTypeOpt, _, _, Some(_)) if isLifetimeIsaType(isaTypeOpt) =>
          json.validate[LifetimeIsaTransferAndClosure]

        case (true, isaTypeOpt, _, _, _) if isLifetimeIsaType(isaTypeOpt) =>
          json.validate[LifetimeIsaTransfer]

        case (true, Some(_), _, _, _) =>
          json.validate[StandardIsaTransfer]

        case (false, isaTypeOpt, Some(_), _, _) if isLifetimeIsaType(isaTypeOpt) =>
          json.validate[LifetimeIsaClosure]

        case (false, isaTypeOpt, _, _, Some(_)) if isLifetimeIsaType(isaTypeOpt) =>
          json.validate[LifetimeIsaClosure]

        case (false, isaTypeOpt, _, _, _) if isLifetimeIsaType(isaTypeOpt) =>
          json.validate[LifetimeIsaNewSubscription]

        case (false, Some(_), _, _, _) =>
          json.validate[StandardIsaNewSubscription]

        case (_, None, _, _, _) =>
          handleIsaTypeError
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
