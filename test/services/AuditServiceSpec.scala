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
import play.api.libs.json.JsObject
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.connectors.DisaAccountConnector
import uk.gov.hmrc.disareturns.models.common.{Month, ReportingPeriod}
import uk.gov.hmrc.disareturns.services.{AuditService, AuditTypes, EventData}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import utils.BaseUnitSpec

import scala.concurrent.Future

class AuditServiceSpec extends BaseUnitSpec {

  private val testAppName      = "disa-returns"
  private val testCompanyName  = "testIM"
  private val internalReturnId = "2f1611e2-0df4-4ddd-b666-b93554e6c85c"
  private val reportingPeriod  = ReportingPeriod("2025-26", Month.JAN)

  private trait TestSetup {
    val auditConnector:       AuditConnector       = mock[AuditConnector]
    val disaAccountConnector: DisaAccountConnector = mock[DisaAccountConnector]
    val appConfig:            AppConfig            = mock[AppConfig]
    val service = new AuditService(auditConnector, disaAccountConnector, appConfig, testReportingPeriodClock)

    when(appConfig.appName).thenReturn(testAppName)
    when(disaAccountConnector.getCompanyName(eqTo(validZReference))(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(testCompanyName)))
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
}
