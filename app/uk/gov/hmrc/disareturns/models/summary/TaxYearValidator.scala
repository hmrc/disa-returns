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

package uk.gov.hmrc.disareturns.models.summary

import play.api.libs.json.{Json, OFormat}

import scala.util.matching.Regex

case class TaxYear(value: String)

object TaxYear {
  implicit val format: OFormat[TaxYear] = Json.format[TaxYear]
}

object TaxYearValidator {

  private val pattern: Regex = raw"^20(\d{2})-(\d{2})$$".r

  def isValid(ref: String): Boolean =
    ref match {
      case pattern(startYr, endYr) if endYr.toInt == startYr.toInt + 1 => true
      case _                                                           => false
    }
}
