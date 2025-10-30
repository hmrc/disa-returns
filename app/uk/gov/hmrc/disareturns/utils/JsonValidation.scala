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

package uk.gov.hmrc.disareturns.utils

import play.api.libs.json._
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, NinoOrAccountNumInvalidErr, NinoOrAccountNumMissingErr}
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaType
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaType.IsaType

import java.time.LocalDate
import scala.math.BigDecimal.RoundingMode
import scala.util.{Success, Try}
import scala.util.matching.Regex

object JsonValidation {

  def firstLevelValidatorExtractNinoAndAccount(json: JsValue): Either[ErrorResponse, (String, String)] = {
    val ninoResult    = (json \ "nino").validate[String](nonEmptyStringReads)
    val accountResult = (json \ "accountNumber").validate[String](nonEmptyStringReads)

    (ninoResult, accountResult) match {
      case (JsSuccess(nino, _), JsSuccess(accountNumber, _)) =>
        Right((nino, accountNumber))
      case (JsError(_), _) | (_, JsError(_)) =>
        if ((json \ "nino").isInstanceOf[JsUndefined] || (json \ "accountNumber").isInstanceOf[JsUndefined])
          Left(NinoOrAccountNumMissingErr)
        else Left(NinoOrAccountNumInvalidErr)
    }
  }

  def findDuplicateFields(line: String): JsResult[Unit] = {
    val keys = "\"([^\"]+)\"\\s*:".r.findAllMatchIn(line).map(_.group(1)).toList
    keys.diff(keys.distinct).headOption match {
      case Some(dupKey) =>
        JsError(JsPath \ dupKey, JsonValidationError("error.duplicateField", s"Duplicate field detected: $dupKey"))
      case None =>
        JsSuccess(())
    }
  }

  val twoDecimalNum: Reads[BigDecimal] = Reads[BigDecimal] {
    case JsNumber(v) if v.scale == 2 => JsSuccess(v)
    case JsNumber(_)                 => JsError("error.expected.2decimal.nonnegative")
    case _                           => JsError("error.expected.number")
  }

  val twoDecimalNumNonNegative: Reads[BigDecimal] = Reads[BigDecimal] {
    case JsNumber(v) if v.scale == 2 && v >= 0 => JsSuccess(v)
    case JsNumber(_)                           => JsError("error.expected.2decimal.nonnegative")
    case _                                     => JsError("error.expected.number")
  }

  implicit val twoDecimalWrites: Writes[BigDecimal] =
    Writes(v => JsNumber(v.setScale(2, RoundingMode.UNNECESSARY)))

  implicit val strictLocalDateReads: Reads[LocalDate] = Reads {
    case JsString(str) =>
      Try(LocalDate.parse(str)).map(JsSuccess(_)).getOrElse(JsError("error.expected.date.iso"))
    case _ => JsError("error.expected.jsstring")
  }

  implicit val nonEmptyStringReads: Reads[String] = Reads[String] {
    case JsString(s) if s.trim.nonEmpty => JsSuccess(s)
    case JsString(_)                    => JsError("error.expected.jsstring")
    case _                              => JsError("error.expected.jsstring")
  }

  val lifetimeIsaTypeReads: Reads[IsaType] = Reads[IsaType] {
    case JsString(s) if s == IsaType.LIFETIME.toString =>
      JsSuccess(IsaType.LIFETIME)
    case JsString(_) =>
      JsError("error.expected.lifetime.isatype")
    case _ =>
      JsError("error.expected.lifetime.isatype")
  }

  val standardIsaTypeReads: Reads[IsaType] = Reads {
    case JsString(s) =>
      Try(IsaType.withName(s)) match {
        case Success(t) if t != IsaType.LIFETIME => JsSuccess(t)
        case _                                   => JsError("error.expected.standard.isatype")
      }
    case _ => JsError("error.expected.standard.isatype")
  }

  private val ninoRegex          = "^[A-CEGHJ-PR-TW-Z]{2}\\d{6}[A-D]$".r
  private val accountNumberRegex = "^[a-zA-Z0-9 :/-]{1,20}$".r //TODO: needs updating once regex is confirmed

  def stringPattern(pattern: Regex, errorMsg: String): Reads[String] =
    Reads[String] {
      case JsString(s) if pattern.matches(s) => JsSuccess(s)
      case JsString(_)                       => JsError(errorMsg)
      case _                                 => JsError("error.expected.string")
    }

  val ninoReads:          Reads[String] = stringPattern(ninoRegex, "INVALID_NINO")
  val accountNumberReads: Reads[String] = stringPattern(accountNumberRegex, "INVALID_ACCOUNT_NUMBER")

}
