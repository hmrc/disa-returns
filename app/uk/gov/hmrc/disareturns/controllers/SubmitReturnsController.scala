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
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.{Action, BodyParser, ControllerComponents, Result}
import uk.gov.hmrc.disareturns.controllers.actionBuilders._
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.services.{ETMPService, ReturnMetadataService, StreamingParserService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmitReturnsController @Inject() (
  cc:                       ControllerComponents,
  streamingParserService:   StreamingParserService,
  clientIdAction:           ClientIdAction,
  authAction:               AuthAction,
  returnMetadataService:    ReturnMetadataService,
  implicit val etmpService: ETMPService
)(implicit ec:              ExecutionContext, implicit val mat: Materializer)
    extends BackendController(cc)
    with Logging {

  def streamingParser: BodyParser[Source[ByteString, _]] = BodyParser("Streaming NDJSON") { request =>
    Accumulator.source[ByteString].map(Right.apply)
  }

  def submit(isaManagerReferenceNumber: String, returnId: String): Action[Source[ByteString, _]] =
    (Action andThen authAction andThen clientIdAction).async(streamingParser) { implicit request =>
      returnMetadataService.existsByIsaManagerReferenceAndReturnId(isaManagerReferenceNumber, returnId).flatMap {
        case true =>
          validateEtmpAndThen(isaManagerReferenceNumber) {
            val source: Source[ByteString, _] = request.body
            val validatedStream = streamingParserService.validatedStream(source)
            streamingParserService
              .processValidatedStream(isaManagerReferenceNumber, returnId, validatedStream)
              .map(_ => NoContent)
              .recover {
                case FirstLevelValidationException(err) =>
                  BadRequest(Json.toJson(err))
                case SecondLevelValidationException(errResponse) =>
                  BadRequest(Json.toJson(errResponse))
                case ex =>
                  logger.error(s"streamingParserService.processValidatedStream has failed with the exception: $ex")
                  InternalServerError(Json.toJson(InternalServerErr: ErrorResponse))
              }
          }
        case false =>
          Future.successful(NotFound(Json.toJson(ReturnIdNotMatchedErr: ErrorResponse)))
      }
    }

  private def validateEtmpAndThen(isaManagerReference: String)(block: => Future[Result])(implicit hc: HeaderCarrier): Future[Result] =
    etmpService
      .validateEtmpSubmissionEligibility(isaManagerReference) // Now returns Future[Either[ErrorResponse, (Unit, Unit)]]
      .flatMap {
        case Right(_) =>
          block
        case Left(error: ErrorResponse) =>
          error match {
            case ReportingWindowClosed | ObligationClosed =>
              Future.successful(Forbidden(Json.toJson(error)))
            case MultipleErrorResponse(_, _, errors) if errors.exists(e => e == ReportingWindowClosed || e == ObligationClosed) =>
              Future.successful(Forbidden(Json.toJson(error)))
            case _ =>
              Future.successful(InternalServerError(Json.toJson(error)))
          }
      }

}
