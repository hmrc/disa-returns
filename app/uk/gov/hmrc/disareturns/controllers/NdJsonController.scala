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
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.libs.streams.Accumulator
import play.api.mvc._
import uk.gov.hmrc.disareturns.service.NdjsonUploadService

import javax.inject._
import scala.concurrent.ExecutionContext

@Singleton
class NdJsonController @Inject() (
  val controllerComponents: ControllerComponents,
  implicit val mat:         Materializer,
  uploadService:            NdjsonUploadService
)(implicit ec:              ExecutionContext)
    extends BaseController {

  def streamingParser: BodyParser[Source[ByteString, _]] = BodyParser("Streaming NDJSON") { _ =>
    Accumulator.source[ByteString].map(Right.apply)
  }

  def uploadNdjsonWithStreamValidation(isaManagerId: String, returnId: String): Action[Source[ByteString, _]] =
    Action.async(streamingParser) { request =>
      val source = request.body
      uploadService
        .processNdjsonUpload(isaManagerId, returnId, source)
        .map {
          case true  => Ok("NDJSON upload complete")
          case false => BadRequest("Upload complete, but some entries were invalid")
        }

}}
