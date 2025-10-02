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

import org.scalatest.matchers.must.Matchers._
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.models.summary.repository.MonthlyReturnsSummary
import uk.gov.hmrc.disareturns.repositories.MonthlyReturnsSummaryRepository
import uk.gov.hmrc.mongo.MongoComponent
import utils.BaseUnitSpec

class MonthlyReturnsSummaryRepositorySpec extends BaseUnitSpec {
  protected val databaseName:     String         = "disa-returns-summary-test"
  protected val mongoUri:         String         = s"mongodb://127.0.0.1:27017/$databaseName"
  lazy val mongoComponentForTest: MongoComponent = MongoComponent(mongoUri)
  private val appConfig = app.injector.instanceOf[AppConfig]

  protected val repository: MonthlyReturnsSummaryRepository =
    new MonthlyReturnsSummaryRepository(mongoComponentForTest, appConfig)

  override def beforeEach(): Unit = await(repository.collection.drop().toFuture())

  "retrieveReturnSummary" should {

    "find a summary with matching details" in {
      val doc = MonthlyReturnsSummary(zRef = validZRef, taxYear = validTaxYear, month = validMonth, totalRecords = 3)

      await(repository.collection.insertOne(doc).toFuture())

      val result = await(repository.retrieveReturnSummary(validZRef, validTaxYear, validMonth))

      result.head.zRef mustBe validZRef
      result.head.taxYear mustBe validTaxYear
      result.head.month mustBe validMonth
      result.head.totalRecords mustBe 3
    }
  }

  "upsert" should {

    "insert a new MonthlyReturnsSummary document when it does not exist" in {
      val doc = MonthlyReturnsSummary(zRef = validZRef, taxYear = validTaxYear, month = validMonth, totalRecords = 3)

      await(repository.upsert(doc))

      val stored = await(repository.collection.find().toFuture())
      stored must have size 1
      stored.head.zRef mustBe validZRef
      stored.head.taxYear mustBe validTaxYear
      stored.head.month mustBe validMonth
      stored.head.totalRecords mustBe 3
    }

    "replace the existing document and update fields" in {
      val original = MonthlyReturnsSummary(zRef = validZRef, taxYear = validTaxYear, month = validMonth, totalRecords = 2)

      val updated = original.copy(totalRecords = 9)

      await(repository.upsert(original))
      await(repository.upsert(updated))

      val stored = await(repository.collection.find().toFuture())

      stored must have size 1
      stored.head.totalRecords mustBe 9
    }
  }
}
