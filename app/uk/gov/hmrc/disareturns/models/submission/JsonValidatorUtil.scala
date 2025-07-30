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

package uk.gov.hmrc.disareturns.models.submission

import play.api.libs.json._
import uk.gov.hmrc.disareturns.models.common.ValidationFailureResponse

object JsonValidatorUtil {

  def validateJson[T](json: JsValue)(implicit reads: Reads[T]): Either[ValidationFailureResponse, T] =
    json.validate[T] match {
      case JsSuccess(value, _) =>
        Right(value)

      case JsError(errors) =>
        Left(ValidationFailureResponse.convertErrorToValidationFailureResponse(JsError(errors)))
    }
}