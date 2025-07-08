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

sealed trait SubmissionError {
  def message: String
}

case object ObligationClosed extends SubmissionError {
  val message: String = "Obligation closed"
}

case object ReportingWindowClosed extends SubmissionError {
  val message: String = "Reporting window has been closed"
}
case object Unauthorised extends SubmissionError {
  val message: String = "Not authorised to access this service"
}

case object InternalServerError extends SubmissionError {
  val message: String = "There has been an issue processing your request"
}

object SubmissionError {
  implicit val writes: Writes[SubmissionError] = Writes {
    case ObligationClosed =>
      Json.obj("code" -> "OBLIGATION_CLOSED", "message" -> ObligationClosed.message)
    case ReportingWindowClosed =>
      Json.obj("code" -> "REPORTING_WINDOW_CLOSED", "message" -> ReportingWindowClosed.message)
    case Unauthorised =>
      Json.obj("code" -> "UNAUTHORISED", "message" -> Unauthorised.message)
    case InternalServerError => Json.obj("code" -> "INTERNAL_SERVER_ERROR", "message" -> InternalServerError.message)
  }

}
