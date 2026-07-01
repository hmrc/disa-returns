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
import org.apache.pekko.stream.scaladsl.{Framing, Source}
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaAccount
import uk.gov.hmrc.disareturns.utils.JsonErrorMapper.jsErrorToDomainError
import uk.gov.hmrc.disareturns.utils.JsonValidation
import uk.gov.hmrc.disareturns.utils.JsonValidation.findDuplicateFields

import java.io.{BufferedWriter, FileWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class StreamingParserService @Inject() (implicit val mat: Materializer, ec: ExecutionContext) extends Logging {

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

  private def aggregateErrors(errors: List[ValidationError]): Left[ValidationError, Path] = {
    val firstLevelErrors  = errors.collect { case FirstLevelValidationFailure(err) => err }
    val secondLevelErrors = errors.collect { case SecondLevelValidationFailure(err) => err }.flatten

    if (firstLevelErrors.nonEmpty) Left(FirstLevelValidationFailure(firstLevelErrors.head))
    else Left(SecondLevelValidationFailure(secondLevelErrors.toList))
  }

  def processToTempFile(source: Source[ByteString, _]): Future[Either[ValidationError, Path]] = {
    val tempFile = Files.createTempFile("disa-returns-", ".ndjson")
    val writer   = new BufferedWriter(new FileWriter(tempFile.toFile, StandardCharsets.UTF_8))

    validatedLinesStream(source)
      .runFold(List.empty[ValidationError]) { (errors, result) =>
        result match {
          case Left(err) => errors :+ err
          case Right(line) =>
            if (errors.isEmpty) {
              writer.write(line)
              writer.newLine()
            }
            errors
        }
      }
      .map { errors =>
        writer.close()
        if (errors.nonEmpty) {
          Files.deleteIfExists(tempFile)
          aggregateErrors(errors)
        } else {
          Right(tempFile)
        }
      }
      .recover { case ex =>
        Try(writer.close())
        Files.deleteIfExists(tempFile)
        throw ex
      }
  }
}
