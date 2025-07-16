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

package uk.gov.hmrc.disareturns.controllers

import play.api.libs.json.Json
import play.api.mvc.Results.BadRequest
import play.api.mvc.{RequestHeader, Result}
import uk.gov.hmrc.auth.core.AuthorisedFunctions
import uk.gov.hmrc.disareturns.models.errors.connector.responses.{BadRequestInvalidIsaRefErr, BadRequestMissingHeaderErr, ErrorResponse}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait HeaderCheckedAuthorisedFunctions extends AuthorisedFunctions {

  private val ClientIdHeader = "X-Client-ID"

  def authorisedWithClientIdCheck(
    isaManagerReferenceNumber: String
  )(body:                      String => Future[Result])(implicit hc: HeaderCarrier, request: RequestHeader, ec: ExecutionContext): Future[Result] = {
    val isaRefRegex = "^Z([0-9]{4}|[0-9]{6})$".r
    val isaRefChecker: Boolean = isaRefRegex.pattern.matcher(isaManagerReferenceNumber).matches()

    request.headers.get(ClientIdHeader) match {
      case Some(clientId) =>
        if (isaRefChecker) {
          authorised()(body(clientId))
        } else {
          Future.successful(BadRequest(Json.toJson(BadRequestInvalidIsaRefErr: ErrorResponse)))
        }
      case None =>
        Future.successful(BadRequest(Json.toJson(BadRequestMissingHeaderErr: ErrorResponse)))
    }
  }
}
