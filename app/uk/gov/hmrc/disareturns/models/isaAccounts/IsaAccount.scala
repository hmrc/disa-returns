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
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaType.{IsaType, LIFETIME}

import java.time.LocalDate

trait IsaAccount {
  def accountNumber:                       String
  def nino:                                String
  def firstName:                           String
  def middleName:                          Option[String]
  def lastName:                            String
  def dateOfBirth:                         LocalDate
  def isaType:                             IsaType
  def dateOfLastSubscription:              LocalDate
  def totalCurrentYearSubscriptionsToDate: BigDecimal
  def marketValueOfAccount:                BigDecimal
  def amountTransferredIn:                 BigDecimal
  def amountTransferredOut:                BigDecimal
}

object IsaAccount {

  implicit val isaAccountReads: Reads[IsaAccount] = Reads { json =>
    val closureDatePath      = (__ \ "closureDate").read[JsValue].reads(json).asOpt
    val isaTypePath          = (__ \ "isaType").read[IsaType].reads(json)
    val isaTypeOpt           = isaTypePath.asOpt
    val reasonForClosurePath = (__ \ "reasonForClosure").read[JsValue].reads(json).asOpt

    def handleIsaTypeError: JsError = isaTypePath match {
      case JsError(errors) => JsError(errors)
      case _               => JsError("Unknown error")
    }

    (isaTypeOpt, closureDatePath, reasonForClosurePath) match {
      case (Some(LIFETIME), None, None) =>
        json.validate[LifetimeIsaSubscription]
      case (Some(LIFETIME), _, _) =>
        json.validate[LifetimeIsaClosure]
      case (Some(_), None, None) =>
        json.validate[StandardIsaSubscription]
      case (Some(_), _, _) =>
        json.validate[StandardIsaClosure]
      case _ =>
        handleIsaTypeError
    }
  }

  implicit val writes: Writes[IsaAccount] = new Writes[IsaAccount] {
    def writes(report: IsaAccount): JsValue = report match {
      case lifetimeIsaSubscription: LifetimeIsaSubscription =>
        Json.toJson(lifetimeIsaSubscription)(LifetimeIsaSubscription.lifetimeIsaSubscriptionWrites)
      case lifetimeIsaClosure:      LifetimeIsaClosure => Json.toJson(lifetimeIsaClosure)(LifetimeIsaClosure.lifetimeIsaClosureWrites)
      case standardIsaSubscription: StandardIsaSubscription =>
        Json.toJson(standardIsaSubscription)(StandardIsaSubscription.standardIsaSubscriptionWrites)
      case standardIsaClosure: StandardIsaClosure => Json.toJson(standardIsaClosure)(StandardIsaClosure.standardIsaClosureWrites)
    }
  }

  implicit val format: Format[IsaAccount] = Format(isaAccountReads, writes)
}
