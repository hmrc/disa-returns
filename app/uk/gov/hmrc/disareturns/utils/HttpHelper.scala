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

package uk.gov.hmrc.disareturns.utils

import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results.{BadRequest, Forbidden, InternalServerError, NotFound, Unauthorized}
import uk.gov.hmrc.disareturns.models.common._

object HttpHelper {
  def toHttpError(error: ErrorResponse): Result = error match {
    case _: ReturnNotFoundErr     => NotFound(Json.toJson(error))
    case _: ReportPageNotFoundErr => NotFound(Json.toJson(error))
    case _: InternalServerErr     => InternalServerError(Json.toJson(error))
    case ReportNotFoundErr                        => NotFound(Json.toJson(error))
    case UnauthorisedErr                          => Unauthorized(Json.toJson(error))
    case InvalidPageErr                           => BadRequest(Json.toJson(error))
    case ObligationClosed                         => Forbidden(Json.toJson(error))
    case ReportingWindowClosed                    => Forbidden(Json.toJson(error))
    case MultipleErrorResponse("FORBIDDEN", _, _) => Forbidden(Json.toJson(error))
  }
}
