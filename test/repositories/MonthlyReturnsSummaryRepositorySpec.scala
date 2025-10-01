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
import uk.gov.hmrc.disareturns.models.common.Month
import uk.gov.hmrc.disareturns.models.summary.TaxYear
import uk.gov.hmrc.disareturns.models.summary.repository.MonthlyReturnsSummary
import uk.gov.hmrc.disareturns.repositories.MonthlyReturnsSummaryRepository
import uk.gov.hmrc.mongo.MongoComponent
import utils.BaseUnitSpec

class MonthlyReturnsSummaryRepositorySpec extends BaseUnitSpec {
  protected val databaseName:     String         = "disa-returns-summary-test"
  protected val mongoUri:         String         = s"mongodb://127.0.0.1:27017/$databaseName"
  lazy val mongoComponentForTest: MongoComponent = MongoComponent(mongoUri)
  val appConfig = app.injector.instanceOf[AppConfig]

  protected val repository: MonthlyReturnsSummaryRepository =
    new MonthlyReturnsSummaryRepository(mongoComponentForTest, appConfig)

  "upsert" should {

    "insert a new MonthlyReturnsSummary document when it does not exist" in new TestSetup {
      val doc = MonthlyReturnsSummary(zRef = "Z1234", taxYear = TaxYear("2026-27"), month = Month.SEP, totalRecords = 3)

      await(repository.upsert(doc))

      val stored = await(repository.collection.find().toFuture())
      stored must have size 1
      stored.head.zRef mustBe "Z1234"
      stored.head.taxYear mustBe TaxYear("2026-27")
      stored.head.month mustBe Month.SEP
      stored.head.totalRecords mustBe 3
    }

    "replace the existing document and update fields" in new TestSetup {
      val keyZRef    = "Z2222"
      val keyTaxYear = TaxYear("2026-27")
      val keyMonth   = Month.JAN

      val original = MonthlyReturnsSummary(zRef = keyZRef, taxYear = keyTaxYear, month = keyMonth, totalRecords = 2)

      val updated = original.copy(totalRecords = 9)

      await(repository.upsert(original))
      await(repository.upsert(updated))

      val stored = await(repository.collection.find().toFuture())

      stored must have size 1
      stored.head.totalRecords mustBe 9
    }
  }

  class TestSetup {
    await(repository.collection.drop().toFuture())
  }
}
