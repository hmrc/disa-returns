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
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.submission.{ErrorVolume, InvalidSubmission, ValidatedSubmission, ValidationMetrics}
import uk.gov.hmrc.disareturns.services.StreamingParserService
import utils.BaseUnitSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.{Clock, Duration, Instant}
import org.mockito.Mockito.when
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile, TemporaryFileCreator}
import scala.concurrent.ExecutionContext
import scala.util.Try

class StreamingParserServiceSpec extends BaseUnitSpec {

  trait Setup {
    implicit val ec:           ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val materializer: Materializer     = app.materializer

    var lastCreatedPath: Option[Path] = None

    val capturingCreator: TemporaryFileCreator = new TemporaryFileCreator {
      override def create(prefix: String, suffix: String): TemporaryFile = {
        val file = SingletonTemporaryFileCreator.create(prefix, suffix)
        lastCreatedPath = Some(file.path)
        file
      }
      override def create(path: Path): TemporaryFile =
        SingletonTemporaryFileCreator.create(path)
      override def delete(file: TemporaryFile): Try[Boolean] =
        SingletonTemporaryFileCreator.delete(file)
    }

    val validationStarted: Instant = Instant.parse("2026-10-15T12:00:00Z")
    val clock:             Clock   = mock[Clock]
    when(clock.instant()).thenReturn(validationStarted, validationStarted.plusMillis(250))

    val service = new StreamingParserService(capturingCreator, clock)
  }

  val validIsaAccountJson: String =
    """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn":250.00,"amountTransferredOut":2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"flexibleIsa":false}""".stripMargin

  val validIsaAccountJson2: String =
    """{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First2","middleName":null,"lastName":"Last2","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn":850.00,"amountTransferredOut":500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"flexibleIsa":false}""".stripMargin

  private def readLines(path: Path): Seq[String] =
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8).linesIterator.toSeq.filter(_.nonEmpty)

  "processToTempFile" should {

    "return a Right with a temp file containing the valid line" in new Setup {
      val source: Source[ByteString, NotUsed]                    = Source.single(ByteString(validIsaAccountJson + "\n"))
      val result: Either[InvalidSubmission, ValidatedSubmission] = service.processToTempFile(source).futureValue

      result.isRight shouldBe true
      val tempFile = result.toOption.get.file
      val lines    = readLines(tempFile.path)
      lines        should have size 1
      lines.head shouldBe validIsaAccountJson
      tempFile.delete()
    }

    "write all valid lines to temp file for NDJSON payload with trailing newline" in new Setup {
      val payload: ByteString = ByteString(s"$validIsaAccountJson\n$validIsaAccountJson2\n")

      val result: Either[InvalidSubmission, ValidatedSubmission] = service.processToTempFile(Source.single(payload)).futureValue

      result.isRight shouldBe true
      val tempFile = result.toOption.get.file
      val lines    = readLines(tempFile.path)
      lines        should have size 2
      lines.head shouldBe validIsaAccountJson
      lines(1)   shouldBe validIsaAccountJson2
      tempFile.delete()
    }

    "write all valid lines to temp file for NDJSON payload without trailing newline" in new Setup {
      val payload: ByteString = ByteString(s"$validIsaAccountJson\n$validIsaAccountJson2")

      val result: Either[InvalidSubmission, ValidatedSubmission] = service.processToTempFile(Source.single(payload)).futureValue

      result.isRight shouldBe true
      val tempFile = result.toOption.get.file
      val lines    = readLines(tempFile.path)
      lines        should have size 2
      lines.head shouldBe validIsaAccountJson
      lines(1)   shouldBe validIsaAccountJson2
      tempFile.delete()
    }

    "return a Left and delete the temp file when IsaAccount fails domain validation" in new Setup {
      val invalidIsaAccountJson1: String =
        """{"accountNumber":"STD000001","nino":"AB000001C","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","flexibleIsa":false}"""
      val invalidIsaAccountJson2: String =
        """{"accountNumber":"STD000002","nino":"AB000001C","firstName":"Jeff","middleName":null,"dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","flexibleIsa":false}"""

      val source: Source[ByteString, NotUsed] = Source.single(ByteString(invalidIsaAccountJson1 + "\n" + invalidIsaAccountJson2 + "\n"))
      val result: Either[InvalidSubmission, ValidatedSubmission] = service.processToTempFile(source).futureValue

      result.isLeft                     shouldBe true
      Files.exists(lastCreatedPath.get) shouldBe false

      result.left.toOption.get.error match {
        case SecondLevelValidationFailure(err :: err2 :: _) =>
          err.code           shouldBe "MISSING_FIRST_NAME"
          err.message        shouldBe "First name field is missing"
          err.nino           shouldBe "AB000001C"
          err.accountNumber  shouldBe "STD000001"
          err2.code          shouldBe "MISSING_LAST_NAME"
          err2.message       shouldBe "Last name field is missing"
          err2.nino          shouldBe "AB000001C"
          err2.accountNumber shouldBe "STD000002"
        case other =>
          fail(s"Unexpected validation error: $other")
      }
    }

    "return a Left and delete the temp file when NINO or account number is missing" in new Setup {
      val invalidJson = """{ "nothing": "wrong" }"""
      val source: Source[ByteString, NotUsed]                    = Source.single(ByteString(invalidJson + "\n"))
      val result: Either[InvalidSubmission, ValidatedSubmission] = service.processToTempFile(source).futureValue

      result.isLeft                     shouldBe true
      Files.exists(lastCreatedPath.get) shouldBe false

      result.left.toOption.get.error match {
        case FirstLevelValidationFailure(err) =>
          err shouldBe NinoOrAccountNumMissingErr
        case other =>
          fail(s"Unexpected validation error: $other")
      }
    }

    "return a Left with EmptyPayload error for an empty source" in new Setup {
      val source: Source[ByteString, NotUsed]                    = Source.single(ByteString(""))
      val result: Either[InvalidSubmission, ValidatedSubmission] = service.processToTempFile(source).futureValue

      result.isLeft shouldBe true

      result.left.toOption.get.error match {
        case FirstLevelValidationFailure(err) =>
          err shouldBe EmptyPayload
        case other =>
          fail(s"Unexpected validation error: $other")
      }
    }
  }

  "processToTempFile metrics" should {

    val missingFirstNameJson =
      """{"accountNumber":"STD000003","nino":"AB000003C","middleName":null,"lastName":"Last3","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"flexibleIsa":false}"""
    val missingNinoJson = """{"accountNumber":"STD000004"}"""

    "count entries, request size and validation time for a valid payload with a trailing newline" in new Setup {
      val payload: ByteString = ByteString(s"$validIsaAccountJson\n$validIsaAccountJson2\n")

      val result: ValidatedSubmission = service.processToTempFile(Source.single(payload)).futureValue.toOption.get

      result.metrics shouldBe ValidationMetrics(numberOfEntries = 2, requestSize = payload.length.toLong, Duration.ofMillis(250))
      result.file.delete()
    }

    "count entries for a valid payload without a trailing newline" in new Setup {
      val payload: ByteString = ByteString(s"$validIsaAccountJson\n$validIsaAccountJson2")

      val result: ValidatedSubmission = service.processToTempFile(Source.single(payload)).futureValue.toOption.get

      result.metrics.numberOfEntries shouldBe 2
      result.metrics.requestSize     shouldBe payload.length.toLong
      result.file.delete()
    }

    "not count blank lines as entries but include them in the request size" in new Setup {
      val payload: ByteString = ByteString(s"\n$validIsaAccountJson\n   \n\n$validIsaAccountJson2\n\n")

      val result: ValidatedSubmission = service.processToTempFile(Source.single(payload)).futureValue.toOption.get

      result.metrics.numberOfEntries shouldBe 2
      result.metrics.requestSize     shouldBe payload.length.toLong
      readLines(result.file.path)    shouldBe Seq(validIsaAccountJson, validIsaAccountJson2)
      result.file.delete()
    }

    "sum the request size across every chunk received" in new Setup {
      val (firstHalf, secondHalf) = s"$validIsaAccountJson\n$validIsaAccountJson2\n".splitAt(100)
      val source: Source[ByteString, NotUsed] = Source(List(ByteString(firstHalf), ByteString(secondHalf)))

      val result: ValidatedSubmission = service.processToTempFile(source).futureValue.toOption.get

      result.metrics.numberOfEntries shouldBe 2
      result.metrics.requestSize     shouldBe (firstHalf.length + secondHalf.length).toLong
      result.file.delete()
    }

    "count valid and invalid entries and group every error by type for a mixed payload" in new Setup {
      val payload: ByteString =
        ByteString(s"$validIsaAccountJson\n$missingFirstNameJson\n$missingNinoJson\n$missingFirstNameJson\n$validIsaAccountJson2\n")

      val result: InvalidSubmission = service.processToTempFile(Source.single(payload)).futureValue.left.toOption.get

      result.error   shouldBe FirstLevelValidationFailure(NinoOrAccountNumMissingErr)
      result.metrics shouldBe ValidationMetrics(numberOfEntries = 5, requestSize = payload.length.toLong, Duration.ofMillis(250))
      result.errorVolumes shouldBe Seq(
        ErrorVolume("MISSING_FIRST_NAME", 2),
        ErrorVolume("MISSING_AMOUNT_TRANSFERRED_IN", 2),
        ErrorVolume("MISSING_AMOUNT_TRANSFERRED_OUT", 2),
        ErrorVolume(NinoOrAccountNumMissingErr.code, 1)
      )
      Files.exists(lastCreatedPath.get) shouldBe false
    }

    "count every error on a line in errorVolumes but return only the first error per line in the response" in new Setup {
      val missingFirstAndLastNameJson =
        """{"accountNumber":"STD000005","nino":"AB000005C","middleName":null,"dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn":250.00,"amountTransferredOut":2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"flexibleIsa":false}"""
      val payload: ByteString = ByteString(s"$missingFirstAndLastNameJson\n$validIsaAccountJson\n")

      val result: InvalidSubmission = service.processToTempFile(Source.single(payload)).futureValue.left.toOption.get

      result.error match {
        case SecondLevelValidationFailure(errors) =>
          errors                      should have size 1
          errors.head.accountNumber shouldBe "STD000005"
        case other =>
          fail(s"Unexpected validation error: $other")
      }
      result.errorVolumes should contain theSameElementsAs Seq(
        ErrorVolume("MISSING_FIRST_NAME", 1),
        ErrorVolume("MISSING_LAST_NAME", 1)
      )
    }

    "report zero entries and a single EmptyPayload error for an empty payload" in new Setup {
      val payload: ByteString = ByteString("\n  \n")

      val result: InvalidSubmission = service.processToTempFile(Source.single(payload)).futureValue.left.toOption.get

      result.error                   shouldBe FirstLevelValidationFailure(EmptyPayload)
      result.metrics.numberOfEntries shouldBe 0
      result.metrics.requestSize     shouldBe payload.length.toLong
      result.errorVolumes            shouldBe Seq(ErrorVolume(EmptyPayload.code, 1))
    }

    "report zero entries and zero request size for a request with no body" in new Setup {
      val result: InvalidSubmission = service.processToTempFile(Source.empty[ByteString]).futureValue.left.toOption.get

      result.metrics.numberOfEntries shouldBe 0
      result.metrics.requestSize     shouldBe 0
      result.errorVolumes            shouldBe Seq(ErrorVolume(EmptyPayload.code, 1))
    }
  }
}
