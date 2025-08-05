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
import uk.gov.hmrc.disareturns.models.submission.isaAccounts.ReasonForClosure.ReasonForClosure

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
          case (true, true, false)  => json.validate[LifetimeIsaTransferAndClosure]
          case (false, true, false) => json.validate[LifetimeIsaTransfer]
          case (false, false, true) => json.validate[StandardIsaTransfer]
          case _                    => JsError("Cannot determine IsaAccount subtype when reportingATransfer is true")
        }
      case false =>
        ((json \ "reasonForClosure").isDefined, (json \ "flexibleIsa").isDefined, (json \ "lisaBonusClaim").isDefined) match {
          case (true, false, true)  => json.validate[LifetimeIsaClosure]
          case (false, true, false) => json.validate[StandardIsaNewSubscription]
          case (false, false, true) => json.validate[LifetimeIsaNewSubscription]
          case _                    => JsError("Cannot determine IsaAccount subtype when reportingATransfer is false")
        }
    }
  }

//  implicit val isaAccountReads: Reads[IsaAccount] = Reads { json =>
//    (__ \ "reportingATransfer").read[Boolean].reads(json).flatMap {
//      case true =>
//        val reasonR = (__ \ "reasonForClosure").read[ReasonForClosure].reads(json)
//        val bonusR  = (__ \ "lisaBonusClaim").read[BigDecimal].reads(json)
//        val flexR   = (__ \ "flexibleIsa").read[Boolean].reads(json)
//
//        (reasonR, bonusR, flexR) match {
//          case (JsSuccess(_, _), JsSuccess(_, _), JsError(_)) =>
//            json.validate[LifetimeIsaTransferAndClosure]
//          case (JsError(_), JsSuccess(_, _), JsError(_)) =>
//            json.validate[LifetimeIsaTransfer]
//          case (JsError(_), JsError(_), JsSuccess(_, _)) =>
//            json.validate[StandardIsaTransfer]
//          case _ =>
//            // Accumulate all errors if any
//            val allErrors = reasonR.asEither.left.getOrElse(Nil) ++
//              bonusR.asEither.left.getOrElse(Nil) ++
//              flexR.asEither.left.getOrElse(Nil)
//            JsError(allErrors)
//        }
//
//      case false =>
//        val reasonR = (__ \ "reasonForClosure").read[ReasonForClosure].reads(json)
//        val bonusR  = (__ \ "lisaBonusClaim").read[BigDecimal].reads(json)
//        val flexR   = (__ \ "flexibleIsa").read[Boolean].reads(json)
//
//        (reasonR, bonusR, flexR) match {
//          case (JsSuccess(_, _), JsSuccess(_, _), JsError(_)) =>
//            json.validate[LifetimeIsaClosure]
//          case (JsError(_), JsSuccess(_, _), JsError(_)) =>
//            json.validate[LifetimeIsaNewSubscription]
//          case (JsError(_), JsError(_), JsSuccess(_, _)) =>
//            json.validate[StandardIsaNewSubscription]
//          case _ =>
//            val allErrors = reasonR.asEither.left.getOrElse(Nil) ++
//              bonusR.asEither.left.getOrElse(Nil) ++
//              flexR.asEither.left.getOrElse(Nil)
//            JsError(allErrors)
//        }
//    }
//  }

  //
//  implicit val isaAccountReads2: Reads[IsaAccount] = Reads { json =>
//    (json \ "reportingATransfer").validate[Boolean] match {
//      case JsSuccess(true, _) =>
//        // This is a transfer
//        LifetimeIsaTransferAndClosure.format
//          .reads(json)
//          .orElse(LifetimeIsaTransfer.format.reads(json))
//          .orElse(StandardIsaTransfer.format.reads(json))
//
//      case JsSuccess(false, _) =>
//        // This is NOT a transfer
//        LifetimeIsaClosure.format
//          .reads(json)
//          .orElse(StandardIsaNewSubscription.format.reads(json))
//          .orElse(LifetimeIsaNewSubscription.format.reads(json))
//
//      case JsError(_) =>
//        JsError(__ \ "reportingATransfer", "Missing or invalid reportingATransfer field")
//    }
//  }

  //  implicit val combinedRead: Reads[IsaAccount] = Reads { json =>
  //    LifetimeIsaTransferAndClosure.format
  //      .reads(json)
  //      .orElse(LifetimeIsaClosure.format.reads(json))
  //      .orElse(LifetimeIsaTransfer.format.reads(json))
  //      .orElse(LifetimeIsaNewSubscription.format.reads(json))
  //      .orElse(StandardIsaTransfer.format.reads(json))
  //      .orElse(StandardIsaNewSubscription.format.reads(json))
  //  }

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
