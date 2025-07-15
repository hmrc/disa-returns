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

package uk.gov.hmrc.disareturns.models.errors.connector.responses

import play.api.libs.json._

sealed trait ErrorResponse {
  def code: String
  def message: String
}

case object ObligationClosed extends ErrorResponse {
  //Check this
  val code = "RETURN_OBLIGATION_ALREADY_MET"
  val message = "Return obligation already met"
}

case object ReportingWindowClosed extends ErrorResponse {
  val code = "REPORTING_WINDOW_CLOSED"
  val message = "Reporting window has been closed"
}

case object Unauthorised extends ErrorResponse {
  val code = "UNAUTHORISED"
  val message = "Not authorised to access this service"
}

case object InternalServerError extends ErrorResponse {
  val code = "INTERNAL_SERVER_ERROR"
  val message = "There has been an issue processing your request"
}



object ErrorResponse {

  // This map links code -> instance
  private val singletons: Map[String, ErrorResponse] = Map(
    ObligationClosed.code -> ObligationClosed,
    ReportingWindowClosed.code -> ReportingWindowClosed,
    Unauthorised.code -> Unauthorised,
    InternalServerError.code -> InternalServerError
  )


  implicit val format: Format[ErrorResponse] = new Format[ErrorResponse] {
    override def reads(json: JsValue): JsResult[ErrorResponse] = {
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
    }

    override def writes(o: ErrorResponse): JsValue = o match {
      case singleton if singletons.values.toSet.contains(singleton) =>
        Json.obj("code" -> singleton.code, "message" -> singleton.message)

      case m: MultipleErrorResponse =>
        Json.toJson(m)(MultipleErrorResponse.format)

      case v: FieldValidationError =>
        Json.obj(
          "code" -> v.code,
          "message" -> v.message,
          "path" -> v.path
        )
    }
  }
}

case class MultipleErrorResponse(
                                  code: String = "FORBIDDEN",
                                  message: String = "Multiple issues found regarding your submission",
                                  errors: Seq[ErrorResponse]
                                ) extends ErrorResponse

object MultipleErrorResponse {
  implicit val format: OFormat[MultipleErrorResponse] = Json.format[MultipleErrorResponse]
}

case class ValidationFailureResponse(
                                      code: String = "VALIDATION_FAILURE",
                                      message: String = "Bad request",
                                      errors: Seq[FieldValidationError]
                                    ) extends ErrorResponse

object ValidationFailureResponse {
  implicit val format: OFormat[ValidationFailureResponse] = Json.format[ValidationFailureResponse]

  def convertErrors(jsError: JsError): ValidationFailureResponse = {
    def mapCode(message: String): String = message match {
      case "error.path.missing" => "MISSING_FIELD"
      case "INVALID_YEAR" => "INVALID_YEAR"
      case "INVALID_YEAR_FORMAT" => "INVALID_YEAR"
      case "MISSING_FIELD" => "MISSING_FIELD"
      case _ => "VALIDATION_ERROR"
    }

    // TODO: check if this is the best way, Tap had an example JsPath used somewhere
    def formatPath(jsPath: JsPath): String = {
      val pathString = jsPath.path.map {
        case KeyPathNode(key) => s"/$key"
        case IdxPathNode(idx) => s"/$idx"
      }.mkString("")

      if (pathString.isEmpty) "/" else pathString
    }

    val fieldErrors: Seq[FieldValidationError] = jsError.errors.toSeq.flatMap { case (path, errs) =>
      errs.map { ve =>
        FieldValidationError(
          code = mapCode(ve.message),
          message = ve.message match {
            case "error.path.missing" => "This field is required"
            case "INVALID_YEAR" => "Year is in the past"
            case "MISSING_FIELD" => "This field is required"
            case "error.min" => "This field must be greater than or equal to 0" //Review with team
            case "INVALID_YEAR" => "Year must be the current tax year" //Review with team
            case "error.expected.validenumvalue" => "Invalid month provided"
            case "error.expected.jsnumber" => "This field must be greater than or equal to 0"
            case other => other
          },
          path = formatPath(path)
        )
      }
    }

    ValidationFailureResponse(errors = fieldErrors)
  }
}


case class FieldValidationError(
                                 code: String,
                                 message: String,
                                 path: String
                               ) extends ErrorResponse


object FieldValidationError {
  implicit val format: OFormat[FieldValidationError] = Json.format[FieldValidationError]
}


