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

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class SubmissionRequest(
  totalRecords:     Int,
  submissionPeriod: Month,
  taxYear:          TaxYear
)

object SubmissionRequest {
  implicit val reads: Reads[SubmissionRequest] = (
    (__ \ "totalRecords").read[Int](Reads.min(0)) and
      (__ \ "submissionPeriod").read[Month] and
      (__ \ "taxYear").read[TaxYear]
  )(SubmissionRequest.apply _)

  implicit val writes: OWrites[SubmissionRequest] = OWrites { request =>
    Json.obj(
      "totalRecords"     -> request.totalRecords,
      "submissionPeriod" -> request.submissionPeriod,
      "taxYear"          -> request.taxYear
    )
  }
}
