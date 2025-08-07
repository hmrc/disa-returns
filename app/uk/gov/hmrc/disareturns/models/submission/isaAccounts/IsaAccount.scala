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
    val closureDatePath        = (__ \ "closureDate").read[JsValue].reads(json).asOpt
    val amountTransferredPath  = (__ \ "amountTransferred").read[JsValue].reads(json).asOpt
    val isaTypePath            = (__ \ "isaType").read[IsaType].reads(json)
    val isaTypeOpt             = isaTypePath.asOpt
    val reasonForClosurePath   = (__ \ "reasonForClosure").read[JsValue].reads(json).asOpt

    def handleIsaTypeError: JsError = isaTypePath match {
      case JsError(errors) => JsError(errors)
      case _               => JsError("Unknown error")
    }

    reportingATransferPath.flatMap {
      case true =>
        isaTypeOpt match {
          case Some(isaType @ (IsaType.LIFETIME_CASH | IsaType.LIFETIME_STOCKS_AND_SHARES)) =>
            (closureDatePath, amountTransferredPath) match {
              case (Some(closureDate), Some(amountTransferredPath)) =>
                json.validate[LifetimeIsaTransferAndClosure]
              case _ =>
                reasonForClosurePath match {
                  case Some(reasonForClosure) =>
                    json.validate[LifetimeIsaTransferAndClosure]
                  case _ =>
                    json.validate[LifetimeIsaTransfer]
                }
            }
          case Some(isaType) =>
            json.validate[StandardIsaTransfer]
          case None =>
            handleIsaTypeError
        }

      case false =>
        isaTypeOpt match {
          case Some(isaType @ (IsaType.LIFETIME_CASH | IsaType.LIFETIME_STOCKS_AND_SHARES)) =>
            closureDatePath match {
              case Some(closureDate) =>
                json.validate[LifetimeIsaClosure]
              case _ =>
                reasonForClosurePath match {
                  case Some(reasonForClosure) =>
                    json.validate[LifetimeIsaClosure]
                  case _ =>
                    json.validate[LifetimeIsaNewSubscription]
                }
            }
          case Some(isaType) =>
            json.validate[StandardIsaNewSubscription]
          case None =>
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
