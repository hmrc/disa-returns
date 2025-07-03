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

sealed trait Month {
  def name: String
}

object Month {
  case object JAN extends Month { val name = "JAN" }
  case object FEB extends Month { val name = "FEB" }
  case object MAR extends Month { val name = "MAR" }
  case object APR extends Month { val name = "APR" }
  case object MAY extends Month { val name = "MAY" }
  case object JUN extends Month { val name = "JUN" }
  case object JUL extends Month { val name = "JUL" }
  case object AUG extends Month { val name = "AUG" }
  case object SEP extends Month { val name = "SEP" }
  case object OCT extends Month { val name = "OCT" }
  case object NOV extends Month { val name = "NOV" }
  case object DEC extends Month { val name = "DEC" }

  private val values: Seq[Month] = Seq(
    JAN,
    FEB,
    MAR,
    APR,
    MAY,
    JUN,
    JUL,
    AUG,
    SEP,
    OCT,
    NOV,
    DEC
  )

  private def fromString(value: String): Option[Month] =
    values.find(_.name.equalsIgnoreCase(value))

  implicit val monthReads: Reads[Month] = Reads {
    case JsString(value) =>
      fromString(value) match {
        case Some(m) => JsSuccess(m)
        case None    => JsError(s"Invalid month: $value")
      }
    case _ => JsError("month must be a string")
  }

  implicit val monthWrites: Writes[Month] = Writes { month =>
    JsString(month.name)
  }
}
