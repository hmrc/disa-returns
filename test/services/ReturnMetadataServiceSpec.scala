/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.common.Month
import uk.gov.hmrc.disareturns.models.initiate.inboundRequest.{SubmissionRequest, TaxYear}
import uk.gov.hmrc.disareturns.models.initiate.mongo.ReturnMetadata
import uk.gov.hmrc.disareturns.repositories.ReturnMetadataRepository
import uk.gov.hmrc.disareturns.services.ReturnMetadataService
import utils.BaseUnitSpec

import scala.concurrent.Future

class ReturnMetadataServiceSpec extends BaseUnitSpec {

  "create and insert ReturnMetadata, returning the returnId" in {
    val mockRepository = mock[ReturnMetadataRepository]
    val service        = new ReturnMetadataService(mockRepository)

    val boxId         = "box-123"
    val isaManagerRef = "Z123456"
    val submissionRequest = SubmissionRequest(
      totalRecords = 5,
      submissionPeriod = Month.JAN,
      taxYear = TaxYear(2025)
    )

    when(mockRepository.insert(any[ReturnMetadata]))
      .thenReturn(Future.successful("test-return-id"))

    val result = await(service.saveReturnMetadata(boxId, submissionRequest, isaManagerRef))

    result shouldBe "test-return-id"
    verify(mockRepository).insert(any[ReturnMetadata])
  }

  "existsByIsaManagerReferenceAndReturnId" should {

    "return true when a matching document exists" in {
      val mockRepository = mock[ReturnMetadataRepository]
      val service        = new ReturnMetadataService(mockRepository)

      val isaManagerRef = "Z123456"
      val returnId      = "return-123"

      when(mockRepository.findByIsaManagerReferenceAndReturnId(isaManagerRef, returnId))
        .thenReturn(
          Future.successful(Some(ReturnMetadata(returnId = returnId, boxId = "", isaManagerReference = isaManagerRef, submissionRequest = null)))
        )

      val result = await(service.existsByIsaManagerReferenceAndReturnId(isaManagerRef, returnId))

      result shouldBe true
      verify(mockRepository).findByIsaManagerReferenceAndReturnId(isaManagerRef, returnId)
    }

    "return false when no matching document exists" in {
      val mockRepository = mock[ReturnMetadataRepository]
      val service        = new ReturnMetadataService(mockRepository)

      val isaManagerRef = "Z123456"
      val returnId      = "return-123"

      when(mockRepository.findByIsaManagerReferenceAndReturnId(isaManagerRef, returnId))
        .thenReturn(Future.successful(None))

      val result = await(service.existsByIsaManagerReferenceAndReturnId(isaManagerRef, returnId))

      result shouldBe false
      verify(mockRepository).findByIsaManagerReferenceAndReturnId(isaManagerRef, returnId)
    }
  }

}
