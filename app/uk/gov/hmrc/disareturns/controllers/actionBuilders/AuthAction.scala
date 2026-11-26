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
import play.api.mvc.Results.{Forbidden, InternalServerError, Unauthorized}
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.AffinityGroup.{Agent, Organisation}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.{AgentInformation, Credentials, ~}
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.config.Constants.{AgentEnrolmentKey, DelegatedAuthRule, EnrolmentKey, IdentifierKey}
import uk.gov.hmrc.disareturns.models.common.{AgentClientRelationshipNotFound, IdentifierRequest, InternalServerErr, UnauthorisedErr, UserDetails}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait AuthAction {
  def apply(zRef: String): ActionBuilder[IdentifierRequest, AnyContent]
}

abstract class BaseAuthAction(ac: AuthConnector, cc: ControllerComponents)(implicit ec: ExecutionContext) extends AuthAction with Logging {

  protected val auth: AuthorisedFunctions = new AuthorisedFunctions {
    override def authConnector: AuthConnector = ac
  }

  protected def authorise[A](zRef: String, request: Request[A], block: IdentifierRequest[A] => Future[Result])(implicit
    hc:                            HeaderCarrier
  ): Future[Result]

  protected def authenticateOrganisationUserWithRole[A](
    zRef:                String,
    request:             Request[A],
    credentials:         Credentials,
    groupId:             String,
    maybeCredentialRole: Option[CredentialRole]
  )(block:               IdentifierRequest[A] => Future[Result]): Future[Result] =
    maybeCredentialRole.fold {
      logger.warn("[AuthAction][authenticateOrganisationUserWithRole] User was missing credential role")
      rejectUnauthorised()
    } { role =>
      block(
        IdentifierRequest(
          request,
          zRef,
          UserDetails.IsaManager(groupId = groupId, credId = credentials.providerId, credentialRole = role.toString)
        )
      )
    }

  protected def agentDetails(groupId: String, agentInformation: AgentInformation): UserDetails.Agent =
    UserDetails.Agent(
      groupId = groupId,
      agentId = agentInformation.agentId.getOrElse(UserDetails.unknown),
      agentName = agentInformation.agentFriendlyName.getOrElse(UserDetails.unknown)
    )

  protected def rejectMissingAgentGroupId(): Future[Result] = {
    logger.warn("[AuthAction][authorise] Agent group ID is missing")
    rejectUnauthorised()
  }

  protected def rejectMissingAuditIdentity(): Future[Result] = {
    logger.warn("[AuthAction][authorise] User was missing audit identity details")
    rejectUnauthorised()
  }

  protected def rejectUnauthorised(): Future[Result] =
    Future.successful(Unauthorized(Json.toJson(UnauthorisedErr)))

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
class EnrolmentVerificationAuthAction @Inject() (ac: AuthConnector, cc: ControllerComponents, appConfig: AppConfig)(implicit ec: ExecutionContext)
    extends BaseAuthAction(ac, cc) {

  override protected def authorise[A](zRef: String, request: Request[A], block: IdentifierRequest[A] => Future[Result])(implicit
    hc:                                     HeaderCarrier
  ): Future[Result] =
    auth
      .authorised((Organisation and Enrolment(EnrolmentKey)) or (Agent and Enrolment(AgentEnrolmentKey)))
      .retrieve(authorisedEnrolments and credentials and credentialRole and affinityGroup and agentInformation and groupIdentifier) {
        case enrolments ~ maybeCredentials ~ maybeCredentialRole ~ maybeAffinityGroup ~ agentInfo ~ maybeGroupId =>
          (maybeCredentials, maybeAffinityGroup, maybeGroupId) match {
            case (_, Some(Agent), _) if !appConfig.agentAuthenticationEnabled =>
              rejectDisabledAgentAuthentication()
            case (_, Some(Agent), Some(groupId)) =>
              authenticateAgent(zRef, request, groupId, agentInfo)(block)
            case (_, Some(Agent), None) =>
              rejectMissingAgentGroupId()
            case (Some(credentials), Some(Organisation), Some(groupId)) =>
              authenticateOrganisationUser(zRef, request, enrolments, credentials, groupId, maybeCredentialRole)(block)
            case _ =>
              rejectMissingAuditIdentity()
          }
      }

  private def rejectDisabledAgentAuthentication(): Future[Result] = {
    logger.warn("[EnrolmentVerificationAuthAction][authorise] Agent authentication is disabled")
    rejectUnauthorised()
  }

  private def authenticateAgent[A](
    zRef:             String,
    request:          Request[A],
    groupId:          String,
    agentInformation: AgentInformation
  )(block:            IdentifierRequest[A] => Future[Result])(implicit hc: HeaderCarrier): Future[Result] =
    auth
      .authorised(
        Enrolment(EnrolmentKey)
          .withIdentifier(IdentifierKey, zRef)
          .withDelegatedAuthRule(DelegatedAuthRule)
      ) {
        block(IdentifierRequest(request, zRef, agentDetails(groupId, agentInformation)))
      }
      .recover { case _: InsufficientEnrolments =>
        logger.warn(s"[EnrolmentVerificationAuthAction][authenticateAgent] Agent does not have a delegated enrolment for Z-Ref [$zRef]")
        Forbidden(Json.toJson(AgentClientRelationshipNotFound(zRef)))
      }

  private def authenticateOrganisationUser[A](
    zRef:                String,
    request:             Request[A],
    enrolments:          Enrolments,
    credentials:         Credentials,
    groupId:             String,
    maybeCredentialRole: Option[CredentialRole]
  )(block:               IdentifierRequest[A] => Future[Result]): Future[Result] =
    enrolments
      .getEnrolment(EnrolmentKey)
      .flatMap(_.getIdentifier(IdentifierKey))
      .map(_.value)
      .filter(_ == zRef)
      .fold {
        logger.warn(
          s"[EnrolmentVerificationAuthAction][authenticateOrganisationUser] User does not have a $IdentifierKey identifier matching Z-Ref [$zRef]"
        )
        rejectUnauthorised()
      }(_ => authenticateOrganisationUserWithRole(zRef, request, credentials, groupId, maybeCredentialRole)(block))
}

@Singleton
class AuthenticatedAuthAction @Inject() (ac: AuthConnector, cc: ControllerComponents)(implicit ec: ExecutionContext) extends BaseAuthAction(ac, cc) {

  override protected def authorise[A](zRef: String, request: Request[A], block: IdentifierRequest[A] => Future[Result])(implicit
    hc:                                     HeaderCarrier
  ): Future[Result] =
    auth
      .authorised(Organisation or (Agent and Enrolment(AgentEnrolmentKey)))
      .retrieve(credentials and credentialRole and affinityGroup and agentInformation and groupIdentifier) {
        case maybeCredentials ~ maybeCredentialRole ~ maybeAffinityGroup ~ agentInfo ~ maybeGroupId =>
          (maybeCredentials, maybeAffinityGroup, maybeGroupId) match {
            case (_, Some(Agent), Some(groupId)) =>
              block(IdentifierRequest(request, zRef, agentDetails(groupId, agentInfo)))
            case (_, Some(Agent), None) =>
              rejectMissingAgentGroupId()
            case (Some(credentials), Some(Organisation), Some(groupId)) =>
              authenticateOrganisationUserWithRole(zRef, request, credentials, groupId, maybeCredentialRole)(block)
            case _ =>
              rejectMissingAuditIdentity()
          }
      }
}
