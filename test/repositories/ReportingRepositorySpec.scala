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
import uk.gov.hmrc.disareturns.models.submission.isaAccounts.{IsaType, LifetimeIsaTransferAndClosure, MonthlyReportDocument, ReasonForClosure}
import uk.gov.hmrc.disareturns.repositories.ReportingRepository
import uk.gov.hmrc.mongo.MongoComponent
import utils.BaseUnitSpec

import java.time.LocalDate

class ReportingRepositorySpec extends BaseUnitSpec {

  protected val databaseName = "disa-reporting-test"
  protected val mongoUri:         String         = s"mongodb://127.0.0.1:27017/$databaseName"
  lazy val mongoComponentForTest: MongoComponent = MongoComponent(mongoUri)

  protected val repository: ReportingRepository =
    new ReportingRepository(mongoComponentForTest)

  "insertBatch" should {
    "insert a batch of IsaAccounts wrapped in MonthlyReportDocument" in new TestSetup {
      await(repository.insertBatch("Z123456", "return-001", testIsaAccounts))

      val stored: Seq[MonthlyReportDocument] = await(repository.collection.find().toFuture())
      stored                                  should have size 1
      stored.head.returnId                  shouldBe "return-001"
      stored.head.isaManagerReferenceNumber shouldBe "Z123456"
      stored.head.isaReport                 shouldBe testIsaAccounts
    }
  }

  "dropCollection" should {
    "drop all documents in the collection" in new TestSetup {
      await(repository.insertBatch("Z123456", "return-001", testIsaAccounts))

      await(repository.dropCollection())

      val stored: Seq[MonthlyReportDocument] = await(repository.collection.find().toFuture())
      stored shouldBe empty
    }
  }

  class TestSetup {

    val testIsaAccounts: Seq[LifetimeIsaTransferAndClosure] = Seq(
      LifetimeIsaTransferAndClosure(
        accountNumber = "LISA123456",
        nino = "AB123456C",
        firstName = "Alice",
        middleName = Some("Jane"),
        lastName = "Smith",
        dateOfBirth = LocalDate.of(1994, 7, 7),
        isaType = IsaType.CASH,
        reportingATransfer = true,
        dateOfLastSubscription = LocalDate.of(2023, 7, 7),
        totalCurrentYearSubscriptionsToDate = BigDecimal(4000),
        marketValueOfAccount = BigDecimal(25000),
        accountNumberOfTransferringAccount = "OLDLISA789",
        amountTransferred = BigDecimal(10000),
        dateOfFirstSubscription = LocalDate.of(2022, 7, 7),
        closureDate = LocalDate.of(2024, 7, 7),
        reasonForClosure = ReasonForClosure.CLOSED,
        lisaQualifyingAddition = BigDecimal(500),
        lisaBonusClaim = 1000.00
      )
    )

    await(repository.dropCollection())
    await(repository.ensureIndexes())
  }
}
