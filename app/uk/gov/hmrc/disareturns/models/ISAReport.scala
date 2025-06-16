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

package uk.gov.hmrc.disareturns.models

import play.api.libs.json._

// Handle Standards, Lifetime, and Transfer ISA types under a single type ISAReport
sealed trait ISAReport {
  def id: Int
  def isaManager: String
}

// Subtypes of ISAReport
case class StandardIsa(id: Int, isaManager: String, isaAmount: Int) extends ISAReport

object StandardIsa {
  implicit val format: OFormat[StandardIsa] = Json.format[StandardIsa]
}

case class LifetimeIsa(id: Int, isaManager: String, isEligibleForBonus: Boolean) extends ISAReport

object LifetimeIsa {
  implicit val format: OFormat[LifetimeIsa] = Json.format[LifetimeIsa]
}

case class StandardIsaTransfer(id: Int, isaManager: String, transferAmount: Int) extends ISAReport

object StandardIsaTransfer {
  implicit val format: OFormat[StandardIsaTransfer] = Json.format[StandardIsaTransfer]
}

object ISAReport {
  // Unified ISAReport JSON Reads (infers type by checking field presence)
  // can't match on subtype because not parse Json blob yet
  // maybe better way to do this
    implicit val reads: Reads[ISAReport] = new Reads[ISAReport] {
      def reads(json: JsValue): JsResult[ISAReport] = {
        if ((json \ "isaAmount").isDefined)
          json.validate[StandardIsa]
        else if ((json \ "isEligibleForBonus").isDefined)
          json.validate[LifetimeIsa]
        else if ((json \ "transferAmount").isDefined)
          json.validate[StandardIsaTransfer]
        else
          JsError("Unable to determine ISAReport type from fields")
      }
    }

    // Writes: based on runtime type
    implicit val writes: Writes[ISAReport] = new Writes[ISAReport] {
      def writes(report: ISAReport): JsValue = report match {
        case s: StandardIsa         => Json.toJson(s)(StandardIsa.format)
        case l: LifetimeIsa         => Json.toJson(l)(LifetimeIsa.format)
        case t: StandardIsaTransfer => Json.toJson(t)(StandardIsaTransfer.format)
      }
    }

    // Combined Format for convenience (optional)
    implicit val format: Format[ISAReport] = Format(reads, writes)
}
