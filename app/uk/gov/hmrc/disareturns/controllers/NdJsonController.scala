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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Flow, Framing, Sink, Source}
import org.apache.pekko.util.ByteString
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc._
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaAccount
import uk.gov.hmrc.disareturns.mongoRepositories.{InvalidIsaAccountRepository, ReportingRepository}

import javax.inject._
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NdJsonController @Inject() (
  val controllerComponents:    ControllerComponents,
  implicit val mat:            Materializer,
  ndJsonRepository:            ReportingRepository,
  invalidIsaAccountRepository: InvalidIsaAccountRepository
)(implicit ec:                 ExecutionContext)
    extends BaseController {

  // Define custom play BodyParser that returns a stream of raw bytes from the incoming HTTP request body
  // Is there a play in built ndJson parser??
  //parse.tolerantStream (if available) gives you a BodyParser[Source[ByteString, _]]?
  def streamingParser: BodyParser[Source[ByteString, _]] = BodyParser("Streaming NDJSON") { request =>
    Accumulator.source[ByteString].map(Right.apply)
  }

  def uploadNdjsonStream(): Action[Source[ByteString, _]] = Action.async(streamingParser) { request =>
    val source: Source[ByteString, _] = request.body

    val lines = source
      .via(Framing.delimiter(delimiter = ByteString("\n"), maximumFrameLength = 65536, allowTruncation = true))
      .map(_.utf8String)
      .filter(_.nonEmpty)

    val processing = lines.runForeach { line =>
      println(s"NDJSON line: $line")
    }

    // Return a Future[Result]
    processing
      .map(_ => Ok("NDJSON streamed successfully"))
      .recover { case ex =>
        InternalServerError(s"Streaming failed: ${ex.getMessage}")
      }
  }

  def uploadNdjsonStreamWithMongo(isaManagerId: String, returnId: String): Action[Source[ByteString, _]] = Action.async(streamingParser) { request =>
    val source: Source[ByteString, _] = request.body

    // why is maximumFrameLength needed? any side effects of exceeding or not enforcing this?
    val lines: Source[String, _] = source
      .via(Framing.delimiter(delimiter = ByteString("\n"), maximumFrameLength = 65536, allowTruncation = false))
      .map(_.utf8String)
      .filter(_.nonEmpty)

    val parsedReports: Source[IsaAccount, _] = lines.map { line =>
      Json.parse(line).as[IsaAccount]
    }

    val collectedReports: Future[Seq[IsaAccount]] = parsedReports.runFold(Seq.empty[IsaAccount])(_ :+ _)

    collectedReports
      .flatMap { reports =>
        ndJsonRepository.insertBatch(isaManagerId, returnId, reports).map { _ =>
          Ok(s"Inserted ${reports.size} reports into MongoDB")
        }
      }
      .recover { case ex =>
        BadRequest(s"Error processing NDJSON: ${ex.getMessage}")
      }
  }

  def uploadNdjsonWithStreamIntoMongo(isaManagerId: String, returnId: String): Action[Source[ByteString, _]] = Action.async(streamingParser) {
    request =>
      // Extract the streamed source of bytes from the request body
      val source: Source[ByteString, _] = request.body

      val lines: Source[String, _] = source
        .via(
          Framing.delimiter(
            delimiter = ByteString("\n"),
            maximumFrameLength = 65536, // 64 KB per line max
            allowTruncation = false // truncates lines that are too long instead of failing
          )
        )
        .map(_.utf8String)
        .filter(_.nonEmpty)

      val parsedReports: Source[IsaAccount, _] = lines.map { line =>
        Json.parse(line).as[IsaAccount]
      }

      val insertFlow = Flow[IsaAccount]
        .grouped(1000)
        .mapAsync(1) { batch =>
          ndJsonRepository.insertOrUpdate(isaManagerId, returnId, batch)
        }

      // Connect the stream of parsed IsaAccounts to the insert flow and run it
      parsedReports
        .via(insertFlow) // stream → batch → insert
        .runWith(Sink.ignore) // we don't care about the result of each insert, just run the stream
        .map(_ => Ok("NDJSON upload complete")) // send OK response when complete
        .recover {
          // If any part of the stream fails, return a BadRequest with the error message
          case ex => BadRequest(s"Error processing NDJSON: ${ex.getMessage}")
        }
  }

  def uploadNdjsonWithStreamValidation(isaManagerId: String, returnId: String): Action[Source[ByteString, _]] =
    Action.async(streamingParser) { request =>
      val source: Source[ByteString, _] = request.body

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
            _ <- ndJsonRepository.insertOrUpdate(isaManagerId, returnId, validBatch)
            _ <- invalidIsaAccountRepository.insertOrUpdate(isaManagerId, returnId, invalidBatch)
          } yield ()
        }
        .runWith(Sink.ignore) // Run stream

      handleStreamedAccounts.map(_ => Ok("NDJSON upload complete"))
  }
}
