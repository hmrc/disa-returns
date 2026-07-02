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

package uk.gov.hmrc.disareturns.services

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{FileIO, Flow, Framing, Keep, Sink, Source}
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.libs.Files.{TemporaryFile, TemporaryFileCreator}
import play.api.libs.json._
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaAccount
import uk.gov.hmrc.disareturns.utils.JsonErrorMapper.jsErrorToDomainError
import uk.gov.hmrc.disareturns.utils.JsonValidation
import uk.gov.hmrc.disareturns.utils.JsonValidation.findDuplicateFields

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StreamingParserService @Inject() (temporaryFileCreator: TemporaryFileCreator)(implicit val mat: Materializer, ec: ExecutionContext)
    extends Logging {

  private def validateSecondLevel(line: String, jsValue: JsValue, nino: String, accountNumber: String): Either[ValidationError, String] = {
    val combinedValidation: JsResult[IsaAccount] = findDuplicateFields(line).flatMap { _ =>
      jsValue.validate[IsaAccount]
    }
    combinedValidation match {
      case JsSuccess(_, _) =>
        Right(line)
      case JsError(errors) =>
        val domainErrors = jsErrorToDomainError(errors, nino, accountNumber).headOption.toSeq
        Left(SecondLevelValidationFailure(domainErrors))
    }
  }

  private def validatedLinesStream(source: Source[ByteString, _]): Source[Either[ValidationError, String], _] = {
    val rawValidated = source
      .via(Framing.delimiter(ByteString("\n"), 65536, allowTruncation = true))
      .map(_.utf8String.trim)
      .filter(_.nonEmpty)
      .map { line =>
        JsonValidation.ensureValidNDJson(line) match {
          case Right(jsValue) =>
            JsonValidation.firstLevelValidatorExtractNinoAndAccount(jsValue) match {
              case Right((nino, accountNumber)) =>
                validateSecondLevel(line, jsValue, nino, accountNumber)
              case Left(firstLevelErr) =>
                Left(FirstLevelValidationFailure(firstLevelErr))
            }
          case Left(error) =>
            Left(FirstLevelValidationFailure(error: ErrorResponse))
        }
      }

    rawValidated.prefixAndTail(1).flatMapConcat {
      case (Seq(), _) =>
        Source.single(Left(FirstLevelValidationFailure(EmptyPayload)))
      case (Seq(first), tail) =>
        tail.prepend(Source.single(first))
    }
  }

  private def aggregateErrors(errors: Seq[ValidationError]): Left[ValidationError, TemporaryFile] = {
    val firstLevelErrors  = errors.collect { case FirstLevelValidationFailure(err) => err }
    val secondLevelErrors = errors.collect { case SecondLevelValidationFailure(err) => err }.flatten

    if (firstLevelErrors.nonEmpty) Left(FirstLevelValidationFailure(firstLevelErrors.head))
    else Left(SecondLevelValidationFailure(secondLevelErrors.toList))
  }

  def processToTempFile(source: Source[ByteString, _]): Future[Either[ValidationError, TemporaryFile]] = {
    val tempFile = temporaryFileCreator.create("disa-returns-", ".ndjson")

    val errorSink: Sink[Either[ValidationError, String], Future[Seq[ValidationError]]] =
      Flow[Either[ValidationError, String]]
        .collect { case Left(err) => err }
        .toMat(Sink.seq)(Keep.right)

    val (errorsFuture, ioResultFuture) =
      validatedLinesStream(source)
        .alsoToMat(errorSink)(Keep.right)
        .collect { case Right(line) => ByteString(line + "\n") }
        .toMat(FileIO.toPath(tempFile.path))(Keep.both)
        .run()

    (for {
      errors <- errorsFuture
      _      <- ioResultFuture
    } yield
      if (errors.nonEmpty) {
        tempFile.delete()
        aggregateErrors(errors)
      } else {
        Right(tempFile)
      }).recover { case ex =>
      tempFile.delete()
      throw ex
    }
  }
}
