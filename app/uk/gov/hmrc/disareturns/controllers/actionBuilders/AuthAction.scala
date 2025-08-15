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

package uk.gov.hmrc.disareturns.controllers.actionBuilders

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.Results.{InternalServerError, Unauthorized}
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InternalServerErr, UnauthorisedErr}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthAction @Inject() (ac: AuthConnector)(implicit val executionContext: ExecutionContext)
    extends ActionBuilder[Request, AnyContent]
    with Logging {

  private val auth = new AuthorisedFunctions {
    override def authConnector: AuthConnector = ac
  }

  override def parser: BodyParser[AnyContent] = parser

  override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    auth.authorised() {
      block(request)
    } recover {
      case ex: AuthorisationException =>
        logger.warn(s"Authorization failed. Bearer token sent: ${hc.authorization}")
        Unauthorized(Json.toJson(UnauthorisedErr(message = ex.reason): ErrorResponse))

      case ex =>
        logger.warn(s"Auth request failed with unexpected exception: $ex")
        InternalServerError(Json.toJson(InternalServerErr: ErrorResponse))
    }
  }
}
