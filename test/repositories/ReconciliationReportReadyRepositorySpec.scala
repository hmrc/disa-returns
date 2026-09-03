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

import org.mongodb.scala.ObservableFuture
import org.scalatest.matchers.must.Matchers.*
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.models.callback.ReconciliationReportReady
import uk.gov.hmrc.disareturns.repositories.ReconciliationReportReadyRepository
import uk.gov.hmrc.mongo.MongoComponent
import utils.BaseUnitSpec

class ReconciliationReportReadyRepositorySpec extends BaseUnitSpec {
  protected val databaseName:     String         = "disa-returns-reconciliation-report-ready-test"
  protected val mongoUri:         String         = s"mongodb://127.0.0.1:27017/$databaseName"
  lazy val mongoComponentForTest: MongoComponent = MongoComponent(mongoUri)
  private val appConfig = app.injector.instanceOf[AppConfig]

  protected val repository: ReconciliationReportReadyRepository =
    new ReconciliationReportReadyRepository(mongoComponentForTest, appConfig)

  override def beforeEach(): Unit = await(repository.collection.drop().toFuture())

  "findByZReference" should {

    "find callback data with matching details" in {
      val doc = ReconciliationReportReady(zRef = validZReference, totalRecords = 3)

      await(repository.collection.insertOne(doc).toFuture())

      val result = await(repository.findByZReference(validZReference))

      result.head.zRef mustBe validZReference
      result.head.totalRecords mustBe 3
    }
  }

  "upsert" should {

    "insert a new ReconciliationReportReady document when it does not exist" in {
      val doc = ReconciliationReportReady(zRef = validZReference, totalRecords = 3)

      await(repository.upsert(doc))

      val stored = await(repository.collection.find().toFuture())
      stored must have size 1
      stored.head.zRef mustBe validZReference
      stored.head.totalRecords mustBe 3
    }

    "replace the existing document and update fields" in {
      val original = ReconciliationReportReady(zRef = validZReference, totalRecords = 2)

      val updated = original.copy(totalRecords = 9)

      await(repository.upsert(original))
      val originallyStored = await(repository.collection.find().head())
      await(repository.upsert(updated))

      val stored = await(repository.collection.find().toFuture())

      stored must have size 1
      stored.head.totalRecords mustBe 9
      stored.head.createdAt mustBe originallyStored.createdAt
      stored.head.updatedAt must be >= originallyStored.updatedAt
    }
  }

  "deleteByZReferences" should {
    "delete only callback data for the supplied Z-references" in {
      await(repository.upsert(ReconciliationReportReady(zRef = validZReference, totalRecords = 3)))
      await(repository.upsert(ReconciliationReportReady(zRef = "Z5678", totalRecords = 4)))

      await(repository.deleteByZReferences(Seq(validZReference)))

      await(repository.findByZReference(validZReference)) mustBe None
      await(repository.findByZReference("Z5678")) must not be empty
    }
  }
}
