package repositories

import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.submission.isaAccounts.{IsaType, LifetimeIsaTransferAndClosure, MonthlyReportDocument}
import uk.gov.hmrc.disareturns.repositories.ReportingRepository
import uk.gov.hmrc.mongo.MongoComponent
import utils.BaseUnitSpec

class ReportingRepositorySpec extends BaseUnitSpec {

  protected val databaseName = "disa-reporting-test"
  protected val mongoUri: String = s"mongodb://127.0.0.1:27017/$databaseName"
  lazy val mongoComponentForTest: MongoComponent = MongoComponent(mongoUri)

  protected val repository: ReportingRepository =
    new ReportingRepository(mongoComponentForTest)

  "insertBatch" should {
    "insert a batch of IsaAccounts wrapped in MonthlyReportDocument" in new TestSetup {
      await(repository.insertBatch("Z123456", "return-001", testIsaAccounts))

      val stored: Seq[MonthlyReportDocument] = await(repository.collection.find().toFuture())
      stored should have size 1
      stored.head.returnId shouldBe "return-001"
      stored.head.isaManagerReferenceNumber shouldBe "Z123456"
      stored.head.isaReport shouldBe testIsaAccounts
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

    val testIsaAccounts: Seq[LifetimeIsaTransferAndClosure] = Seq(LifetimeIsaTransferAndClosure(
      accountNumber = "LISA123456",
      nino = "AB123456C",
      firstName = "Alice",
      middleName = Some("Jane"),
      lastName = "Smith",
      dateOfBirth = "1990-05-15",
      isaType = IsaType.CASH,
      reportingATransfer = true,
      dateOfLastSubscription = "2025-06-30",
      totalCurrentYearSubscriptionsToDate = BigDecimal(4000),
      marketValueOfAccount = BigDecimal(25000),
      accountNumberOfTransferringAccount = "OLDLISA789",
      amountTransferred = BigDecimal(10000),
      dateOfFirstSubscription = "2018-04-06",
      closureDate = "2025-07-15",
      reasonForClosure = "Death of investor",
      lisaQualifyingAddition = BigDecimal(500),
      lisaBonusClaim = true
    ))

    await(repository.dropCollection())
    await(repository.ensureIndexes())
  }
}