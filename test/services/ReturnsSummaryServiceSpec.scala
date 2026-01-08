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
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.common.{InternalServerErr, ReturnNotFoundErr}
import uk.gov.hmrc.disareturns.models.summary.ReturnSummaryResults
import uk.gov.hmrc.disareturns.models.summary.repository.MonthlyReturnsSummary
import uk.gov.hmrc.disareturns.services.ReturnsSummaryService
import utils.BaseUnitSpec

import scala.concurrent.Future

class ReturnsSummaryServiceSpec extends BaseUnitSpec {

  private val service = new ReturnsSummaryService(mockReturnsSummaryRepository, mockAppConfig)

  private val totalRecords = 3

  override def beforeEach(): Unit = reset(mockReturnsSummaryRepository)

  "ReturnsSummaryService#retrieveReturnSummary" should {

    "return a ReturnSummaryResults object when a matching summary is found" in {
      val returnSummaryResults = MonthlyReturnsSummary(validZReference, validTaxYear, validMonth, 1)
      when(mockReturnsSummaryRepository.retrieveReturnSummary(any, any, any)).thenReturn(Future.successful(Some(returnSummaryResults)))
      when(mockAppConfig.getNoOfPagesForReturnResults(any)).thenReturn(Some(1))
      when(mockAppConfig.selfHost).thenReturn("localhost")

      val result = await(service.retrieveReturnSummary(validZReference, validTaxYear, validMonth))

      verify(mockAppConfig).getNoOfPagesForReturnResults(any)

      result mustBe Right(ReturnSummaryResults(s"localhost/monthly/$validZReference/2026-27/SEP/results?page=0", 1, 1))
    }

    "return a ReturnNotFound error when no summary is found" in {
      when(mockReturnsSummaryRepository.retrieveReturnSummary(any, any, any)).thenReturn(Future.successful(None))

      val result = await(service.retrieveReturnSummary(validZReference, validTaxYear, validMonth))

      result mustBe Left(ReturnNotFoundErr(s"No return found for $validZReference for SEP 2026-27"))
    }

    "return a InternalServerErr when NPS sends back an invalid number of records" in {
      val returnSummaryResults = MonthlyReturnsSummary(validZReference, validTaxYear, validMonth, -1)
      when(mockReturnsSummaryRepository.retrieveReturnSummary(any, any, any)).thenReturn(Future.successful(Some(returnSummaryResults)))
      when(mockAppConfig.getNoOfPagesForReturnResults(any)).thenReturn(None)

      val result = await(service.retrieveReturnSummary(validZReference, validTaxYear, validMonth))

      result mustBe Left(InternalServerErr())
    }

    "return a InternalServerErr when something goes wrong on the server" in {
      when(mockReturnsSummaryRepository.retrieveReturnSummary(any, any, any)).thenReturn(Future.failed(new Exception("fubar")))

      val result = await(service.retrieveReturnSummary(validZReference, validTaxYear, validMonth))

      result mustBe Left(InternalServerErr())
    }
  }

  "ReturnsSummaryService#saveReturnsSummary" should {

    "return Saved when repository upsert succeeds" in {
      when(mockReturnsSummaryRepository.upsert(any[MonthlyReturnsSummary])).thenReturn(Future.successful(()))

      val result = await(service.saveReturnsSummary(MonthlyReturnsSummary(validZReference, validTaxYear, validMonth, totalRecords)))

      result mustBe Right(())
      verify(mockReturnsSummaryRepository).upsert(argThat[MonthlyReturnsSummary] { summary =>
        summary.zRef == validZReference &&
        summary.taxYear == validTaxYear &&
        summary.month == validMonth &&
        summary.totalRecords == totalRecords
      })
    }

    "return InternalServerErr if repository upsert throws an exception" in {
      when(mockReturnsSummaryRepository.upsert(any[MonthlyReturnsSummary]))
        .thenReturn(Future.failed(new Exception("fail")))

      val result = await(service.saveReturnsSummary(MonthlyReturnsSummary(validZReference, validTaxYear, validMonth, totalRecords)))

      result mustBe Left(InternalServerErr())
      verify(mockReturnsSummaryRepository).upsert(argThat[MonthlyReturnsSummary] { summary =>
        summary.zRef == validZReference &&
        summary.taxYear == validTaxYear &&
        summary.month == validMonth &&
        summary.totalRecords == totalRecords
      })
    }
  }
}
