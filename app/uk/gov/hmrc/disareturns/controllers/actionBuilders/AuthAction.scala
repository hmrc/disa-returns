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
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.authorisedEnrolments
import uk.gov.hmrc.disareturns.models.common.{InternalServerErr, UnauthorisedErr}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthAction @Inject() (ac: AuthConnector, cc: ControllerComponents)(implicit val ec: ExecutionContext) {

  private val auth = new AuthorisedFunctions {
    override def authConnector: AuthConnector = ac
  }

  def apply(zRef: String): ActionBuilder[Request, AnyContent] =
    new ActionBuilder[Request, AnyContent] with Logging {

      override def parser:                     BodyParser[AnyContent] = cc.parsers.defaultBodyParser
      override protected def executionContext: ExecutionContext       = cc.executionContext

      override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        auth.authorised(Enrolment(enrolmentKey)).retrieve(authorisedEnrolments) { enrolments =>
          val zRefMatchesEnrolment = enrolments
            .getEnrolment(enrolmentKey)
            .fold(false)(_.getIdentifier(identifierKey).exists(_.value == zRef))

          if (zRefMatchesEnrolment) block(request)
          else throw InternalError("Z-Ref does not match enrolment.")
        } recover {
          case ex: AuthorisationException =>
            logger.warn(s"Authorization failed. Error: ${ex.reason}")
            Unauthorized(Json.toJson(UnauthorisedErr))

          case ex =>
            logger.warn(s"Auth request failed with unexpected exception: $ex")
            InternalServerError(Json.toJson(InternalServerErr()))
        }
      }
    }

  private val enrolmentKey  = "HMRC-DISA-ORG"
  private val identifierKey = "ZREF"
}
