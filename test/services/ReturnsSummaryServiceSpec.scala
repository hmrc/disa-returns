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
import uk.gov.hmrc.disareturns.models.common.{InternalServerErr, ReturnNotFoundErr}
import uk.gov.hmrc.disareturns.models.summary.ReturnSummaryResults
import uk.gov.hmrc.disareturns.models.summary.repository.MonthlyReturnsSummary
import uk.gov.hmrc.disareturns.services.ReturnsSummaryService
import utils.BaseUnitSpec

import scala.concurrent.Future

class ReturnsSummaryServiceSpec extends BaseUnitSpec {
  private val service      = new ReturnsSummaryService(mockReturnsSummaryRepository, mockAppConfig)
  private val totalRecords = 3

  override def beforeEach(): Unit = reset(mockReturnsSummaryRepository)

  "retrieveReturnSummary" should {
    "return a summary with a periodless results location" in {
      when(mockReturnsSummaryRepository.retrieveReturnSummary(any)).thenReturn(Future.successful(Some(MonthlyReturnsSummary(validZReference, 1))))
      when(mockAppConfig.getNoOfPagesForReturnResults(any)).thenReturn(Some(1))
      when(mockAppConfig.selfHost).thenReturn("localhost")

      await(service.retrieveReturnSummary(validZReference)) shouldBe
        Right(ReturnSummaryResults(s"localhost/monthly/$validZReference/results?page=0", 1, 1))
    }

    "return not found when no current summary exists" in {
      when(mockReturnsSummaryRepository.retrieveReturnSummary(any)).thenReturn(Future.successful(None))
      await(service.retrieveReturnSummary(validZReference)) shouldBe Left(ReturnNotFoundErr(s"No return found for $validZReference"))
    }

    "handle invalid record counts and repository failures" in {
      when(mockReturnsSummaryRepository.retrieveReturnSummary(any)).thenReturn(Future.successful(Some(MonthlyReturnsSummary(validZReference, -1))))
      when(mockAppConfig.getNoOfPagesForReturnResults(any)).thenReturn(None)
      await(service.retrieveReturnSummary(validZReference)) shouldBe Left(InternalServerErr())

      when(mockReturnsSummaryRepository.retrieveReturnSummary(any)).thenReturn(Future.failed(new Exception("fubar")))
      await(service.retrieveReturnSummary(validZReference)) shouldBe Left(InternalServerErr())
    }
  }

  "saveReturnsSummary" should {
    "upsert a Z-reference and total record count" in {
      when(mockReturnsSummaryRepository.upsert(any[MonthlyReturnsSummary])).thenReturn(Future.successful(()))
      await(service.saveReturnsSummary(MonthlyReturnsSummary(validZReference, totalRecords))) shouldBe Right(())
      verify(mockReturnsSummaryRepository).upsert(
        argThat[MonthlyReturnsSummary](summary => summary.zRef == validZReference && summary.totalRecords == totalRecords)
      )
    }

    "map repository failures" in {
      when(mockReturnsSummaryRepository.upsert(any[MonthlyReturnsSummary])).thenReturn(Future.failed(new Exception("fail")))
      await(service.saveReturnsSummary(MonthlyReturnsSummary(validZReference, totalRecords))) shouldBe Left(InternalServerErr())
    }
  }
}
