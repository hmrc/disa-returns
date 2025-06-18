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
import uk.gov.hmrc.disareturns.mongoRepositories.ReportingRepository

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NdJsonController @Inject() (
                                   val controllerComponents: ControllerComponents,
                                   implicit val mat: Materializer,
                                   ndJsonRepository: ReportingRepository
                                 )(implicit ec: ExecutionContext) extends BaseController {

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
    processing.map(_ => Ok("NDJSON streamed successfully"))
      .recover {
        case ex => InternalServerError(s"Streaming failed: ${ex.getMessage}")
      }
  }


  def uploadNdjsonStreamWithMongo(isaMangagerId: String, returnId: String): Action[Source[ByteString, _]] = Action.async(streamingParser) { request =>
    val source: Source[ByteString, _] = request.body

    // why is maximumFrameLength needed? any side effects of exceeding or not enforcing this?
    val lines: Source[String, _] = source
      .via(Framing.delimiter(delimiter = ByteString("\n"), maximumFrameLength = 65536, allowTruncation = true))
      .map(_.utf8String)
      .filter(_.nonEmpty)

    val parsedReports: Source[IsaAccount, _] = lines.map { line =>
      Json.parse(line).as[IsaAccount]
    }

    val collectedReports: Future[Seq[IsaAccount]] = parsedReports.runFold(Seq.empty[IsaAccount])(_ :+ _)

    collectedReports.flatMap { reports =>
      ndJsonRepository.insertBatch(isaMangagerId, returnId, reports).map { _ =>
        Ok(s"Inserted ${reports.size} reports into MongoDB")
      }
    }.recover {
      case ex =>
        BadRequest(s"Error processing NDJSON: ${ex.getMessage}")
    }
  }


  def uploadNdjsonStreamWithStreamIntoMongo(isaMangagerId: String, returnId: String): Action[Source[ByteString, _]] = Action.async(streamingParser) { request =>
    // Extract the streamed source of bytes from the request body
    val source: Source[ByteString, _] = request.body

    // Split the stream of bytes into lines based on newline delimiter
    // maximumFrameLength limits the maximum size of a line/frame
    //   - Prevents excessively large lines from consuming too much memory
    //   - If allowTruncation = true, a line exceeding the limit will be truncated (might cause JSON parse errors)
    //   - If allowTruncation = false, it will fail with a FramingException
    val lines: Source[String, _] = source
      .via(Framing.delimiter(
        delimiter = ByteString("\n"),
        maximumFrameLength = 65536, // 64 KB per line max
        allowTruncation = true      // truncates lines that are too long instead of failing
      ))
      .map(_.utf8String)           // convert each ByteString line to a UTF-8 string
      .filter(_.nonEmpty)          // ignore empty lines (e.g., blank lines in NDJSON)

    // Parse each line of JSON into a IsaAccount case class
    val parsedReports: Source[IsaAccount, _] = lines.map { line =>
      Json.parse(line).as[IsaAccount]
      // This will throw if parsing fails — consider using .validate or .asOpt for safer handling
    }

    // Flow that groups IsaAccounts into batches of 1000 and inserts them into MongoDB asynchronously
    val insertFlow = Flow[IsaAccount]
      .grouped(1000) // collect up to 1000 records before inserting
      .mapAsync(1) { batch =>
        ndJsonRepository.insertBatch(isaMangagerId, returnId, batch)
        // Assuming insertBatch returns Future[Unit] or Future[Result]
      }

    // Connect the stream of parsed IsaAccounts to the insert flow and run it
    parsedReports
      .via(insertFlow)      // stream → batch → insert
      .runWith(Sink.ignore) // we don't care about the result of each insert, just run the stream
      .map(_ => Ok("NDJSON upload complete")) // send OK response when complete
      .recover {
        // If any part of the stream fails, return a BadRequest with the error message
        case ex => BadRequest(s"Error processing NDJSON: ${ex.getMessage}")
      }
  }
}