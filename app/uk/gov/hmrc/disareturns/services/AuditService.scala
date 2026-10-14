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
import uk.gov.hmrc.disareturns.connectors.{DisaAccountConnector, SubmissionConnector}
import uk.gov.hmrc.disareturns.models.common.{ReportingPeriod, UserDetails}
import uk.gov.hmrc.disareturns.models.submission.{ErrorVolume, MonthlyReturn, ValidationMetrics}
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
  submissionConnector:  SubmissionConnector,
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

  def auditApiDeclarationSubmitted(
    zReference:      String,
    userDetails:     UserDetails,
    reportingPeriod: ReportingPeriod,
    nilReturn:       Boolean,
    failureReason:   Option[String]
  )(implicit hc:     HeaderCarrier): Future[Unit] =
    for {
      monthlyReturn <- retrieveMonthlyReturn(zReference, reportingPeriod, AuditTypes.APIDeclarationSubmitted)
      groupName     <- retrieveGroupName(zReference, AuditTypes.APIDeclarationSubmitted)
      returnDetail = (nilReturn, failureReason, monthlyReturn) match {
                       case (true, _, _)            => Json.obj(EventData.nilReturn -> "yes")
                       case (false, None, Some(mr)) => submissionCounts(mr)
                       case _                       => Json.obj()
                     }
      statusDetail = failureReason.fold(Json.obj(EventData.submissionStatus -> AuditStatus.Success)) { reason =>
                       Json.obj(
                         EventData.submissionStatus -> AuditStatus.Failure,
                         EventData.failureReason    -> reason
                       )
                     }
      detail =
        monthlyReturn.fold(Json.obj())(mr => Json.obj(EventData.internalReturnId -> mr.submissionId)) ++
          Json.obj(
            EventData.period  -> periodFormatter.format(reportingPeriod.yearMonth),
            EventData.groupId -> userDetails.groupId
          ) ++
          groupName.fold(Json.obj())(name => Json.obj(EventData.groupName -> name)) ++
          Json.obj(
            EventData.zReference -> zReference,
            EventData.userType   -> userDetails.userType
          ) ++
          userDetail(userDetails) ++
          returnDetail ++
          statusDetail
      _ <- sendEvent(AuditTypes.APIDeclarationSubmitted, detail)
    } yield ()

  def auditApiValidation(
    zReference:      String,
    userDetails:     UserDetails,
    reportingPeriod: ReportingPeriod,
    metrics:         ValidationMetrics,
    errorVolumes:    Seq[ErrorVolume]
  )(implicit hc:     HeaderCarrier): Future[Unit] =
    retrieveGroupName(zReference, AuditTypes.APIValidation).flatMap { groupName =>
      val statusDetail =
        if (errorVolumes.isEmpty) Json.obj(EventData.apiUploadStatus -> AuditStatus.Success)
        else
          Json.obj(
            EventData.apiUploadStatus -> AuditStatus.Failure,
            EventData.errorDetails -> errorVolumes.map(ev =>
              Json.obj(
                EventData.errorType -> ev.errorType.toLowerCase(Locale.UK),
                EventData.volume    -> ev.volume
              )
            )
          )
      val detail =
        Json.obj(
          EventData.period  -> periodFormatter.format(reportingPeriod.yearMonth),
          EventData.groupId -> userDetails.groupId
        ) ++
          groupName.fold(Json.obj())(name => Json.obj(EventData.groupName -> name)) ++
          Json.obj(
            EventData.zReference -> zReference,
            EventData.userType   -> userDetails.userType
          ) ++
          userDetail(userDetails) ++
          Json.obj(
            EventData.requestValidationTime -> metrics.requestValidationTime.toMillis.toString,
            EventData.requestSize           -> metrics.requestSize.toString,
            EventData.numberOfEntries       -> metrics.numberOfEntries.toString
          ) ++
          statusDetail

      sendEvent(AuditTypes.APIValidation, detail)
    }

  private def submissionCounts(monthlyReturn: MonthlyReturn): JsObject =
    Json.obj(
      EventData.numberOfRequests -> monthlyReturn.numberOfRequests.toString,
      EventData.numberOfEntries  -> monthlyReturn.numberOfEntries.toString
    )

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

  private def retrieveMonthlyReturn(zReference: String, reportingPeriod: ReportingPeriod, auditType: String)(implicit
    hc:                                         HeaderCarrier
  ): Future[Option[MonthlyReturn]] =
    submissionConnector
      .getMonthlyReturn(zReference, reportingPeriod.taxYear, reportingPeriod.month)
      .map { monthlyReturn =>
        if (monthlyReturn.isEmpty) {
          logger.warn(
            s"$auditType audit sent without internalReturnId, numberOfRequests or numberOfEntries, no monthly return found for zRef: [$zReference]"
          )
        }
        monthlyReturn
      }
      .recover { case NonFatal(e) =>
        logger.warn(
          s"$auditType audit sent without internalReturnId, numberOfRequests or numberOfEntries, failed to retrieve monthly return for zRef: [$zReference]",
          e
        )
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
  val APIUploadStarted        = "APIUploadStarted"
  val APIDeclarationSubmitted = "APIDeclarationSubmitted"
  val APIValidation           = "APIValidation"
}

object AuditStatus {
  val Success = "Success"
  val Failure = "Failure"
}

object EventData {
  val internalReturnId      = "internalReturnId"
  val uploadStartedTime     = "uploadStartedTime"
  val period                = "period"
  val groupId               = "groupId"
  val groupName             = "groupName"
  val zReference            = "zReference"
  val userType              = "userType"
  val credId                = "credId"
  val credentialRole        = "credentialRole"
  val agentId               = "agentId"
  val agentName             = "agentName"
  val nilReturn             = "nilReturn"
  val numberOfRequests      = "numberOfRequests"
  val numberOfEntries       = "numberOfEntries"
  val submissionStatus      = "submissionStatus"
  val failureReason         = "failureReason"
  val requestValidationTime = "requestValidationTime"
  val requestSize           = "requestSize"
  val apiUploadStatus       = "APIUploadStatus"
  val errorDetails          = "errorDetails"
  val errorType             = "errorType"
  val volume                = "volume"
}
