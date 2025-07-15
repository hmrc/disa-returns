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

///*
// * Copyright 2025 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package repositories
//
//import org.scalatest.concurrent.IntegrationPatience
//import play.api.test.Helpers.await
//import uk.gov.hmrc.disareturns.models.common.Month
//import uk.gov.hmrc.disareturns.models.initiate.inboundRequest.{InitiateSubmission, TaxYear}
//import uk.gov.hmrc.disareturns.models.initiate.mongo.SubmissionRequest
//import uk.gov.hmrc.disareturns.repositories.InitiateSubmissionRepository
//import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
//import utils.BaseUnitSpec
//
//import java.time.Instant
//import java.time.temporal.ChronoUnit
//
//class InitiateSubmissionRepositorySpec extends BaseUnitSpec
//  with DefaultPlayMongoRepositorySupport[InitiateSubmission] with IntegrationPatience {
//  override lazy val repository: InitiateSubmissionRepository = new InitiateSubmissionRepository(mongoComponent)
//
//  override def beforeEach(): Unit = {
//    super.beforeEach()
//    await(repository.dropCollection())
//    await(repository.ensureIndexes())
//  }
//
//  val testSubmission: InitiateSubmission = InitiateSubmission(
//    returnId = "test-return-id",
//    boxId = "test-box-id",
//    isaManagerReference = "test-isa-ref",
//    submissionRequest = SubmissionRequest(
//      totalRecords = 1000,
//      submissionPeriod = Month.JAN,
//      taxYear = TaxYear(2025)),
//    createdAt = Instant.now.truncatedTo(ChronoUnit.MILLIS)
//  )
//
//  "insert" should {
//    "store a submission and return the returnId" in new testSetup {
//      val result = repository.insert(testSubmission).futureValue
//      result shouldBe testSubmission.returnId
//
//      val stored = repository.collection.find().toFuture().futureValue.headOption
//      stored shouldBe Some(testSubmission)
//    }
//  }
//
//  "findByIsaManagerReference" should {
//    "return the matching document when found" in new testSetup {
//      repository.insert(testSubmission).futureValue
//
//      val result = repository.findByIsaManagerReference("Z123456").futureValue
//      result shouldBe Some(testSubmission)
//    }
//
//    "return None when no document matches" in {
//      val result = repository.findByIsaManagerReference("NON_EXISTENT").futureValue
//      result shouldBe None
//    }
//  }
//
//  "dropCollection" should {
//    "remove all documents from the collection" in new testSetup {
//      repository.insert(testSubmission).futureValue
//
//      repository.dropCollection().futureValue
//
//      val result = await(repository.findByIsaManagerReference("Z123456"))
//      result shouldBe None
//    }
//  }
//
//  class testSetup {
//    await(repository.dropCollection())
//    await(repository.ensureIndexes())
//  }
//}
