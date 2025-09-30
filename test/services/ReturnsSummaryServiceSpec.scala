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

import org.mockito.ArgumentMatchers.{any, argThat, eq => eqTo}
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.common.Month
import uk.gov.hmrc.disareturns.models.summary.repository.MonthlyReturnsSummary
import uk.gov.hmrc.disareturns.models.summary.repository.SaveReturnsSummaryResult._
import uk.gov.hmrc.disareturns.services.ReturnsSummaryService
import utils.BaseUnitSpec

import scala.concurrent.Future

class ReturnsSummaryServiceSpec extends BaseUnitSpec {

  private val validZRef       = "Z1234"
  private val validTaxEndYear = 2026
  private val validMonth      = Month.SEP
  private val totalRecords    = 3

  override def beforeEach(): Unit = reset(mockReturnsSummaryRepository)

  "ReturnsSummaryService#saveReturnsSummary" should {

    "return Saved when repository upsert succeeds" in {
      when(mockReturnsSummaryRepository.upsert(any[MonthlyReturnsSummary])).thenReturn(Future.successful(()))
      val service = new ReturnsSummaryService(mockReturnsSummaryRepository)

      val result = await(service.saveReturnsSummary(MonthlyReturnsSummary(validZRef, validTaxEndYear, validMonth, totalRecords)))

      result mustBe Saved
      verify(mockReturnsSummaryRepository).upsert(argThat[MonthlyReturnsSummary] { summary =>
        summary.zRef == validZRef &&
        summary.taxYearEnd == validTaxEndYear &&
        summary.month == validMonth &&
        summary.totalRecords == totalRecords
      })
    }

    "return Error(msg) when db fails" in {
      val msg = "f"
      when(mockReturnsSummaryRepository.upsert(any[MonthlyReturnsSummary]))
        .thenReturn(Future.failed(new Exception(msg)))
      val service = new ReturnsSummaryService(mockReturnsSummaryRepository)

      val result = await(service.saveReturnsSummary(MonthlyReturnsSummary(validZRef, validTaxEndYear, validMonth, totalRecords)))

      result mustBe Error(msg)
      verify(mockReturnsSummaryRepository).upsert(argThat[MonthlyReturnsSummary] { summary =>
        summary.zRef == validZRef &&
        summary.taxYearEnd == validTaxEndYear &&
        summary.month == validMonth &&
        summary.totalRecords == totalRecords
      })
    }
  }
}
