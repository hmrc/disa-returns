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
import uk.gov.hmrc.disareturns.models.common._

import scala.collection.Seq

object JsonErrorMapper {

  private def splitCamelCase(fieldName: String): Array[String] =
    fieldName
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
      .replaceAll("([a-z\\d])([A-Z])", "$1_$2")
      .split("_")

  def toErrorCodePrefix(fieldName: String): String =
    splitCamelCase(fieldName).mkString("_").toUpperCase

  def humanizeFieldName(fieldName: String): String = {
    val parts = splitCamelCase(fieldName).map(_.toLowerCase)
    parts.head.capitalize + parts.tail.map(" " + _).mkString
  }

  private def buildFieldError(prefix: String, fieldName: String, errorMessage: String): (String, String) = {
    val code    = s"${prefix}_${toErrorCodePrefix(fieldName)}"
    val message = errorMessage
    (code, message)
  }

  def buildMissingFieldError(fieldName: String): (String, String) =
    buildFieldError("MISSING", fieldName, s"${humanizeFieldName(fieldName)} field is missing")
  def buildInvalidFieldError(fieldName: String): (String, String) =
    buildFieldError("INVALID", fieldName, s"${humanizeFieldName(fieldName)} is not formatted correctly")
  def buildDuplicateFieldError(fieldName: String): (String, String) =
    buildFieldError("DUPLICATE", fieldName, s"Duplicate ${humanizeFieldName(fieldName)} field detected in this record")

  def jsErrorToDomainError(
    jsErrors:      Seq[(JsPath, Seq[JsonValidationError])],
    nino:          String,
    accountNumber: String
  ): Seq[SecondLevelValidationError] = {

    def determineError(fieldName: String, messages: Seq[String]): (String, String) =
      messages
        .collectFirst {
          case msg if msg.startsWith("error.path.missing")   => buildMissingFieldError(fieldName)
          case msg if msg.startsWith("error.expected")       => buildInvalidFieldError(fieldName)
          case msg if msg.startsWith("error.duplicateField") => buildDuplicateFieldError(fieldName)
        }
        .getOrElse(("UNKNOWN_ERROR", s"Validation failed for field: $fieldName"))

    jsErrors.map { case (path, errors) =>
      val fieldName       = path.toString.stripPrefix("/").split("/").last
      val messages        = errors.flatMap(_.messages)
      val (code, message) = determineError(fieldName, messages)

      SecondLevelValidationError(
        code = code,
        message = message,
        nino = nino,
        accountNumber = accountNumber
      )
    }
  }

}
