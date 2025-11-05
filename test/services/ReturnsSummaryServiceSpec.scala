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
import play.api.Application
import play.api.inject.bind
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.models.common.{InternalServerErr, ReturnNotFoundErr}
import uk.gov.hmrc.disareturns.models.summary.ReturnSummaryResults
import uk.gov.hmrc.disareturns.models.summary.repository.MonthlyReturnsSummary
import uk.gov.hmrc.disareturns.repositories.MonthlyReturnsSummaryRepository
import uk.gov.hmrc.disareturns.services.ReturnsSummaryService
import utils.BaseUnitSpec

import scala.concurrent.Future

class ReturnsSummaryServiceSpec extends BaseUnitSpec {

  override lazy val app: Application =
    app(bind[MonthlyReturnsSummaryRepository].toInstance(mockReturnsSummaryRepository), bind[AppConfig].toInstance(mockAppConfig))
  private val service = app.injector.instanceOf[ReturnsSummaryService]

  override def beforeEach(): Unit = reset(mockReturnsSummaryRepository)

  private val totalRecords = 3

  "ReturnsSummaryService#retrieveReturnSummary" should {

    "return a ReturnSummaryResults object when a matching summary is found" in {
      val returnSummaryResults = MonthlyReturnsSummary(validZRef, validTaxYear, validMonth, 1)
      when(mockReturnsSummaryRepository.retrieveReturnSummary(any, any, any)).thenReturn(Future.successful(Some(returnSummaryResults)))
      when(mockAppConfig.getNoOfPagesForReturnResults(any)).thenReturn(Some(1))
      when(mockAppConfig.getReturnResultsLocation(any, any, any)).thenReturn("results")

      val result = await(service.retrieveReturnSummary(validZRef, validTaxYear, validMonth))

      verify(mockAppConfig).getNoOfPagesForReturnResults(any)

      result mustBe Right(
        ReturnSummaryResults("results", 1, 1)
      )
    }

    "return a ReturnNotFound error when no summary is found" in {
      when(mockReturnsSummaryRepository.retrieveReturnSummary(any, any, any)).thenReturn(Future.successful(None))

      val result = await(service.retrieveReturnSummary(validZRef, validTaxYear, validMonth))

      result mustBe Left(ReturnNotFoundErr("No return found for Z1234 for SEP 2026-27"))
    }

    "return a InternalServerErr when something goes wrong on the server" in {
      when(mockReturnsSummaryRepository.retrieveReturnSummary(any, any, any)).thenReturn(Future.failed(new Exception("fubar")))

      val result = await(service.retrieveReturnSummary(validZRef, validTaxYear, validMonth))

      result mustBe Left(InternalServerErr("fubar"))
    }
  }

  "ReturnsSummaryService#saveReturnsSummary" should {

    "return Saved when repository upsert succeeds" in {
      when(mockReturnsSummaryRepository.upsert(any[MonthlyReturnsSummary])).thenReturn(Future.successful(()))

      val result = await(service.saveReturnsSummary(MonthlyReturnsSummary(validZRef, validTaxYear, validMonth, totalRecords)))

      result mustBe Right(())
      verify(mockReturnsSummaryRepository).upsert(argThat[MonthlyReturnsSummary] { summary =>
        summary.zRef == validZRef &&
        summary.taxYear == validTaxYear &&
        summary.month == validMonth &&
        summary.totalRecords == totalRecords
      })
    }

    "return Error(msg) when db fails" in {
      val msg = "f"
      when(mockReturnsSummaryRepository.upsert(any[MonthlyReturnsSummary]))
        .thenReturn(Future.failed(new Exception(msg)))

      val result = await(service.saveReturnsSummary(MonthlyReturnsSummary(validZRef, validTaxYear, validMonth, totalRecords)))

      result mustBe Left(InternalServerErr(msg))
      verify(mockReturnsSummaryRepository).upsert(argThat[MonthlyReturnsSummary] { summary =>
        summary.zRef == validZRef &&
        summary.taxYear == validTaxYear &&
        summary.month == validMonth &&
        summary.totalRecords == totalRecords
      })
    }
  }
}
