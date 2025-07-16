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

import play.api.Logging
import uk.gov.hmrc.http.UpstreamErrorResponse

object UpstreamErrorMapper extends Logging {

  def mapToErrorResponse(err: UpstreamErrorResponse): ErrorResponse = {
    logger.warn(s"Received upstream error: status=${err.statusCode}, message='${err.message}'")
    err match {
      case UpstreamErrorResponse(_, 401, _, _) =>
        logger.info("Mapping 401 to Unauthorised")
        Unauthorised
      case UpstreamErrorResponse(_, 500, _, _) | UpstreamErrorResponse(_, 502, _, _) | UpstreamErrorResponse(_, 503, _, _) =>
        logger.error(s"Mapping ${err.statusCode} to InternalServerError")
        InternalServerErr

      case UpstreamErrorResponse(_, statusCode, _, _) if statusCode >= 400 =>
        logger.error(s"Unhandled upstream error with status=$statusCode, mapping to InternalServerError")
        InternalServerErr
      //???
    }
  }

}
