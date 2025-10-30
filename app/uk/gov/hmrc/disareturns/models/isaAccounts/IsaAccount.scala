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

  implicit val reads: Reads[IsaAccount] = Reads { json =>
    val closureDatePath      = (__ \ "closureDate").read[JsValue].reads(json).asOpt
    val lisaBonusClaimPath   = (__ \ "lisaBonusClaim").read[JsValue].reads(json).asOpt
    val flexibleIsaPath      = (__ \ "flexibleIsa").read[JsValue].reads(json).asOpt
    val reasonForClosurePath = (__ \ "reasonForClosure").read[JsValue].reads(json).asOpt
    val isaTypePath          = (__ \ "isaType").read[IsaType].reads(json)
    val isaTypeOpt           = isaTypePath.asOpt

    def handleIsaTypeError: JsError = isaTypePath match {
      case JsError(errors) => JsError(errors)
      case _               => JsError((__ \ "isaType"), JsonValidationError("error.expected.lifetime.isatype", "Isa type is not formatted correctly"))

    }

    def validateByClosureReasonAndDate(subscriptionReads: Reads[_ <: IsaAccount], closureReads: Reads[_ <: IsaAccount]): JsResult[IsaAccount] =
      (closureDatePath, reasonForClosurePath) match {
        case (None, None) => json.validate(subscriptionReads)
        case _            => json.validate(closureReads)
      }

    (isaTypeOpt, lisaBonusClaimPath, flexibleIsaPath) match {
      case (Some(LIFETIME), _, None) | (_, Some(_), None) =>
        validateByClosureReasonAndDate(LifetimeIsaSubscription.reads, LifetimeIsaClosure.reads)

      case (Some(isaType), None, _) if isaType != LIFETIME =>
        validateByClosureReasonAndDate(StandardIsaSubscription.reads, StandardIsaClosure.reads)

      case _ => handleIsaTypeError
    }
  }

  implicit val writes: Writes[IsaAccount] = new Writes[IsaAccount] {
    def writes(report: IsaAccount): JsValue = report match {
      case lis: LifetimeIsaSubscription =>
        Json.toJson(lis)(LifetimeIsaSubscription.writes)
      case lic: LifetimeIsaClosure => Json.toJson(lic)(LifetimeIsaClosure.writes)
      case sis: StandardIsaSubscription =>
        Json.toJson(sis)(StandardIsaSubscription.writes)
      case sic: StandardIsaClosure => Json.toJson(sic)(StandardIsaClosure.writes)
    }
  }

  implicit val format: Format[IsaAccount] = Format(reads, writes)
}
