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

package uk.gov.hmrc.disareturns.service

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Framing, Source}
import org.apache.pekko.util.ByteString
import play.api.libs.json.Json
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaAccount
import uk.gov.hmrc.disareturns.mongoRepositories.{InvalidIsaAccountRepository, ReportingRepository}
import uk.gov.hmrc.disareturns.utils.NinoValidator

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NdjsonUploadService @Inject() (
  ndJsonRepository:            ReportingRepository,
  invalidIsaAccountRepository: InvalidIsaAccountRepository
)(implicit mat:                Materializer, ec: ExecutionContext) {



  def processNdjsonUpload(
                           isaManagerId: String,
                           returnId: String,
                           source: Source[ByteString, _]
                         )(implicit ec: ExecutionContext): Future[Boolean] = {

    source // this is a stream of NDJSON input
      .via(Framing.delimiter(ByteString("\n"), 65536, allowTruncation = false)) //split each bystream into line. Each line will be a single JSON object.
      .map(_.utf8String.trim) //convert to string
      .filter(_.nonEmpty) //remove an extra white spaces and filter out empty lines
      .map { line =>
        val parsed = try {
          //Each line is parsed into JSON and validated against IsaAccount
          Json.parse(line).validate[IsaAccount].asOpt match {
            //if its valid and nino passed the regex return Right(Account)
            case Some(account) if NinoValidator.isValid(account.nino) => Right(account)
            //if it doesnt pass validation return Left(Account)
            case Some(account) => Left(account)
            //fails to parse (plain bad data)
            case None => Left(line)
          }
        } catch {
          case _: Throwable => Left(line)
        }
        parsed
      }
      .grouped(400) // batches the stream into group of 400
      //proccess each batch sequentially
      .mapAsync(1) { batch =>
        val (valid, invalid) = batch.partition(_.isRight) //split the entries
        val validBatch = valid.collect { case Right(acc) => acc } //collect all the valid entries
        val invalidBatch = invalid.collect { //collect the invalid entries
          case Left(acc: IsaAccount) => acc
        }

        for {
          _ <- if (validBatch.nonEmpty)
            ndJsonRepository.insertOrUpdate(isaManagerId, returnId, validBatch) //insert into mongo
          else Future.unit

          _ <- if (invalidBatch.nonEmpty)
            invalidIsaAccountRepository.insertOrUpdate(isaManagerId, returnId, invalidBatch) //inert into mongo
          else Future.unit
        } yield invalidBatch.nonEmpty
      }
      .runFold(false)(_ || _) // folds over all batch results, returning true if any batch had invalids
      .map(hasInvalid => !hasInvalid) // final result: true = all valid, false = some invalid
  }


}
