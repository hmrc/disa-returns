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
import uk.gov.hmrc.auth.core.AffinityGroup.Agent
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.{AgentInformation, Credentials, Retrieval, ~}
import uk.gov.hmrc.disareturns.models.common.{IdentifierRequest, InternalServerErr, UnauthorisedErr, UserDetails}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait AuthAction {
  def apply(zRef: String): ActionBuilder[IdentifierRequest, AnyContent]
}

abstract class BaseAuthAction(ac: AuthConnector, cc: ControllerComponents)(implicit ec: ExecutionContext) extends AuthAction with Logging {

  protected type UserDetailsRetrievals = Option[Credentials] ~ Option[CredentialRole] ~ Option[AffinityGroup] ~ AgentInformation ~ Option[String]

  protected val userDetailsRetrievals: Retrieval[UserDetailsRetrievals] =
    credentials and credentialRole and affinityGroup and agentInformation and groupIdentifier

  protected val auth: AuthorisedFunctions = new AuthorisedFunctions {
    override def authConnector: AuthConnector = ac
  }

  protected def authorise[A](zRef: String, request: Request[A], block: IdentifierRequest[A] => Future[Result])(implicit
    hc:                            HeaderCarrier
  ): Future[Result]

  protected def withUserDetails[A](zRef: String, request: Request[A], retrievals: UserDetailsRetrievals)(
    block:                               IdentifierRequest[A] => Future[Result]
  ): Future[Result] = {
    val maybeCredentials ~ maybeCredentialRole ~ maybeAffinityGroup ~ agentInfo ~ maybeGroupId = retrievals

    val maybeUserDetails: Option[UserDetails] = (maybeCredentials, maybeCredentialRole, maybeAffinityGroup, maybeGroupId) match {
      case (_, _, Some(Agent), Some(groupId)) =>
        Some(
          UserDetails.Agent(
            groupId = groupId,
            agentId = agentInfo.agentId.getOrElse(UserDetails.unknown),
            agentName = agentInfo.agentFriendlyName.getOrElse(UserDetails.unknown)
          )
        )
      case (Some(credentials), Some(role), Some(_), Some(groupId)) =>
        Some(UserDetails.IsaManager(groupId = groupId, credId = credentials.providerId, credentialRole = role.toString))
      case _ => None
    }

    maybeUserDetails.fold[Future[Result]](throw InternalError("User was missing audit identity details.")) { userDetails =>
      block(IdentifierRequest(request, zRef, userDetails))
    }
  }

  final override def apply(zRef: String): ActionBuilder[IdentifierRequest, AnyContent] =
    new ActionBuilder[IdentifierRequest, AnyContent] {

      override def parser:                     BodyParser[AnyContent] = cc.parsers.defaultBodyParser
      override protected def executionContext: ExecutionContext       = cc.executionContext

      override def invokeBlock[A](
        request: Request[A],
        block:   IdentifierRequest[A] => Future[Result]
      ): Future[Result] = {
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

        authorise(zRef, request, block) recover {
          case ex: AuthorisationException =>
            logger.warn(s"[AuthAction][invokeBlock] Authorization failed. Error: ${ex.reason}")
            Unauthorized(Json.toJson(UnauthorisedErr))

          case ex =>
            logger.warn(s"[AuthAction][invokeBlock] Auth request failed with unexpected exception: $ex")
            InternalServerError(Json.toJson(InternalServerErr()))
        }
      }
    }
}

@Singleton
class EnrolmentVerificationAuthAction @Inject() (ac: AuthConnector, cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends BaseAuthAction(ac, cc) {

  override protected def authorise[A](zRef: String, request: Request[A], block: IdentifierRequest[A] => Future[Result])(implicit
    hc:                                     HeaderCarrier
  ): Future[Result] =
    auth.authorised(Enrolment(enrolmentKey)).retrieve(authorisedEnrolments and userDetailsRetrievals) { case enrolments ~ retrievals =>
      val zRefMatchesEnrolment = enrolments
        .getEnrolment(enrolmentKey)
        .fold(false)(_.getIdentifier(identifierKey).exists(_.value == zRef))

      if (zRefMatchesEnrolment) withUserDetails(zRef, request, retrievals)(block)
      else throw InternalError("Z-Ref does not match enrolment.")
    }

  private val enrolmentKey  = "HMRC-DISA-ORG"
  private val identifierKey = "ZREF"
}

@Singleton
class AuthenticatedAuthAction @Inject() (ac: AuthConnector, cc: ControllerComponents)(implicit ec: ExecutionContext) extends BaseAuthAction(ac, cc) {

  override protected def authorise[A](zRef: String, request: Request[A], block: IdentifierRequest[A] => Future[Result])(implicit
    hc:                                     HeaderCarrier
  ): Future[Result] =
    auth.authorised().retrieve(userDetailsRetrievals)(retrievals => withUserDetails(zRef, request, retrievals)(block))
}
