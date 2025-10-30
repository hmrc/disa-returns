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

object ValidationHelper extends Logging {

  def validateParams(isaManagerReferenceNumber: String, year: String, month: String): Either[MultipleErrorResponse, (String, String, Month)] = {
    val errors: Seq[ErrorResponse] = List(
      Option.unless(IsaRefValidator.isValid(isaManagerReferenceNumber))(BadRequestErr("ISA Manager Reference Number format is invalid")),
      Option.unless(TaxYearValidator.isValid(year))(BadRequestErr("Invalid parameter for tax year")),
      Option.unless(Month.isValid(month))(BadRequestErr("Invalid parameter for month"))
    ).flatten
    if (errors.nonEmpty) {
      logger.warn(s"Failed path parameter validation with errors: [$errors]")
      Left(MultipleErrorResponse("BAD_REQUEST", "Issue(s) with your request", errors))
    } else Right((isaManagerReferenceNumber.toUpperCase, year, Month.withName(month)))
  }

}
