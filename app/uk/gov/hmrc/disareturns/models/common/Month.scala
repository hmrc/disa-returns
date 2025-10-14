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

package uk.gov.hmrc.disareturns.models.common

import play.api.libs.json._

object Month extends Enumeration {

  type Month = Value

  val JAN: Value = Value("JAN")
  val FEB: Value = Value("FEB")
  val MAR: Value = Value("MAR")
  val APR: Value = Value("APR")
  val MAY: Value = Value("MAY")
  val JUN: Value = Value("JUN")
  val JUL: Value = Value("JUL")
  val AUG: Value = Value("AUG")
  val SEP: Value = Value("SEP")
  val OCT: Value = Value("OCT")
  val NOV: Value = Value("NOV")
  val DEC: Value = Value("DEC")

  implicit val format: Format[Month.Value] = JsonUtils.enumFormat(Month)

  def isValid(month: String): Boolean =
    Month.values.exists(_.toString == month)
}
