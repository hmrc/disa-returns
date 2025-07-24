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

package uk.gov.hmrc.disareturns.controllers

import com.google.inject.Inject
import jakarta.inject.Singleton
import org.apache.pekko.stream.scaladsl.{Framing, Sink, Source}
import org.apache.pekko.util.ByteString
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.{Action, BodyParser, ControllerComponents}
import uk.gov.hmrc.disareturns.controllers.actionBuilders._
import uk.gov.hmrc.disareturns.models.submission.isaAccounts.IsaAccount
import uk.gov.hmrc.disareturns.repositories.ReportingRepository
import uk.gov.hmrc.disareturns.services.{ETMPService, PPNSService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import org.apache.pekko.stream.Materializer

import scala.concurrent.ExecutionContext

@Singleton
class ReturnsSubmissionController @Inject()(
  cc:                    ControllerComponents,
  implicit val mat:      Materializer,
  etmpService:           ETMPService,
  ppnsService:           PPNSService,
  reportingRepository:   ReportingRepository,
  clientIdAction:        ClientIdAction,
  authAction:            AuthAction)(implicit ec: ExecutionContext)
  extends BackendController(cc) with WithJsonBodyWithBadRequest {


  // Define custom play BodyParser that returns a stream of raw bytes from the incoming HTTP request body
  // Is there a play in built ndJson parser??
  //parse.tolerantStream (if available) gives you a BodyParser[Source[ByteString, _]]?
  def streamingParser: BodyParser[Source[ByteString, _]] = BodyParser("Streaming NDJSON") { request =>
    Accumulator.source[ByteString].map(Right.apply)
  }

  def submit(isaManagerReferenceNumber: String, returnId: String): Action[Source[ByteString, _]] =
    (Action andThen authAction andThen clientIdAction).async(streamingParser) { implicit request =>
      val source: Source[ByteString, _] = request.body

      //TODO:Move this out of controller.
      val ninoRegex = """^((?!(BG|GB|KN|NK|NT|TN|ZZ)|(D|F|I|Q|U|V)[A-Z]|[A-Z](D|F|I|O|Q|U|V))[A-Z]{2})[0-9]{6}[A-D]?$""".r

      val lines = source
        .via(Framing.delimiter(ByteString("\n"), 65536, allowTruncation = false))
        .map(_.utf8String.trim)
        .filter(_.nonEmpty)
        .map { line =>
          // Parse each line to IsaAccount and validate NINO
          Json.parse(line).validate[IsaAccount].asOpt match {
            case Some(account) =>
              if (ninoRegex.matches(account.nino)) Right(account)
              else Left(account)
            case None =>
              throw new IllegalArgumentException(s"Invalid JSON line: $line")
          }
        }

      val handleStreamedAccounts = lines
        .grouped(3000)
        .mapAsync(1) { batch =>
          // Separate valid and invalid records
          val (invalid, valid) = batch.partition(_.isLeft)
          val validBatch: Seq[IsaAccount] = valid.collect { case Right(acc) => acc }
          val invalidBatch: Seq[IsaAccount] = invalid.collect { case Left(acc) => acc }

          // Store valid and invalid records
          for {
            _ <- reportingRepository.insertOrUpdate(isaManagerReferenceNumber, returnId, validBatch)
//            _ <- invalidIsaAccountRepository.insertOrUpdate(isaManagerId, returnId, invalidBatch)
          } yield ()
        }
        .runWith(Sink.ignore) // Run stream

      handleStreamedAccounts.map(_ => Ok("NDJSON upload complete"))
    }
}
