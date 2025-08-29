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

import org.mockito.Mockito.when
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.common.{MisMatchErr, Month, ReturnIdNotMatchedErr}
import uk.gov.hmrc.disareturns.models.complete.CompleteResponse
import uk.gov.hmrc.disareturns.models.initiate.inboundRequest.{SubmissionRequest, TaxYear}
import uk.gov.hmrc.disareturns.models.initiate.mongo.ReturnMetadata
import uk.gov.hmrc.disareturns.services.CompleteReturnService
import utils.BaseUnitSpec

import java.time.Instant
import scala.concurrent.Future

class CompleteReturnServiceSpec extends BaseUnitSpec {

  val service             = new CompleteReturnService(mockReturnMetadataRepository, mockMonthlyReportDocumentRepository)
  val isaManagerReference = "Z1111"
  val returnId            = "Return-1234"
  val returnMetadata: ReturnMetadata =
    ReturnMetadata(returnId, "Box-1", SubmissionRequest(100, Month.MAR, TaxYear(2025)), isaManagerReference, Instant.now())

  "findReturnMetadata" should {
    "return Some(ReturnMetadata) is the ReturnMetadata exist" in {
      when(mockReturnMetadataRepository.findByIsaManagerReferenceAndReturnId(isaManagerReference, returnId))
        .thenReturn(Future.successful(Some(returnMetadata)))
      await(service.findReturnMetadata(isaManagerReference, returnId)) shouldBe Some(returnMetadata)
    }

    "return None if ReturnMetadata does not exist" in {
      when(mockReturnMetadataRepository.findByIsaManagerReferenceAndReturnId(isaManagerReference, returnId))
        .thenReturn(Future.successful(None))
      await(service.findReturnMetadata(isaManagerReference, returnId)) shouldBe None
    }
  }
  "countSubmittedReturns" should {
    "return a count of submitted returns" in {
      when(mockMonthlyReportDocumentRepository.countByIsaManagerReferenceAndReturnId(isaManagerReference, returnId)).thenReturn(Future.successful(5))
      await(service.countSubmittedReturns(isaManagerReference, returnId)) shouldBe 5
    }
  }
  "validateRecordCount" should {
    "return Left(MisMatchErr) when there is a match between the expected and submitted returns" in {
      when(mockReturnMetadataRepository.findByIsaManagerReferenceAndReturnId(isaManagerReference, returnId))
        .thenReturn(Future.successful(Some(returnMetadata)))
      when(mockMonthlyReportDocumentRepository.countByIsaManagerReferenceAndReturnId(isaManagerReference, returnId)).thenReturn(Future.successful(15))
      await(service.validateRecordCount(isaManagerReference, returnId)) shouldBe Left(MisMatchErr)
    }
    "return Right(CompleteResponse) when the expected submission matches the submitted" in {
      val returnSummaryLocation = s"/monthly/$isaManagerReference/$returnId/results/summary"
      when(mockReturnMetadataRepository.findByIsaManagerReferenceAndReturnId(isaManagerReference, returnId))
        .thenReturn(Future.successful(Some(returnMetadata)))
      when(mockMonthlyReportDocumentRepository.countByIsaManagerReferenceAndReturnId(isaManagerReference, returnId))
        .thenReturn(Future.successful(100))
      await(service.validateRecordCount(isaManagerReference, returnId)) shouldBe Right(CompleteResponse(returnSummaryLocation))
    }
    "return Left(ReturnIdNotMatchedErr) when the return id doesn't not exist" in {
      val invalidReturnId = "Invalid"
      when(mockReturnMetadataRepository.findByIsaManagerReferenceAndReturnId(isaManagerReference, invalidReturnId))
        .thenReturn(Future.successful(None))
      when(mockMonthlyReportDocumentRepository.countByIsaManagerReferenceAndReturnId(isaManagerReference, invalidReturnId))
        .thenReturn(Future.successful(0))
      await(service.validateRecordCount(isaManagerReference, invalidReturnId)) shouldBe Left(ReturnIdNotMatchedErr)
    }
  }
}
