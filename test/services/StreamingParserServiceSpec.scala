package services

import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.submission.isaAccounts.{IsaAccount, IsaType, LifetimeIsaTransferAndClosure}
import uk.gov.hmrc.disareturns.repositories.ReportingRepository
import uk.gov.hmrc.disareturns.services.StreamingParserService
import utils.BaseUnitSpec

import scala.concurrent.{ExecutionContext, Future}

class StreamingParserServiceSpec extends BaseUnitSpec
  with MockitoSugar
  with BeforeAndAfterEach {

  trait Setup {
    implicit val ec: ExecutionContext       = scala.concurrent.ExecutionContext.Implicits.global
    val mockReportingRepository: ReportingRepository = mock[ReportingRepository]
    implicit val materializer: Materializer = app.materializer

    val service = new StreamingParserService(mockReportingRepository, materializer)
  }

  "validatedStream" should {

    "return a Right when input is a valid IsaAccount" in new Setup {
      val validIsaAccountJson: String =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}""".stripMargin

      val source = Source.single(ByteString(validIsaAccountJson + "\n"))
      val result = service.validatedStream(source)
        .runFold(Seq.empty[Either[SecondLevelValidationError, IsaAccount]])(_ :+ _)
        .futureValue

      result should have size 1
      result.head.isRight shouldBe true
    }

    "return a Left when IsaAccount fails domain validation with missing first name" in new Setup {
      val invalidIsaAccountJson: String =
        """{"accountNumber":"STD000001","nino":"AB000001C","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}""".stripMargin

      val source = Source.single(ByteString(invalidIsaAccountJson + "\n"))
      val result = service.validatedStream(source).runFold(Seq.empty[Either[SecondLevelValidationError, IsaAccount]])(_ :+ _).futureValue

      result should have size 1
      result.head.isLeft shouldBe true
      result.head.left.get.code shouldBe "MISSING_FIRST_NAME"
      result.head.left.get.message shouldBe "First name field is missing"
    }

    "throw FirstLevelValidationException when NINO or account number is missing" in new Setup {
      val invalidJson = """{ "nothing": "wrong" }"""
      val source      = Source.single(ByteString(invalidJson + "\n"))

      val result = service.validatedStream(source).runWith(Sink.seq)

      val thrown = the[FirstLevelValidationException] thrownBy {
        await(result)
      }

      thrown.error shouldBe NinoOrAccountNumMissingErr
    }
  }

  "processValidatedStream" should {

    "call insertBatch with valid accounts and succeed when no errors" in new Setup {
      val testIsaAccount = LifetimeIsaTransferAndClosure(
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
      )
      when(mockReportingRepository.insertBatch(any(), any(), any()))
        .thenReturn(Future.successful(Done))

      val source = Source(List(Right(testIsaAccount)))
      val result = service.processValidatedStream("manager1", "return1", source).futureValue

      result shouldBe Done
      verify(mockReportingRepository).insertBatch("manager1", "return1", Seq(testIsaAccount))
    }

    "fail with SecondLevelValidationException when there are validation errors" in new Setup {
      val error = SecondLevelValidationError("AA000003A", "ACC123", "INVALID_TYPE", "Invalid account type")
      val source = Source(List(Left(error)))

      val thrown = the[SecondLevelValidationException] thrownBy {
        await(service.processValidatedStream("manager1", "return1", source))
      }

      thrown.error.errors should contain(error)
      verify(mockReportingRepository, never()).insertBatch(any(), any(), any())
    }

//    "insert batch only when all entries are valid" in new Setup {
//      val isa1 = IsaAccount("AA000003A", "ACC1", "cash")
//      val isa2 = IsaAccount("AA000003A", "ACC2", "cash")
//
//      when(mockReportingRepository.insertBatch(any(), any(), any()))
//        .thenReturn(Future.successful(Done))
//
//      val source = Source(List(Right(isa1), Right(isa2)))
//      val result = service.processValidatedStream("managerX", "returnX", source).futureValue
//
//      result shouldBe Done
//      verify(mockReportingRepository).insertBatch("managerX", "returnX", Seq(isa1, isa2))
//    }
  }
}
