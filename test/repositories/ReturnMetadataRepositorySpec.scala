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

package repositories

import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.common.Month
import uk.gov.hmrc.disareturns.models.initiate.inboundRequest.{SubmissionRequest, TaxYear}
import uk.gov.hmrc.disareturns.models.initiate.mongo.ReturnMetadata
import uk.gov.hmrc.disareturns.repositories.ReturnMetadataRepository
import uk.gov.hmrc.mongo.MongoComponent
import utils.BaseUnitSpec

import java.time.Instant
import java.time.temporal.ChronoUnit

class ReturnMetadataRepositorySpec extends BaseUnitSpec {

  protected val databaseName = "disa-returns-test"

  protected val mongoUri:         String         = s"mongodb://127.0.0.1:27017/$databaseName"
  lazy val mongoComponentForTest: MongoComponent = MongoComponent(mongoUri)

  protected val repository: ReturnMetadataRepository =
    new ReturnMetadataRepository(mongoComponentForTest)

  "insert" should {
    "store a submission and return the returnId" in new testSetup {
      val result: String = await(repository.insert(testReturnMetadata))
      result shouldBe testReturnMetadata.returnId

      val stored: Option[ReturnMetadata] = await(repository.collection.find().toFuture()).headOption
      stored shouldBe Some(testReturnMetadata)
    }
  }

  "findByIsaManagerReference" should {
    "return the matching document when found" in new testSetup {
      await(repository.insert(testReturnMetadata))

      val result: Option[ReturnMetadata] = await(repository.findByIsaManagerReference("test-isa-ref"))
      result shouldBe Some(testReturnMetadata)
    }

    "return None when no document matches" in {
      val result = await(repository.findByIsaManagerReference("NON_EXISTENT"))
      result shouldBe None
    }
  }

  "dropCollection" should {
    "remove all documents from the collection" in new testSetup {
      await(repository.insert(testReturnMetadata))

      await(repository.dropCollection())

      val result: Option[ReturnMetadata] = await(repository.findByIsaManagerReference("Z123456"))
      result shouldBe None
    }
  }

  class testSetup {
    val testReturnMetadata: ReturnMetadata = ReturnMetadata(
      returnId = "test-return-id",
      boxId = "test-box-id",
      isaManagerReference = "test-isa-ref",
      submissionRequest = SubmissionRequest(totalRecords = 1000, submissionPeriod = Month.JAN, taxYear = TaxYear(2025)),
      createdAt = Instant.now.truncatedTo(ChronoUnit.MILLIS)
    )

    await(repository.dropCollection())
    await(repository.ensureIndexes())
  }
}
