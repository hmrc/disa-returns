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
import play.api.libs.json.{JsError, JsSuccess, Json}
import uk.gov.hmrc.disareturns.models.common.{FirstLevelValidationException, NinoOrAccountNumMissingErr, SecondLevelValidationError, SecondLevelValidationException, SecondLevelValidationResponse}
import uk.gov.hmrc.disareturns.models.submission.DataValidator
import uk.gov.hmrc.disareturns.models.submission.DataValidator.jsErrorToDomainError
import uk.gov.hmrc.disareturns.models.submission.isaAccounts.IsaAccount
import uk.gov.hmrc.disareturns.repositories.ReportingRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StreamingParserService @Inject()(reportingRepository: ReportingRepository,
                                       implicit val mat: Materializer)(implicit ec: ExecutionContext) {

  def validatedStream(source: Source[ByteString, _]): Source[Either[SecondLevelValidationError, IsaAccount], _] =
    source
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 65536, allowTruncation = false))
      .map(_.utf8String.trim)
      .filter(_.nonEmpty)
      .map { line =>
        val jsValue = Json.parse(line)
        DataValidator.FirstLevelValidatorExtractNinoAndAccount(jsValue) match {
          case Right((nino, accountNumber)) =>
            jsValue.validate[IsaAccount] match {
              case JsSuccess(account, _) =>
                DataValidator.validateAccount(account) match {
                  case Right(_)                            => Right(account)
                  case Left(err: SecondLevelValidationError) => Left(err)
                }

              case JsError(errors) =>
                val domainErrors = jsErrorToDomainError(errors, nino, accountNumber)
                // first failure, since stream emits Either[SecondLevelValidationError, IsaAccount]
                Left(domainErrors.headOption.getOrElse(
                  SecondLevelValidationError(nino, accountNumber, "UNKNOWN_VALIDATION", "Unknown validation error")
                ))

            }
          case Left(_) =>
            throw FirstLevelValidationException(NinoOrAccountNumMissingErr)
        }
      }


  def processValidatedStream(
                              isaManagerReferenceNumber: String,
                              returnId: String,
                              validatedStream: Source[Either[SecondLevelValidationError, IsaAccount], _]
                            ): Future[Done] = {

    validatedStream
      .grouped(25000)
      .mapAsync(1) { batch =>
        val (errors, validAccounts) = batch.partitionMap(identity)
        if (errors.nonEmpty)
          Future.failed(SecondLevelValidationException(
            SecondLevelValidationResponse(errors = errors.toList)
          ))
        else
          reportingRepository.insertBatch(isaManagerReferenceNumber, returnId, validAccounts)
      }
      .runWith(Sink.ignore)
  }

}
