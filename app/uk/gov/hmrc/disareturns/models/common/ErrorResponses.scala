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

case class NotFoundErr(message: String = "Not found") extends ErrorResponse {
  val code = "NOT_FOUND"
}

case object UnauthorisedErr extends ErrorResponse {
  val code    = "UNAUTHORISED"
  val message = "Unauthorised"
}

case class InternalServerErr(
  message: String = "There has been an issue processing your request"
) extends ErrorResponse {
  val code = "INTERNAL_SERVER_ERROR"
}

case object ObligationClosed extends ErrorResponse {
  val code    = "OBLIGATION_CLOSED"
  val message = "Obligation closed"
}

case object ReportingWindowClosed extends ErrorResponse {
  val code    = "REPORTING_WINDOW_CLOSED"
  val message = "Reporting window has been closed"
}

case object NinoOrAccountNumMissingErr extends ErrorResponse {
  val code    = "NINO_OR_ACC_NUM_MISSING"
  val message = "All models sent must include an account number and nino in order to process correctly"
}

case object NinoOrAccountNumInvalidErr extends ErrorResponse {
  val code    = "NINO_OR_ACC_NUM_INVALID"
  val message = "All models sent must include a valid account number and nino in order to process correctly"
}

case object MalformedJsonFailureErr extends ErrorResponse {
  val code    = "MALFORMED JSON"
  val message = "One of the NDJson lines contains malformed JSON"
}

case object ReturnIdNotMatchedErr extends ErrorResponse {
  val code    = "RETURN_ID_NOT_FOUND"
  val message = "The provided returnId could not be found"
}

case object MismatchErr extends ErrorResponse {
  val code    = "MISMATCH_EXPECTED_VS_RECEIVED"
  val message = "Number of records declared in the header does not match the number submitted."
}

object ErrorResponse {

  implicit val badRequestErrReads: Reads[BadRequestErr] =
    (JsPath \ "message").read[String].map(BadRequestErr.apply)

  implicit val notFoundErrReads: Reads[NotFoundErr] =
    (JsPath \ "message").read[String].map(NotFoundErr.apply)

  implicit val internalServerErrReads: Reads[InternalServerErr] =
    (JsPath \ "message")
      .readWithDefault[String](
        "There has been an issue processing your request"
      )
      .map(InternalServerErr.apply)

  private val singletons: Map[String, ErrorResponse] = Map(
    ObligationClosed.code           -> ObligationClosed,
    ReportingWindowClosed.code      -> ReportingWindowClosed,
    UnauthorisedErr.code            -> UnauthorisedErr,
    NinoOrAccountNumMissingErr.code -> NinoOrAccountNumMissingErr,
    NinoOrAccountNumInvalidErr.code -> NinoOrAccountNumInvalidErr,
    ReturnIdNotMatchedErr.code      -> ReturnIdNotMatchedErr,
    MalformedJsonFailureErr.code    -> MalformedJsonFailureErr,
    MismatchErr.code                -> MismatchErr
  )

  implicit val format: Format[ErrorResponse] = new Format[ErrorResponse] {
    override def reads(json: JsValue): JsResult[ErrorResponse] =
      (json \ "code").validate[String].flatMap {
        case "FORBIDDEN" =>
          Json.fromJson[MultipleErrorResponse](json)
        case "VALIDATION_FAILURE" if (json \ "errors").validate[Seq[SecondLevelValidationError]].isSuccess =>
          json.validate[SecondLevelValidationResponse]
        case "BAD_REQUEST" =>
          badRequestErrReads.reads(json)
        case "NOT_FOUND" =>
          notFoundErrReads.reads(json)
        case "INTERNAL_SERVER_ERROR" =>
          internalServerErrReads.reads(json)
        case code if singletons.contains(code) =>
          JsSuccess(singletons(code))
        case other =>
          JsError(s"Unknown error code: $other")
      }

    override def writes(errorResponse: ErrorResponse): JsValue = errorResponse match {
      case m: MultipleErrorResponse =>
        Json.toJson(m)(MultipleErrorResponse.format)
      case v: FieldValidationError =>
        Json.obj("code" -> v.code, "message" -> v.message, "path" -> v.path)
      case error: ErrorResponse =>
        Json.obj("code" -> error.code, "message" -> error.message)
    }
  }

  implicit def subtypeWrites[A <: ErrorResponse]: Writes[A] =
    format.contramap[A](er => er: ErrorResponse)
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

  private def mapJsErrorToResponseCode(message: String): String = message match {
    case "error.path.missing"             => "MISSING_FIELD"
    case m if m.contains("error.taxYear") => "INVALID_YEAR"
    case _                                => "VALIDATION_ERROR"
  }

  private def formatFieldPath(jsPath: JsPath): String = {
    val pathString = jsPath.path
      .map {
        case KeyPathNode(key) => s"/$key"
        case IdxPathNode(idx) => s"/$idx"
      }
      .mkString("")

    if (pathString.isEmpty) "/" else pathString
  }

  def createFromJsError(jsError: JsError): ValidationFailureResponse = {

    val fieldErrors: Seq[FieldValidationError] = jsError.errors.toSeq.flatMap { case (path, errors) =>
      errors.map { validationError =>
        FieldValidationError(
          code = mapJsErrorToResponseCode(validationError.message),
          message = validationError.message match {
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
          path = formatFieldPath(path)
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

case class SecondLevelValidationResponse(
  code:    String = "VALIDATION_FAILURE",
  message: String = "One or more models failed validation",
  errors:  Seq[SecondLevelValidationError]
) extends ErrorResponse

object SecondLevelValidationResponse {
  implicit val format: OFormat[SecondLevelValidationResponse] = Json.format[SecondLevelValidationResponse]
}

case class SecondLevelValidationError(
  nino:          String,
  accountNumber: String,
  code:          String,
  message:       String
) extends ErrorResponse

object SecondLevelValidationError {
  implicit val format: OFormat[SecondLevelValidationError] = Json.format[SecondLevelValidationError]
}
