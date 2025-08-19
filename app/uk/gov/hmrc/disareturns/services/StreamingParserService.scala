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

import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Framing, Sink, Source}
import org.apache.pekko.util.ByteString
import play.api.libs.json.{JsError, JsPath, JsResult, JsSuccess, JsValue, Json, JsonValidationError}
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.submission.isaAccounts.IsaAccount
import uk.gov.hmrc.disareturns.repositories.MonthlyReportDocumentRepository
import uk.gov.hmrc.disareturns.services.validation.DataValidator
import uk.gov.hmrc.disareturns.services.validation.DataValidator.jsErrorToDomainError

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class StreamingParserService @Inject() (reportingRepository: MonthlyReportDocumentRepository, implicit val mat: Materializer) {

  def process(source: Source[ByteString, _]): Source[Either[ValidationError, IsaAccount], _] = {
    val validated = validatedStream(source)
    validated.prefixAndTail(1).flatMapConcat {
      case (Seq(), _) =>
        Source.single(Left(FirstLevelValidationFailure(BadRequestErr("NDJSON payload is empty."): ErrorResponse)))
      case (Seq(first), tail) =>
        tail.prepend(Source.single(first))
    }
  }

  def findDuplicateFields(line: String): JsResult[Unit] = {
    val keys = "\"([^\"]+)\"\\s*:".r.findAllMatchIn(line).map(_.group(1)).toList
    keys.diff(keys.distinct).headOption match {
      case Some(dupKey) =>
        JsError(JsPath \ dupKey, JsonValidationError("error.duplicateField", s"Duplicate field detected: $dupKey"))
      case None =>
        JsSuccess(())
    }
  }

  def validateSecondLevel(line: String, jsValue: JsValue, nino: String, accountNumber: String): Either[ValidationError, IsaAccount] = {
    val combinedValidation: JsResult[IsaAccount] = findDuplicateFields(line).flatMap { _ =>
      jsValue.validate[IsaAccount]
    }
    combinedValidation match {
      case JsSuccess(account, _) =>
        DataValidator.validate(account) match {
          case None      => Right(account)
          case Some(err) => Left(SecondLevelValidationFailure(err))
        }

      case JsError(errors) =>
        jsErrorToDomainError(errors, nino, accountNumber).headOption match {
          case Some(error) => Left(SecondLevelValidationFailure(error))
          case None =>
            Left(
              SecondLevelValidationFailure(
                SecondLevelValidationError(
                  nino,
                  accountNumber,
                  "UNKNOWN_VALIDATION",
                  "Unknown validation error"
                )
              )
            )
        }
    }
  }

  def validatedStream(source: Source[ByteString, _]): Source[Either[ValidationError, IsaAccount], _] =
    source
      .via(Framing.delimiter(ByteString("\n"), 65536, allowTruncation = false))
      .map(_.utf8String.trim)
      .filter(_.nonEmpty)
      .map { line =>
        Try(Json.parse(line)) match {
          case Success(jsValue) =>
            DataValidator.firstLevelValidatorExtractNinoAndAccount(jsValue) match {
              case Right((nino, accountNumber)) =>
                validateSecondLevel(line, jsValue, nino, accountNumber)
              case Left(firstLevelErr) =>
                Left(FirstLevelValidationFailure(firstLevelErr))
            }
          case Failure(ex) =>
            Left(FirstLevelValidationFailure(MalformedJsonFailureErr: ErrorResponse))
        }
      }

  def processValidatedStream(
    isaManagerReferenceNumber: String,
    returnId:                  String,
    validatedStream:           Source[Either[ValidationError, IsaAccount], _]
  ): Future[Done] =
    validatedStream
      .grouped(27000)
      .mapAsync(1) { batch =>
        val (errors, validAccounts) = batch.partitionMap(identity)

        if (errors.nonEmpty) {
          val firstLevelErrors  = errors.collect { case FirstLevelValidationFailure(err) => err }
          val secondLevelErrors = errors.collect { case SecondLevelValidationFailure(err) => err }

          val failureException = if (firstLevelErrors.nonEmpty) {
            FirstLevelValidationException(firstLevelErrors.head)
          } else {
            SecondLevelValidationException(SecondLevelValidationResponse(errors = secondLevelErrors.toList))
          }
          Future.failed(failureException)
        } else {
          reportingRepository.insertBatch(isaManagerReferenceNumber, returnId, validAccounts)
        }
      }
      .runWith(Sink.ignore)
}
