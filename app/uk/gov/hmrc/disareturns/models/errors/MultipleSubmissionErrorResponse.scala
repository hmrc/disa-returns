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

package uk.gov.hmrc.disareturns.models.errors

import play.api.libs.json._

case class SubmissionErrorDetail(code: String, message: String)
object SubmissionErrorDetail {
  implicit val writes: Writes[SubmissionErrorDetail] = Json.writes[SubmissionErrorDetail]
}

case class MultipleSubmissionErrorResponse(
  code:    String,
  message: String,
  errors:  Seq[SubmissionErrorDetail]
)

object MultipleSubmissionErrorResponse {
  implicit val writes: Writes[MultipleSubmissionErrorResponse] = Json.writes[MultipleSubmissionErrorResponse]

  def toErrorDetail(error: SubmissionError): SubmissionErrorDetail = error match {
    case ObligationClosed =>
      SubmissionErrorDetail("OBLIGATION_CLOSED", ObligationClosed.message)
    case ReportingWindowClosed =>
      SubmissionErrorDetail("REPORTING_WINDOW_CLOSED", ReportingWindowClosed.message)
    case Unauthorised =>
      SubmissionErrorDetail("UNAUTHORISED", Unauthorised.message)
    case InternalServerError =>
      SubmissionErrorDetail("INTERNAL_SERVER_ERROR", InternalServerError.message)
  }
}
