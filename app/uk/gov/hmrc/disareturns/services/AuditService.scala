/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.disareturns.services

import play.api.Logging
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.connectors.DisaAccountConnector
import uk.gov.hmrc.disareturns.models.common.{ReportingPeriod, UserDetails}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions
import uk.gov.hmrc.play.audit.http.connector.AuditResult.{Disabled, Failure, Success}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import java.time.format.DateTimeFormatter
import java.time.{Clock, ZoneOffset}
import java.util.Locale
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class AuditService @Inject() (
  connector:            AuditConnector,
  disaAccountConnector: DisaAccountConnector,
  appConfig:            AppConfig,
  clock:                Clock
)(implicit ec:          ExecutionContext)
    extends Logging {

  private val periodFormatter    = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.UK)
  private val timestampFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

  def auditApiUploadStarted(
    zReference:       String,
    userDetails:      UserDetails,
    reportingPeriod:  ReportingPeriod,
    internalReturnId: String
  )(implicit hc:      HeaderCarrier): Future[Unit] = {
    val uploadStartedTime = timestampFormatter.format(clock.instant())

    retrieveGroupName(zReference, AuditTypes.APIUploadStarted).flatMap { groupName =>
      val detail =
        Json.obj(
          EventData.internalReturnId  -> internalReturnId,
          EventData.uploadStartedTime -> uploadStartedTime,
          EventData.period            -> periodFormatter.format(reportingPeriod.yearMonth),
          EventData.groupId           -> userDetails.groupId
        ) ++
          groupName.fold(Json.obj())(name => Json.obj(EventData.groupName -> name)) ++
          Json.obj(
            EventData.zReference -> zReference,
            EventData.userType   -> userDetails.userType
          ) ++
          userDetail(userDetails)

      sendEvent(AuditTypes.APIUploadStarted, detail)
    }
  }

  private def sendEvent(auditType: String, detail: JsObject)(implicit hc: HeaderCarrier): Future[Unit] = {
    val event = ExtendedDataEvent(
      auditSource = appConfig.appName,
      auditType = auditType,
      tags = AuditExtensions.auditHeaderCarrier(hc).toAuditTags(),
      detail = detail
    )

    connector
      .sendExtendedEvent(event)
      .map(logResponse(_, auditType))
  }

  private def retrieveGroupName(zReference: String, auditType: String)(implicit hc: HeaderCarrier): Future[Option[String]] =
    disaAccountConnector
      .getCompanyName(zReference)
      .map { companyName =>
        if (companyName.isEmpty) {
          logger.warn(s"$auditType audit sent without groupName, no company name found for zRef: [$zReference]")
        }
        companyName
      }
      .recover { case NonFatal(e) =>
        logger.warn(s"$auditType audit sent without groupName, failed to retrieve company name for zRef: [$zReference]", e)
        None
      }

  private def userDetail(userDetails: UserDetails): JsObject =
    userDetails match {
      case im: UserDetails.IsaManager =>
        Json.obj(
          EventData.credId         -> im.credId,
          EventData.credentialRole -> im.credentialRole
        )
      case agent: UserDetails.Agent =>
        Json.obj(
          EventData.agentId   -> agent.agentId,
          EventData.agentName -> agent.agentName
        )
    }

  private def logResponse(result: AuditResult, auditType: String): Unit =
    result match {
      case Success         => logger.info(s"$auditType audit successful")
      case Failure(err, _) => logger.warn(s"$auditType Audit Error, message: $err")
      case Disabled        => logger.warn(s"$auditType failure - auditing disabled")
    }
}

object AuditTypes {
  val APIUploadStarted = "APIUploadStarted"
}

object EventData {
  val internalReturnId  = "internalReturnId"
  val uploadStartedTime = "uploadStartedTime"
  val period            = "period"
  val groupId           = "groupId"
  val groupName         = "groupName"
  val zReference        = "zReference"
  val userType          = "userType"
  val credId            = "credId"
  val credentialRole    = "credentialRole"
  val agentId           = "agentId"
  val agentName         = "agentName"
}
