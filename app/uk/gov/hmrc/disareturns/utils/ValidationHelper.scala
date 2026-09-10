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

import cats.data.ValidatedNel
import cats.syntax.all.*
import play.api.Logging
import uk.gov.hmrc.disareturns.models.common.*
import scala.util.Try

object ValidationHelper extends Logging {

  def validateParams(zReference: String): Either[ErrorResponse, (String, Option[Int])] =
    validateZReference(zReference).map(_ -> None)

  def validatePaginationParams(
    zReference:   String,
    limit:        Option[String],
    defaultLimit: Int,
    maxLimit:     Int
  ): Either[ErrorResponse, (String, Int)] = {

    val zRefValidated = validateZReference(zReference).toValidatedNel

    val limitValidated: ValidatedNel[ErrorResponse, Int] =
      limit match {
        case None => defaultLimit.validNel
        case Some(value) =>
          Try(value.toInt).toOption
            .filter(value => value > 0 && value <= maxLimit)
            .toValidNel(InvalidLimitErr)
      }

    val combined = (zRefValidated, limitValidated).mapN((zRef, validatedLimit) => (zRef, validatedLimit))

    combined.toEither.leftMap { nonEmptyList =>
      val errs = nonEmptyList.toList
      logger.warn(s"[ValidationHelper][validateParams] Failed path or query string parameter validation with errors: [$errs]")
      if (errs.size == 1) errs.head
      else MultipleErrorResponse(code = "BAD_REQUEST", errors = errs)
    }
  }

  private def validateZReference(zReference: String): Either[ErrorResponse, String] =
    if (ZReferenceValidator.isValid(zReference)) Right(zReference.toUpperCase)
    else Left(InvalidZReference)
}
