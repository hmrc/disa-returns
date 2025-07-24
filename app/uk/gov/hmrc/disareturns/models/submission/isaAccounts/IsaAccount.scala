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

trait IsaAccount {
  def accountNumber: String
  def nino: String
}

object IsaAccount {

  implicit val isaAccountReads: Reads[IsaAccount] = Reads { json =>
    (json \ "reportingATransfer").validate[Boolean].flatMap {
      case true =>
        // Check for distinguishing fields
        if ((json \ "lisaBonusClaim").isDefined) {
          json.validate[LifetimeIsaTransfer]
        } else if ((json \ "flexibleIsa").isDefined) {
          json.validate[StandardIsaTransfer]
        } else {
          JsError("Cannot determine IsaAccount subtype when reportingATransfer is true")
        }

      case false =>
        if ((json \ "lisaBonusClaim").isDefined) {
          json.validate[LifetimeIsaNewSubscription]
        } else if ((json \ "flexibleIsa").isDefined) {
          json.validate[StandardIsaNewSubscription]
        } else {
          JsError("Cannot determine IsaAccount subtype when reportingATransfer is true")
        }
    }
  }

  implicit val writes: Writes[IsaAccount] = new Writes[IsaAccount] {
    def writes(report: IsaAccount): JsValue = report match {
      case lns: LifetimeIsaNewSubscription      => Json.toJson(lns)(LifetimeIsaNewSubscription.format)
      case lt: LifetimeIsaTransfer             => Json.toJson(lt)(LifetimeIsaTransfer.format)
      case ltc: LifetimeIsaTransferAndClosure   => Json.toJson(ltc)(LifetimeIsaTransferAndClosure.format)
      case lc: LifetimeIsaClosure              => Json.toJson(lc)(LifetimeIsaClosure.format)
      case sns: StandardIsaNewSubscription      => Json.toJson(sns)(StandardIsaNewSubscription.format)
      case st: StandardIsaTransfer             => Json.toJson(st)(StandardIsaTransfer.format)
    }
  }
}