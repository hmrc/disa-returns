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

package models

import play.api.libs.json._
import uk.gov.hmrc.disareturns.models.common._
import utils.BaseUnitSpec

class ErrorResponseSpec extends BaseUnitSpec {

  "ErrorResponse format" should {

    "deserialize a known singleton error" in {
      val json = Json.obj(
        "code"    -> ObligationClosed.code,
        "message" -> ObligationClosed.message
      )

      val result = Json.fromJson[ErrorResponse](json)

      result shouldBe JsSuccess(ObligationClosed)
    }
    "deserialize a unauthorised error" in {
      val json = Json.obj(
        "code"    -> UnauthorisedErr.code,
        "message" -> UnauthorisedErr.message
      )

      val result = Json.fromJson[ErrorResponse](json)

      result shouldBe JsSuccess(UnauthorisedErr)
    }

    "deserialize a MultipleErrorResponse with code FORBIDDEN" in {
      val json = Json.obj(
        "code"    -> "FORBIDDEN",
        "message" -> "Multiple issues found regarding your submission",
        "errors" -> Json.arr(
          Json.obj("code" -> ObligationClosed.code, "message"      -> ObligationClosed.message),
          Json.obj("code" -> ReportingWindowClosed.code, "message" -> ReportingWindowClosed.message)
        )
      )

      val result = Json.fromJson[ErrorResponse](json)

      result.isSuccess shouldBe true
      val multipleError = result.get
      multipleError                                                 shouldBe a[MultipleErrorResponse]
      multipleError.code                                            shouldBe "FORBIDDEN"
      multipleError.asInstanceOf[MultipleErrorResponse].errors.head shouldBe ObligationClosed
      multipleError.asInstanceOf[MultipleErrorResponse].errors(1)   shouldBe ReportingWindowClosed
    }

    "fail to deserialize unknown error codes" in {
      val json = Json.obj(
        "code"    -> "UNKNOWN_CODE",
        "message" -> "Unknown error"
      )

      val result = Json.fromJson[ErrorResponse](json)

      result shouldBe a[JsError]
      val JsError(errors) = result
      errors.head._2.head.message should include("Unknown error code: UNKNOWN_CODE")
    }

    "serialize a known singleton error" in {
      val json = Json.toJson(ObligationClosed: ErrorResponse)
      json shouldBe Json.obj(
        "code"    -> ObligationClosed.code,
        "message" -> ObligationClosed.message
      )
    }

    "serialize a MultipleErrorResponse" in {
      val multipleError = MultipleErrorResponse(errors = Seq(ObligationClosed))
      val json          = Json.toJson(multipleError: ErrorResponse)

      (json \ "code").as[String] shouldBe "FORBIDDEN"
      (json \ "errors").as[Seq[JsValue]].head shouldBe Json.obj(
        "code"    -> ObligationClosed.code,
        "message" -> ObligationClosed.message
      )
    }

    "serialize a FieldValidationError" in {
      val fieldError = FieldValidationError("CODE", "message", "/some/path")
      val json       = Json.toJson(fieldError: ErrorResponse)

      json shouldBe Json.obj(
        "code"    -> "CODE",
        "message" -> "message",
        "path"    -> "/some/path"
      )
    }

    "serialise and deserialise InternalServerErr with default message" in {
      val err: ErrorResponse = InternalServerErr()
      val js = Json.toJson(err)

      (js \ "code").as[String]  shouldBe "INTERNAL_SERVER_ERROR"
      (js \ "message").as[String] should not be empty

      js.as[ErrorResponse] match {
        case i: InternalServerErr => i.message shouldBe err.message
        case other => fail(s"Expected InternalServerErr, got $other")
      }
    }

    "serialise and deserialise ReturnNotFoundErr with custom message" in {
      val err: ErrorResponse = ReturnNotFoundErr("not-found")
      val js = Json.toJson(err)

      (js \ "code").as[String]    shouldBe "RETURN_NOT_FOUND"
      (js \ "message").as[String] shouldBe "not-found"

      js.as[ErrorResponse] match {
        case i: ReturnNotFoundErr => i.message shouldBe err.message
        case other => fail(s"Expected ReturnNotFoundErr, got $other")
      }
    }
  }
}
