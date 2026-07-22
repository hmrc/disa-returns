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

import play.api.Logging
import play.api.http.Status.*
import play.api.libs.json.Json
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InternalServerErr, MonthlyReturnNotSubmitted, UnauthorisedErr}
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.util.Try

object UpstreamErrorMapper extends Logging {

  def mapToErrorResponse(err: UpstreamErrorResponse): ErrorResponse = {
    logger.warn(s"Received upstream error: status=${err.statusCode}, message='${err.message}'")
    err match {
      case UpstreamErrorResponse(_, UNAUTHORIZED, _, _) =>
        logger.info("Mapping 401 to Unauthorised")
        UnauthorisedErr
      case UpstreamErrorResponse(message, UNPROCESSABLE_ENTITY, _, _) =>
        val code = extractCode(message)
        code match {
          case Some("NO_SUBMISSION_DATA") =>
            logger.warn("Mapping 422 (NO_SUBMISSION_DATA) to MonthlyReturnNotSubmitted")
            MonthlyReturnNotSubmitted
          case _ =>
            logger.error(s"Unhandled 422 error code: ${code.getOrElse("none")}, mapping to InternalServerError")
            InternalServerErr()
        }
      case UpstreamErrorResponse(_, INTERNAL_SERVER_ERROR, _, _) | UpstreamErrorResponse(_, BAD_GATEWAY, _, _) |
          UpstreamErrorResponse(_, SERVICE_UNAVAILABLE, _, _) =>
        logger.error(s"Mapping ${err.statusCode} to InternalServerError")
        InternalServerErr()

      case UpstreamErrorResponse(_, statusCode, _, _) if statusCode >= BAD_REQUEST =>
        logger.error(s"Unhandled upstream error with status=$statusCode, mapping to InternalServerError")
        InternalServerErr()

      case _ =>
        logger.error(s"Unhandled upstream error, mapping to InternalServerError")
        InternalServerErr()
    }
  }

  private def extractCode(message: String): Option[String] = {
    def parseCode(s: String) = Try(Json.parse(s)).toOption.flatMap(js => (js \ "code").asOpt[String])
    parseCode(message).orElse {
      val prefix = "Response body: '"
      val idx    = message.lastIndexOf(prefix)
      if (idx >= 0) parseCode(message.substring(idx + prefix.length).stripSuffix("'")) else None
    }
  }
}
