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

package services

import org.mockito.ArgumentMatchers.{any, argThat}
import org.mockito.Mockito.{reset, verify, when}
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.callback.ReconciliationReportReady
import uk.gov.hmrc.disareturns.models.common.InternalServerErr
import uk.gov.hmrc.disareturns.models.ppns.ReconciliationReportReadyNotification
import uk.gov.hmrc.disareturns.services.ReconciliationReportReadyCallbackService
import utils.BaseUnitSpec

import scala.concurrent.Future

class ReconciliationReportReadyCallbackServiceSpec extends BaseUnitSpec {
  private val service      = new ReconciliationReportReadyCallbackService(mockReconciliationReportReadyRepository, mockAppConfig)
  private val totalRecords = 3

  override def beforeEach(): Unit = reset(mockReconciliationReportReadyRepository)

  "buildNotification" should {
    "return a notification with a periodless results location" in {
      when(mockReconciliationReportReadyRepository.findByZReference(any))
        .thenReturn(Future.successful(Some(ReconciliationReportReady(validZReference, 1))))
      when(mockAppConfig.getNoOfPagesForReturnResults(any)).thenReturn(Some(1))
      when(mockAppConfig.selfHost).thenReturn("localhost")

      await(service.buildNotification(validZReference)) shouldBe
        Right(ReconciliationReportReadyNotification(s"localhost/monthly/$validZReference/results?page=0", 1, 1))
    }

    "return an internal error when callback data is missing" in {
      when(mockReconciliationReportReadyRepository.findByZReference(any)).thenReturn(Future.successful(None))
      await(service.buildNotification(validZReference)) shouldBe Left(InternalServerErr())
    }

    "handle invalid record counts and repository failures" in {
      when(mockReconciliationReportReadyRepository.findByZReference(any))
        .thenReturn(Future.successful(Some(ReconciliationReportReady(validZReference, -1))))
      when(mockAppConfig.getNoOfPagesForReturnResults(any)).thenReturn(None)
      await(service.buildNotification(validZReference)) shouldBe Left(InternalServerErr())

      when(mockReconciliationReportReadyRepository.findByZReference(any)).thenReturn(Future.failed(new Exception("fubar")))
      await(service.buildNotification(validZReference)) shouldBe Left(InternalServerErr())
    }
  }

  "save" should {
    "upsert a Z-reference and total record count" in {
      when(mockReconciliationReportReadyRepository.upsert(any[ReconciliationReportReady])).thenReturn(Future.successful(()))
      await(service.save(ReconciliationReportReady(validZReference, totalRecords))) shouldBe Right(())
      verify(mockReconciliationReportReadyRepository).upsert(
        argThat[ReconciliationReportReady](reportReady => reportReady.zRef == validZReference && reportReady.totalRecords == totalRecords)
      )
    }

    "map repository failures" in {
      when(mockReconciliationReportReadyRepository.upsert(any[ReconciliationReportReady])).thenReturn(Future.failed(new Exception("fail")))
      await(service.save(ReconciliationReportReady(validZReference, totalRecords))) shouldBe Left(InternalServerErr())
    }
  }
}
