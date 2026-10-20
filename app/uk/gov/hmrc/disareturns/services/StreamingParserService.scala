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
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json._
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaAccount
import uk.gov.hmrc.disareturns.models.submission.{ErrorVolume, InvalidSubmission, ValidatedSubmission, ValidationMetrics}
import uk.gov.hmrc.disareturns.utils.JsonErrorMapper.jsErrorToDomainError
import uk.gov.hmrc.disareturns.utils.JsonValidation
import uk.gov.hmrc.disareturns.utils.JsonValidation.findDuplicateFields

import java.time.{Clock, Duration}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StreamingParserService @Inject() (temporaryFileCreator: TemporaryFileCreator, clock: Clock, appConfig: AppConfig)(implicit
  val mat:                                                    Materializer,
  ec:                                                         ExecutionContext
) extends Logging {

  private def validateSecondLevel(line: String, jsValue: JsValue, nino: String, accountNumber: String): Either[ValidationError, String] = {
    val combinedValidation: JsResult[IsaAccount] = findDuplicateFields(line).flatMap { _ =>
      jsValue.validate[IsaAccount]
    }
    combinedValidation match {
      case JsSuccess(_, _) =>
        Right(line)
      case JsError(errors) =>
        Left(SecondLevelValidationFailure(jsErrorToDomainError(errors, nino, accountNumber).toSeq))
    }
  }

  private def validatedLinesFlow: Flow[ByteString, Either[ValidationError, String], Future[Long]] = {
    val entriesSink: Sink[String, Future[Long]] = Sink.fold(0L)((count, _) => count + 1)

    val rawValidated = Flow[ByteString]
      .via(Framing.delimiter(ByteString("\n"), 65536, allowTruncation = true))
      .map(_.utf8String.trim)
      .filter(_.nonEmpty)
      .alsoToMat(entriesSink)(Keep.right)
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

  private def aggregateErrors(errors: Seq[ValidationError]): ValidationError = {
    val firstLevelErrors = errors.collect { case FirstLevelValidationFailure(err) => err }
    val secondLevelErrors = errors
      .collect { case SecondLevelValidationFailure(err) => err }
      .flatten
      .take(appConfig.monthlyReturnMaxErrors)

    if (firstLevelErrors.nonEmpty) FirstLevelValidationFailure(firstLevelErrors.head)
    else SecondLevelValidationFailure(secondLevelErrors.toList)
  }

  private def errorVolumes(errors: Seq[ValidationError]): Seq[ErrorVolume] = {
    val errorTypes = errors.flatMap {
      case FirstLevelValidationFailure(err)   => Seq(err.code)
      case SecondLevelValidationFailure(errs) => errs.map(_.code)
    }
    val volumes = errorTypes.groupMapReduce(identity)(_ => 1)(_ + _)
    errorTypes.distinct.map(errorType => ErrorVolume(errorType, volumes(errorType)))
  }

  def processToTempFile(source: Source[ByteString, _]): Future[Either[InvalidSubmission, ValidatedSubmission]] = {
    val validationStarted = clock.instant()
    val tempFile          = temporaryFileCreator.create("disa-returns-", ".ndjson")

    val requestSizeSink: Sink[ByteString, Future[Long]] = Sink.fold(0L)((size, bytes) => size + bytes.length)

    val errorSink: Sink[Either[ValidationError, String], Future[Seq[ValidationError]]] =
      Flow[Either[ValidationError, String]]
        .collect { case Left(err) => err }
        .toMat(Sink.seq)(Keep.right)

    val (((requestSizeFuture, entriesFuture), errorsFuture), ioResultFuture) =
      source
        .alsoToMat(requestSizeSink)(Keep.right)
        .viaMat(validatedLinesFlow)(Keep.both)
        .alsoToMat(errorSink)(Keep.both)
        .collect { case Right(line) => ByteString(line + "\n") }
        .toMat(FileIO.toPath(tempFile.path))(Keep.both)
        .run()

    (for {
      requestSize     <- requestSizeFuture
      numberOfEntries <- entriesFuture
      errors          <- errorsFuture
      _               <- ioResultFuture
    } yield {
      val metrics = ValidationMetrics(numberOfEntries, requestSize, Duration.between(validationStarted, clock.instant()))
      if (errors.nonEmpty) {
        tempFile.delete()
        Left(InvalidSubmission(aggregateErrors(errors), errorVolumes(errors), metrics))
      } else {
        Right(ValidatedSubmission(tempFile, metrics))
      }
    }).recover { case ex =>
      tempFile.delete()
      throw ex
    }
  }
}
