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

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import org.mockito.Mockito._
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.submission.isaAccounts.{IsaAccount, IsaType, LifetimeIsaTransferAndClosure, ReasonForClosure}
import uk.gov.hmrc.disareturns.services.StreamingParserService
import utils.BaseUnitSpec

import java.time.LocalDate
import scala.concurrent.ExecutionContext

class StreamingParserServiceSpec extends BaseUnitSpec {

  trait Setup {
    implicit val ec:           ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val materializer: Materializer     = app.materializer
    val service = new StreamingParserService

    val testIsaAccount: LifetimeIsaTransferAndClosure = LifetimeIsaTransferAndClosure(
      accountNumber = "LISA123456",
      nino = "AB123456C",
      firstName = "Alice",
      middleName = Some("Jane"),
      lastName = "Smith",
      dateOfBirth = LocalDate.of(1994, 7, 7),
      isaType = IsaType.CASH,
      reportingATransfer = true,
      dateOfLastSubscription = LocalDate.of(2022, 7, 7),
      totalCurrentYearSubscriptionsToDate = BigDecimal(4000),
      marketValueOfAccount = BigDecimal(25000),
      accountNumberOfTransferringAccount = "OLDLISA789",
      amountTransferred = BigDecimal(10000),
      dateOfFirstSubscription = LocalDate.of(2018, 7, 7),
      closureDate = LocalDate.of(2023, 7, 7),
      reasonForClosure = ReasonForClosure.VOID,
      lisaQualifyingAddition = BigDecimal(500),
      lisaBonusClaim = BigDecimal("1000.00")
    )
  }

  "validatedStream" should {

    "return a Right when input is a valid IsaAccount" in new Setup {
      val validIsaAccountJson: String =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val source: Source[ByteString, NotUsed]              = Source.single(ByteString(validIsaAccountJson + "\n"))
      val result: Seq[Either[ValidationError, IsaAccount]] = service.validatedStream(source).runWith(Sink.seq).futureValue

      result                should have size 1
      result.head.isRight shouldBe true
    }

    "return a Left when IsaAccount fails domain validation with missing first name" in new Setup {
      val invalidIsaAccountJson: String =
        """{"accountNumber":"STD000001","nino":"AB000001C","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val source: Source[ByteString, NotUsed]              = Source.single(ByteString(invalidIsaAccountJson + "\n"))
      val result: Seq[Either[ValidationError, IsaAccount]] = service.validatedStream(source).runWith(Sink.seq).futureValue

      result        should have size 1
      result.head shouldBe a[Left[_, _]]

      result.head.swap.toOption match {
        case Some(SecondLevelValidationFailure(err +: _)) =>
          err.code          shouldBe "MISSING_FIRST_NAME"
          err.message       shouldBe "First name field is missing"
          err.nino          shouldBe "AB000001C"
          err.accountNumber shouldBe "STD000001"
        case other =>
          fail(s"Unexpected validation error: $other")
      }
    }

    "return a Left when NINO or account number is missing" in new Setup {
      val invalidJson = """{ "nothing": "wrong" }"""
      val source: Source[ByteString, NotUsed]              = Source.single(ByteString(invalidJson + "\n"))
      val result: Seq[Either[ValidationError, IsaAccount]] = service.validatedStream(source).runWith(Sink.seq).futureValue

      result        should have size 1
      result.head shouldBe a[Left[_, _]]

      result.head.left.get match {
        case FirstLevelValidationFailure(err) =>
          err shouldBe NinoOrAccountNumMissingErr
        case other =>
          fail(s"Unexpected validation error: $other")
      }
    }
  }

  "processValidatedStream" should {

    "return Left with SecondLevelValidationException when there are validation errors" in new Setup {
      val error: SecondLevelValidationError = SecondLevelValidationError("AA000003A", "ACC123", "INVALID_TYPE", "Invalid account type")
      val source: Source[Left[ValidationError, Nothing], NotUsed] =
        Source(List(Left(SecondLevelValidationFailure(Seq(error)))))

      val result = await(service.processValidatedStream(source))

      result.left.toOption.get match {
        case SecondLevelValidationFailure(errs) => errs should contain(error)
        case other                              => fail(s"Unexpected validation error: $other")
      }
    }

    "return Right with ISA Accounts only when all entries are valid" in new Setup {
      val isa1: LifetimeIsaTransferAndClosure = testIsaAccount
      val isa2: LifetimeIsaTransferAndClosure = testIsaAccount.copy(accountNumber = "test1234")

      val source: Source[Right[Nothing, LifetimeIsaTransferAndClosure], NotUsed] =
        Source(List(Right(isa1), Right(isa2)))

      val result = service.processValidatedStream(source).futureValue

      result shouldBe Right(Seq(isa1, isa2))
    }
  }
}
