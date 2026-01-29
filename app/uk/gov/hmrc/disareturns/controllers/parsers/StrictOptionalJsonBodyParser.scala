/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.disareturns.controllers.parsers

import com.fasterxml.jackson.core.{JsonFactory, JsonParseException, JsonParser}
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Inject
import jakarta.inject.Singleton
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.libs.streams.Accumulator
import play.api.mvc.Results.BadRequest
import play.api.mvc._
import uk.gov.hmrc.disareturns.models.common.DuplicateNilReturnField

import scala.concurrent.ExecutionContext

@Singleton
class StrictOptionalJsonBodyParser @Inject() ()(implicit ec: ExecutionContext) extends BodyParser[Option[JsValue]] with Logging {

  private val mapper: ObjectMapper =
    new ObjectMapper(new JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION))

  override def apply(request: RequestHeader): Accumulator[ByteString, Either[Result, Option[JsValue]]] =
    Accumulator(Sink.fold[ByteString, ByteString](ByteString.empty)(_ ++ _)).map { bytes =>
      if (bytes.isEmpty)
        Right(None)
      else {
        val raw = bytes.toArray
        try {
          mapper.readTree(raw)
          Right(Some(Json.parse(raw)))
        } catch {
          case _: JsonParseException =>
            logger.warn("Duplicate NilReturn Field Detected")
            Left(BadRequest(Json.toJson(DuplicateNilReturnField)))
        }
      }
    }
}
