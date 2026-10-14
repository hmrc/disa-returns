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

package services

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{verify, when}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.connectors.{DisaAccountConnector, SubmissionConnector}
import uk.gov.hmrc.disareturns.models.common.{Month, ReportingPeriod}
import uk.gov.hmrc.disareturns.models.submission.{ErrorVolume, MonthlyReturn, Submission, SubmissionDetails, ValidationMetrics}
import uk.gov.hmrc.disareturns.services.{AuditService, AuditTypes, EventData, SubmissionStatus}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import utils.BaseUnitSpec

import java.time.Duration
import scala.concurrent.Future

class AuditServiceSpec extends BaseUnitSpec {

  private val testAppName      = "disa-returns"
  private val testCompanyName  = "testIM"
  private val internalReturnId = "2f1611e2-0df4-4ddd-b666-b93554e6c85c"
  private val reportingPeriod  = ReportingPeriod("2025-26", Month.JAN)
  private val metrics          = ValidationMetrics(numberOfEntries = 10L, requestSize = 4096L, requestValidationTime = Duration.ofMillis(153))
  private val monthlyReturn =
    MonthlyReturn(
      internalReturnId,
      Seq(
        Submission(Submission.Stored, Some(SubmissionDetails(Some(150L)))),
        Submission(Submission.Stored, Some(SubmissionDetails(Some(209L)))),
        Submission("CREATED")
      )
    )

  private trait TestSetup {
    val auditConnector:       AuditConnector       = mock[AuditConnector]
    val disaAccountConnector: DisaAccountConnector = mock[DisaAccountConnector]
    val submissionConnector:  SubmissionConnector  = mock[SubmissionConnector]
    val appConfig:            AppConfig            = mock[AppConfig]
    val service = new AuditService(auditConnector, disaAccountConnector, submissionConnector, appConfig, testReportingPeriodClock)

    when(appConfig.appName).thenReturn(testAppName)
    when(disaAccountConnector.getCompanyName(eqTo(validZReference))(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(testCompanyName)))
    when(submissionConnector.getMonthlyReturn(eqTo(validZReference), eqTo(reportingPeriod.taxYear), eqTo(reportingPeriod.month))(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(monthlyReturn)))
    when(auditConnector.sendExtendedEvent(any[ExtendedDataEvent])(any(), any()))
      .thenReturn(Future.successful(Success))

    def captureEvent(): ExtendedDataEvent = {
      val captor: ArgumentCaptor[ExtendedDataEvent] = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
      verify(auditConnector).sendExtendedEvent(captor.capture())(any(), any())
      captor.getValue
    }
  }

  "AuditService.auditApiUploadStarted" should {

    "send APIUploadStarted with IM detail fields in the agreed order" in new TestSetup {
      service.auditApiUploadStarted(validZReference, testIsaManagerDetails, reportingPeriod, internalReturnId).futureValue shouldBe ()

      val event:  ExtendedDataEvent = captureEvent()
      val detail: JsObject          = event.detail.as[JsObject]

      event.auditSource shouldBe testAppName
      event.auditType   shouldBe AuditTypes.APIUploadStarted
      detail.keys.toSeq shouldBe Seq(
        EventData.internalReturnId,
        EventData.uploadStartedTime,
        EventData.period,
        EventData.groupId,
        EventData.groupName,
        EventData.zReference,
        EventData.userType,
        EventData.credId,
        EventData.credentialRole
      )
      (detail \ EventData.internalReturnId).as[String]  shouldBe internalReturnId
      (detail \ EventData.uploadStartedTime).as[String] shouldBe "2026-10-15T12:00:00.000Z"
      (detail \ EventData.period).as[String]            shouldBe "January 2026"
      (detail \ EventData.groupId).as[String]           shouldBe testGroupId
      (detail \ EventData.groupName).as[String]         shouldBe testCompanyName
      (detail \ EventData.zReference).as[String]        shouldBe validZReference
      (detail \ EventData.userType).as[String]          shouldBe "IM"
      (detail \ EventData.credId).as[String]            shouldBe testCredId
      (detail \ EventData.credentialRole).as[String]    shouldBe testCredentialRole
    }

    "send APIUploadStarted with Agent detail fields" in new TestSetup {
      service.auditApiUploadStarted(validZReference, testAgentDetails, reportingPeriod, internalReturnId).futureValue shouldBe ()

      val detail: JsObject = captureEvent().detail.as[JsObject]

      (detail \ EventData.groupId).as[String]      shouldBe testGroupId
      (detail \ EventData.userType).as[String]     shouldBe "Agent"
      (detail \ EventData.agentId).as[String]      shouldBe testAgentId
      (detail \ EventData.agentName).as[String]    shouldBe testAgentName
      (detail \ EventData.credId).toOption         shouldBe None
      (detail \ EventData.credentialRole).toOption shouldBe None
    }

    "format the period using the calendar year for months after the tax year start" in new TestSetup {
      service
        .auditApiUploadStarted(validZReference, testIsaManagerDetails, ReportingPeriod("2025-26", Month.APR), internalReturnId)
        .futureValue shouldBe ()

      (captureEvent().detail \ EventData.period).as[String] shouldBe "April 2025"
    }

    Seq(
      "no company name is found"      -> Future.successful(None),
      "the company name lookup fails" -> Future.failed(UpstreamErrorResponse("boom", 500))
    ).foreach { case (description, lookupResult) =>
      s"still send the event without groupName when $description" in new TestSetup {
        when(disaAccountConnector.getCompanyName(eqTo(validZReference))(any[HeaderCarrier])).thenReturn(lookupResult)

        service.auditApiUploadStarted(validZReference, testIsaManagerDetails, reportingPeriod, internalReturnId).futureValue shouldBe ()

        val detail: JsObject = captureEvent().detail.as[JsObject]

        (detail \ EventData.groupId).as[String] shouldBe testGroupId
        (detail \ EventData.groupName).toOption shouldBe None
      }
    }
  }
  "AuditService.auditApiDeclarationSubmitted" should {

    "send a successful APIDeclarationSubmitted with IM detail fields in the agreed order" in new TestSetup {
      service
        .auditApiDeclarationSubmitted(validZReference, testIsaManagerDetails, reportingPeriod, nilReturn = false, failureReason = None)
        .futureValue shouldBe ()

      val event:  ExtendedDataEvent = captureEvent()
      val detail: JsObject          = event.detail.as[JsObject]

      event.auditSource shouldBe testAppName
      event.auditType   shouldBe AuditTypes.APIDeclarationSubmitted
      detail.keys.toSeq shouldBe Seq(
        EventData.internalReturnId,
        EventData.period,
        EventData.groupId,
        EventData.groupName,
        EventData.zReference,
        EventData.userType,
        EventData.credId,
        EventData.credentialRole,
        EventData.numberOfRequests,
        EventData.numberOfEntries,
        EventData.submissionStatus
      )
      (detail \ EventData.internalReturnId).as[String] shouldBe internalReturnId
      (detail \ EventData.period).as[String]           shouldBe "January 2026"
      (detail \ EventData.groupId).as[String]          shouldBe testGroupId
      (detail \ EventData.groupName).as[String]        shouldBe testCompanyName
      (detail \ EventData.zReference).as[String]       shouldBe validZReference
      (detail \ EventData.userType).as[String]         shouldBe "IM"
      (detail \ EventData.credId).as[String]           shouldBe testCredId
      (detail \ EventData.credentialRole).as[String]   shouldBe testCredentialRole
      (detail \ EventData.numberOfRequests).as[String] shouldBe "2"
      (detail \ EventData.numberOfEntries).as[String]  shouldBe "359"
      (detail \ EventData.submissionStatus).as[String] shouldBe SubmissionStatus.Success
    }

    "send a failed nil return APIDeclarationSubmitted with Agent detail fields in the agreed order" in new TestSetup {
      service
        .auditApiDeclarationSubmitted(validZReference, testAgentDetails, reportingPeriod, nilReturn = true, failureReason = Some("Obligation closed"))
        .futureValue shouldBe ()

      val detail: JsObject = captureEvent().detail.as[JsObject]

      detail.keys.toSeq shouldBe Seq(
        EventData.internalReturnId,
        EventData.period,
        EventData.groupId,
        EventData.groupName,
        EventData.zReference,
        EventData.userType,
        EventData.agentId,
        EventData.agentName,
        EventData.nilReturn,
        EventData.submissionStatus,
        EventData.failureReason
      )
      (detail \ EventData.userType).as[String]         shouldBe "Agent"
      (detail \ EventData.agentId).as[String]          shouldBe testAgentId
      (detail \ EventData.agentName).as[String]        shouldBe testAgentName
      (detail \ EventData.nilReturn).as[String]        shouldBe "yes"
      (detail \ EventData.submissionStatus).as[String] shouldBe SubmissionStatus.Failure
      (detail \ EventData.failureReason).as[String]    shouldBe "Obligation closed"
    }

    Seq(
      "no monthly return is found"      -> Future.successful(None),
      "the monthly return lookup fails" -> Future.failed(UpstreamErrorResponse("boom", 500))
    ).foreach { case (description, lookupResult) =>
      s"still send the event without internalReturnId, numberOfRequests or numberOfEntries when $description" in new TestSetup {
        when(submissionConnector.getMonthlyReturn(any(), any(), any())(any[HeaderCarrier])).thenReturn(lookupResult)

        service
          .auditApiDeclarationSubmitted(validZReference, testIsaManagerDetails, reportingPeriod, nilReturn = false, failureReason = None)
          .futureValue shouldBe ()

        val detail: JsObject = captureEvent().detail.as[JsObject]

        (detail \ EventData.internalReturnId).toOption   shouldBe None
        (detail \ EventData.numberOfRequests).toOption   shouldBe None
        (detail \ EventData.numberOfEntries).toOption    shouldBe None
        (detail \ EventData.submissionStatus).as[String] shouldBe SubmissionStatus.Success
      }
    }

    "send a failed APIDeclarationSubmitted without numberOfRequests or numberOfEntries when a monthly return exists" in new TestSetup {
      service
        .auditApiDeclarationSubmitted(
          validZReference,
          testIsaManagerDetails,
          reportingPeriod,
          nilReturn = false,
          failureReason = Some("Obligation closed")
        )
        .futureValue shouldBe ()

      val detail: JsObject = captureEvent().detail.as[JsObject]

      detail.keys.toSeq shouldBe Seq(
        EventData.internalReturnId,
        EventData.period,
        EventData.groupId,
        EventData.groupName,
        EventData.zReference,
        EventData.userType,
        EventData.credId,
        EventData.credentialRole,
        EventData.submissionStatus,
        EventData.failureReason
      )
      (detail \ EventData.internalReturnId).as[String] shouldBe internalReturnId
      (detail \ EventData.submissionStatus).as[String] shouldBe SubmissionStatus.Failure
      (detail \ EventData.failureReason).as[String]    shouldBe "Obligation closed"
    }

    "still send the event without groupName when the company name lookup fails" in new TestSetup {
      when(disaAccountConnector.getCompanyName(eqTo(validZReference))(any[HeaderCarrier]))
        .thenReturn(Future.failed(UpstreamErrorResponse("boom", 500)))

      service
        .auditApiDeclarationSubmitted(validZReference, testIsaManagerDetails, reportingPeriod, nilReturn = false, failureReason = None)
        .futureValue shouldBe ()

      (captureEvent().detail \ EventData.groupName).toOption shouldBe None
    }

    "send numberOfRequests without numberOfEntries when submission does not return numberOfEntries" in new TestSetup {
      when(submissionConnector.getMonthlyReturn(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(MonthlyReturn(internalReturnId, Seq(Submission(Submission.Stored, Some(SubmissionDetails())))))))

      service
        .auditApiDeclarationSubmitted(validZReference, testIsaManagerDetails, reportingPeriod, nilReturn = false, failureReason = None)
        .futureValue shouldBe ()

      val detail: JsObject = captureEvent().detail.as[JsObject]

      (detail \ EventData.numberOfRequests).as[String] shouldBe "1"
      (detail \ EventData.numberOfEntries).toOption    shouldBe None
    }
  }

  "AuditService.auditApiValidation" should {

    "send a successful APIValidation with IM detail fields in the agreed order" in new TestSetup {
      service
        .auditApiValidation(validZReference, testIsaManagerDetails, reportingPeriod, metrics, Seq.empty)
        .futureValue shouldBe ()

      val event:  ExtendedDataEvent = captureEvent()
      val detail: JsObject          = event.detail.as[JsObject]

      event.auditSource shouldBe testAppName
      event.auditType   shouldBe AuditTypes.APIValidation
      detail shouldBe Json.obj(
        EventData.period                -> "January 2026",
        EventData.groupId               -> testGroupId,
        EventData.groupName             -> testCompanyName,
        EventData.zReference            -> validZReference,
        EventData.userType              -> "IM",
        EventData.credId                -> testCredId,
        EventData.credentialRole        -> testCredentialRole,
        EventData.requestValidationTime -> "153",
        EventData.requestSize           -> "4096",
        EventData.numberOfEntries       -> "10",
        EventData.apiUploadStatus       -> SubmissionStatus.Success
      )
      detail.keys.toSeq shouldBe Seq(
        EventData.period,
        EventData.groupId,
        EventData.groupName,
        EventData.zReference,
        EventData.userType,
        EventData.credId,
        EventData.credentialRole,
        EventData.requestValidationTime,
        EventData.requestSize,
        EventData.numberOfEntries,
        EventData.apiUploadStatus
      )
    }

    "send a failed APIValidation with Agent detail fields and errorDetails" in new TestSetup {
      val errorVolumes = Seq(ErrorVolume("MISSING_FIRST_NAME", 6), ErrorVolume("NINO_OR_ACC_NUM_MISSING", 1))

      service
        .auditApiValidation(validZReference, testAgentDetails, reportingPeriod, metrics, errorVolumes)
        .futureValue shouldBe ()

      val detail: JsObject = captureEvent().detail.as[JsObject]

      detail shouldBe Json.obj(
        EventData.period                -> "January 2026",
        EventData.groupId               -> testGroupId,
        EventData.groupName             -> testCompanyName,
        EventData.zReference            -> validZReference,
        EventData.userType              -> "Agent",
        EventData.agentId               -> testAgentId,
        EventData.agentName             -> testAgentName,
        EventData.requestValidationTime -> "153",
        EventData.requestSize           -> "4096",
        EventData.numberOfEntries       -> "10",
        EventData.apiUploadStatus       -> SubmissionStatus.Failure,
        EventData.errorDetails -> Json.arr(
          Json.obj(EventData.errorType -> "missing_first_name", EventData.volume      -> 6),
          Json.obj(EventData.errorType -> "nino_or_acc_num_missing", EventData.volume -> 1)
        )
      )
      detail.keys.toSeq shouldBe Seq(
        EventData.period,
        EventData.groupId,
        EventData.groupName,
        EventData.zReference,
        EventData.userType,
        EventData.agentId,
        EventData.agentName,
        EventData.requestValidationTime,
        EventData.requestSize,
        EventData.numberOfEntries,
        EventData.apiUploadStatus,
        EventData.errorDetails
      )
    }

    "still send the event without groupName when the company name lookup fails" in new TestSetup {
      when(disaAccountConnector.getCompanyName(eqTo(validZReference))(any[HeaderCarrier]))
        .thenReturn(Future.failed(UpstreamErrorResponse("boom", 500)))

      service
        .auditApiValidation(validZReference, testIsaManagerDetails, reportingPeriod, metrics, Seq.empty)
        .futureValue shouldBe ()

      (captureEvent().detail \ EventData.groupName).toOption shouldBe None
    }
  }
}
