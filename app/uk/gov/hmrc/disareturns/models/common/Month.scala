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

  val JAN: Value = Value(1, "JAN")
  val FEB: Value = Value(2, "FEB")
  val MAR: Value = Value(3, "MAR")
  val APR: Value = Value(4, "APR")
  val MAY: Value = Value(5, "MAY")
  val JUN: Value = Value(6, "JUN")
  val JUL: Value = Value(7, "JUL")
  val AUG: Value = Value(8, "AUG")
  val SEP: Value = Value(9, "SEP")
  val OCT: Value = Value(10, "OCT")
  val NOV: Value = Value(11, "NOV")
  val DEC: Value = Value(12, "DEC")

  implicit val format: Format[Month.Value] = JsonUtils.enumFormat(Month)

  def isValid(month: String): Boolean =
    Month.values.exists(_.toString == month)
}
