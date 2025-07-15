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

package uk.gov.hmrc.disareturns.models.initiate.inboundRequest

import play.api.libs.json._

import java.time.LocalDate

final case class TaxYear(endYear: Int) extends AnyVal {

  override def toString: String = endYear.toString
}

object TaxYear {
  private def now: LocalDate = LocalDate.now()

  private def currentTaxYear: Int = {
    val cutoff = LocalDate.of(now.getYear, 4, 6) // new tax year starts on April 6
    if (now.isBefore(cutoff)) now.getYear - 1 else now.getYear
  }

  implicit val reads: Reads[TaxYear] = Reads {
    case JsNumber(num) =>
      if (!num.isValidInt) {
        JsError(JsonValidationError("error.taxYear.not.whole.integer"))
      } else {
        val year = num.toInt
        if (year < currentTaxYear) {
          JsError(JsonValidationError("error.taxYear.in.past"))
        } else if (year > currentTaxYear) {
          JsError(JsonValidationError("error.taxYear.not.current"))
        } else {
          JsSuccess(TaxYear(year))
        }
      }
    case _ =>
      JsError(JsonValidationError("error.taxYear.not.integer"))
  }

  implicit val writes: Writes[TaxYear] = Writes(t => JsNumber(t.endYear))
}
