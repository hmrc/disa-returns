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

import uk.gov.hmrc.disareturns.models.common.Month.Month
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.summary.TaxYearValidator

object ValidationHelper {

  def validateParams(
    isaManagerReferenceNumber: String,
    year:                      String,
    month:                     String
  ): Either[ErrorResponse, (String, String, Month)] = {

    val errors: Seq[ErrorResponse] = Seq(
      Option.unless(IsaRefValidator.isValid(isaManagerReferenceNumber))(InvalidIsaManagerRef),
      Option.unless(TaxYearValidator.isValid(year))(InvalidTaxYear),
      Option.unless(Month.isValid(month))(InvalidMonth)
    ).flatten

    errors match {
      case Seq()            => Right((isaManagerReferenceNumber.toUpperCase, year, Month.withName(month)))
      case Seq(singleError) => Left(singleError)
      case multipleErrors   => Left(MultipleErrorResponse(code = "BAD_REQUEST", errors = multipleErrors))
    }
  }
}
