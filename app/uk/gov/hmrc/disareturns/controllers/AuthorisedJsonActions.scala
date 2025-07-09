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

import org.apache.pekko.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.auth.core.AuthorisedFunctions
import uk.gov.hmrc.auth.core.authorise.{EmptyPredicate, Predicate}
import uk.gov.hmrc.disareturns.models.errors.connector.responses.ErrorResponse
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait AuthorisedJsonActions extends AuthorisedFunctions {

  /**
   * Handles service responses of type Future[Either[UpstreamErrorResponse, A]]
   * and maps errors into consistent JSON format.
   */
  def authorisedEitherAction[A](
                                 predicate: Predicate = EmptyPredicate
                               )(future: => Future[Either[UpstreamErrorResponse, A]])
                               (onSuccess: A => Result)
                               (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] = {

    authorised() {
      handleErrors(future)(onSuccess)
    }
  }

  /**
   * Generic error handler that maps UpstreamErrorResponse and other exceptions into Result.
   */
  private def handleErrors[A](
                               future: => Future[Either[UpstreamErrorResponse, A]]
                             )(onSuccess: A => Result)
                             (implicit ec: ExecutionContext): Future[Result] = {

    future.map {
      case Right(value) => onSuccess(value)
      case Left(error) =>
        error.statusCode match {
          case UNAUTHORIZED =>
            Unauthorized(Json.toJson(ErrorResponse("UNAUTHORISED", "Not authorised to access this service")))
          case FORBIDDEN =>
            Forbidden(Json.toJson(ErrorResponse("FORBIDDEN", "Access is forbidden")))
          case NOT_FOUND =>
            NotFound(Json.toJson(ErrorResponse("NOT_FOUND", "Resource not found")))
          case BAD_GATEWAY | SERVICE_UNAVAILABLE =>
            BadGateway(Json.toJson(ErrorResponse("DOWNSTREAM_ERROR", "Downstream service unavailable")))
          case _ =>
            InternalServerError(Json.toJson(ErrorResponse("INTERNAL_ERROR", s"Unexpected error: ${error.message}")))
        }
    }.recover {
      case NonFatal(e) =>
        InternalServerError(Json.toJson(ErrorResponse("UNEXPECTED_ERROR", e.getMessage)))
    }
  }
}
