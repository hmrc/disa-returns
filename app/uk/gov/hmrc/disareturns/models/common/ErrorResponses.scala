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

import play.api.libs.json._

trait ErrorResponse {
  def code:    String
  def message: String
}

case class BadRequestErr(message: String) extends ErrorResponse {
  val code = "BAD_REQUEST"
}

case object ObligationClosed extends ErrorResponse {
  val code    = "OBLIGATION_CLOSED"
  val message = "Obligation closed"
}

case object ReportingWindowClosed extends ErrorResponse {
  val code    = "REPORTING_WINDOW_CLOSED"
  val message = "Reporting window has been closed"
}

case object Unauthorised extends ErrorResponse {
  val code    = "UNAUTHORISED"
  val message = "Not authorised to access this service"
}

case object InternalServerErr extends ErrorResponse {
  val code    = "INTERNAL_SERVER_ERROR"
  val message = "There has been an issue processing your request"
}
case object BadRequestInvalidIsaRefErr extends ErrorResponse {
  val code    = "BAD_REQUEST"
  val message = "ISA Manager Reference Number format is invalid"
}

case object BadRequestMissingHeaderErr extends ErrorResponse {
  val code    = "BAD_REQUEST"
  val message = "Missing required header: X-Client-ID"
}

object ErrorResponse {

  private val singletons: Map[String, ErrorResponse] = Map(
    ObligationClosed.code      -> ObligationClosed,
    ReportingWindowClosed.code -> ReportingWindowClosed,
    Unauthorised.code          -> Unauthorised,
    InternalServerErr.code     -> InternalServerErr
  )

  implicit val format: Format[ErrorResponse] = new Format[ErrorResponse] {
    override def reads(json: JsValue): JsResult[ErrorResponse] =
      (json \ "code").validate[String].flatMap {
        case "FORBIDDEN" =>
          Json.fromJson[MultipleErrorResponse](json)
        case code if (json \ "path").isDefined =>
          Json.fromJson[FieldValidationError](json)
        case code if singletons.contains(code) =>
          JsSuccess(singletons(code))
        case other =>
          JsError(s"Unknown error code: $other")
      }

    override def writes(errorResponse: ErrorResponse): JsValue = errorResponse match {
      case ObligationClosed =>
        Json.obj("code" -> ObligationClosed.code, "message" -> ObligationClosed.message)
      case ReportingWindowClosed =>
        Json.obj("code" -> ReportingWindowClosed.code, "message" -> ReportingWindowClosed.message)
      case Unauthorised =>
        Json.obj("code" -> Unauthorised.code, "message" -> Unauthorised.message)
      case InternalServerErr =>
        Json.obj("code" -> InternalServerErr.code, "message" -> InternalServerErr.message)
      case m: MultipleErrorResponse =>
        Json.toJson(m)(MultipleErrorResponse.format)
      case v: FieldValidationError =>
        Json.obj("code" -> v.code, "message" -> v.message, "path" -> v.path)
      case BadRequestInvalidIsaRefErr =>
        Json.obj("code" -> BadRequestInvalidIsaRefErr.code, "message" -> BadRequestInvalidIsaRefErr.message)
      case BadRequestMissingHeaderErr =>
        Json.obj("code" -> BadRequestMissingHeaderErr.code, "message" -> BadRequestMissingHeaderErr.message)
      case badRequestErr: BadRequestErr =>
        Json.obj("code" -> badRequestErr.code, "message" -> badRequestErr.message)
      case other =>
        Json.obj("code" -> "UNKNOWN", "message" -> "Unknown error response")
    }
  }
}

case class MultipleErrorResponse(
  code:    String = "FORBIDDEN",
  message: String = "Multiple issues found regarding your submission",
  errors:  Seq[ErrorResponse]
) extends ErrorResponse

object MultipleErrorResponse {
  implicit val format: OFormat[MultipleErrorResponse] = Json.format[MultipleErrorResponse]
}

case class ValidationFailureResponse(
  code:    String = "VALIDATION_FAILURE",
  message: String = "Bad request",
  errors:  Seq[FieldValidationError]
) extends ErrorResponse

object ValidationFailureResponse {
  implicit val format: OFormat[ValidationFailureResponse] = Json.format[ValidationFailureResponse]

  def convertErrors(jsError: JsError): ValidationFailureResponse = {
    def mapCode(message: String): String = message match {
      case "error.path.missing"             => "MISSING_FIELD"
      case m if m.contains("error.taxYear") => "INVALID_YEAR"
      case _                                => "VALIDATION_ERROR"
    }

    def formatPath(jsPath: JsPath): String = {
      val pathString = jsPath.path
        .map {
          case KeyPathNode(key) => s"/$key"
          case IdxPathNode(idx) => s"/$idx"
        }
        .mkString("")

      if (pathString.isEmpty) "/" else pathString
    }

    val fieldErrors: Seq[FieldValidationError] = jsError.errors.toSeq.flatMap { case (path, errs) =>
      errs.map { ve =>
        FieldValidationError(
          code = mapCode(ve.message),
          message = ve.message match {
            case "error.path.missing"              => "This field is required"
            case "error.taxYear.not.whole.integer" => "Tax year must be a valid whole number"
            case "error.taxYear.in.past"           => "Tax year cannot be in the past"
            case "error.taxYear.not.current"       => "Tax year must be the current tax year"
            case "error.taxYear.not.integer"       => "Tax year must be a number"
            case "error.min"                       => "This field must be greater than or equal to 0"
            case "error.expected.validenumvalue"   => "Invalid month provided"
            case "error.expected.enumstring"       => "Invalid month provided must be a string"
            case "error.expected.jsnumber"         => "This field must be greater than or equal to 0"
            case other                             => other
          },
          path = formatPath(path)
        )
      }
    }

    ValidationFailureResponse(errors = fieldErrors)
  }
}

case class FieldValidationError(
  code:    String,
  message: String,
  path:    String
) extends ErrorResponse

object FieldValidationError {
  implicit val format: OFormat[FieldValidationError] = Json.format[FieldValidationError]
}
