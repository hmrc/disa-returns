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
import org.apache.pekko.stream.scaladsl.{Framing, Sink, Source}
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaAccount
import uk.gov.hmrc.disareturns.utils.JsonErrorMapper.jsErrorToDomainError
import uk.gov.hmrc.disareturns.utils.JsonValidation
import uk.gov.hmrc.disareturns.utils.JsonValidation.findDuplicateFields

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class StreamingParserService @Inject() (implicit val mat: Materializer) extends Logging {

  private def processStream(source: Source[ByteString, _]): Source[Either[ValidationError, IsaAccount], _] = {
    val validated = validatedStream(source)
    validated.prefixAndTail(1).flatMapConcat {
      case (Seq(), _) =>
        Source.single(Left(FirstLevelValidationFailure(EmptyPayload)))
      case (Seq(first), tail) =>
        tail.prepend(Source.single(first))
    }
  }

  private def validateSecondLevel(line: String, jsValue: JsValue, nino: String, accountNumber: String): Either[ValidationError, IsaAccount] = {
    val combinedValidation: JsResult[IsaAccount] = findDuplicateFields(line).flatMap { _ =>
      jsValue.validate[IsaAccount]
    }
    combinedValidation match {
      case JsSuccess(account, _) =>
        Right(account)
      case JsError(errors) =>
        val domainErrors = jsErrorToDomainError(errors, nino, accountNumber).headOption.toSeq
        Left(SecondLevelValidationFailure(domainErrors))
    }
  }

  private def validatedStream(source: Source[ByteString, _]): Source[Either[ValidationError, IsaAccount], _] =
    source
      .via(Framing.delimiter(ByteString("\n"), 65536, allowTruncation = false))
      .map(_.utf8String.trim)
      .filter(_.nonEmpty)
      .map { line =>
        Try(Json.parse(line)) match {
          case Success(jsValue) =>
            JsonValidation.firstLevelValidatorExtractNinoAndAccount(jsValue) match {
              case Right((nino, accountNumber)) =>
                validateSecondLevel(line, jsValue, nino, accountNumber)
              case Left(firstLevelErr) =>
                Left(FirstLevelValidationFailure(firstLevelErr))
            }
          case Failure(ex) =>
            Left(FirstLevelValidationFailure(MalformedJsonFailureErr: ErrorResponse))
        }
      }

  def processSource(
    source: Source[ByteString, _]
  ): Future[Either[ValidationError, Seq[IsaAccount]]] =
    //TODO: .grouped(27000) is not safe for a few reason, 1. loads everything into memory, 2. payload could have more than 27k records in a single request.
    processStream(source)
      .grouped(27000)
      .map { batch =>
        val (errors, validAccounts) = batch.partitionMap(identity)
        logger.info(s"Processing the ISA accounts payload validation results")
        if (errors.nonEmpty) {
          val firstLevelErrors  = errors.collect { case FirstLevelValidationFailure(err) => err }
          val secondLevelErrors = errors.collect { case SecondLevelValidationFailure(err) => err }.flatten

          val failureException = if (firstLevelErrors.nonEmpty) {
            FirstLevelValidationFailure(firstLevelErrors.head)
          } else {
            SecondLevelValidationFailure(secondLevelErrors.toList)
          }
          Left(failureException)
        } else {
          Right(validAccounts)
        }
      }
      .runWith(Sink.head[Either[ValidationError, Seq[IsaAccount]]])
}
