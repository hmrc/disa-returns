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

package uk.gov.hmrc.disareturns.models.helpers

import play.api.Logging
import uk.gov.hmrc.disareturns.models.common.Month.Month
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.summary.TaxYearValidator
import cats.data.ValidatedNel
import cats.syntax.all._

import scala.util.Try

object ValidationHelper extends Logging {

  def validateParams(
    zReference: String,
    year:       String,
    month:      String,
    pageIndex:  Option[String] = None
  ): Either[ErrorResponse, (String, String, Month, Option[Int])] = {

    val zRefValidated: ValidatedNel[ErrorResponse, String] =
      if (ZReferenceValidator.isValid(zReference)) zReference.toUpperCase.validNel
      else InvalidZReference.invalidNel

    val yearValidated: ValidatedNel[ErrorResponse, String] =
      if (TaxYearValidator.isValid(year)) year.validNel
      else InvalidTaxYear.invalidNel

    val monthValidated: ValidatedNel[ErrorResponse, Month] =
      if (Month.isValid(month)) Month.withName(month).validNel
      else InvalidMonth.invalidNel

    val pageValidated: ValidatedNel[ErrorResponse, Option[Int]] =
      pageIndex match {
        case None => None.validNel
        case Some(value) =>
          Try(value.toInt).toOption
            .filter(_ >= 0)
            .map(Some)
            .toValidNel(InvalidPageErr)
      }

    val combined = (zRefValidated, yearValidated, monthValidated, pageValidated).mapN((im, yr, mo, pg) => (im, yr, mo, pg))

    combined.toEither.leftMap { nonEmptyList =>
      val errs = nonEmptyList.toList
      logger.warn(s"Failed path or query string parameter validation with errors: [$errs]")
      if (errs.size == 1) errs.head
      else MultipleErrorResponse(code = "BAD_REQUEST", errors = errs)
    }
  }
}
