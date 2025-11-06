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

import scala.util.Try

object ValidationHelper extends Logging {

  def validateParams(
    isaManagerReferenceNumber: String,
    year:                      String,
    month:                     String,
    pageIndex:                 Option[String] = None
  ): Either[ErrorResponse, (String, String, Month, Option[Int])] = {

    val imRefValidation: Either[ErrorResponse, String] =
      Either.cond(IsaRefValidator.isValid(isaManagerReferenceNumber), isaManagerReferenceNumber.toUpperCase, InvalidIsaManagerRef)

    val taxYearValidation: Either[ErrorResponse, String] =
      Either.cond(TaxYearValidator.isValid(year), year, InvalidTaxYear)

    val monthValidation: Either[ErrorResponse, Month] =
      Either.cond(Month.isValid(month), Month.withName(month), InvalidMonth)

    val pageValidation: Either[ErrorResponse, Option[Int]] = pageIndex match {
      case None => Right(None)
      case Some(value) =>
        Try(value.toInt).toOption
          .filter(_ >= 0)
          .map(p => Some(p))
          .toRight(InvalidPageErr)
    }

    val errors =
      List(imRefValidation.left.toOption, taxYearValidation.left.toOption, monthValidation.left.toOption, pageValidation.left.toOption).flatten

    if (errors.nonEmpty) {
      logger.warn(s"Failed path or query string parameter validation with errors: [$errors]")
    }
    errors match {
      case Nil =>
        for {
          imRef   <- imRefValidation
          taxYear <- taxYearValidation
          month   <- monthValidation
          page    <- pageValidation
        } yield (imRef, taxYear, month, page)
      case Seq(singleError) => Left(singleError)
      case multipleErrors   => Left(MultipleErrorResponse(code = "BAD_REQUEST", errors = multipleErrors))
    }
  }
}
