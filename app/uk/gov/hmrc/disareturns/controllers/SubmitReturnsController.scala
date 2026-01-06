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
import play.api.mvc.{Action, BodyParser, ControllerComponents}
import uk.gov.hmrc.disareturns.controllers.actionBuilders._
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.helpers.ValidationHelper
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaAccount
import uk.gov.hmrc.disareturns.services.{ETMPService, NPSService, StreamingParserService}
import uk.gov.hmrc.disareturns.utils.HttpHelper
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmitReturnsController @Inject() (
  cc:                       ControllerComponents,
  streamingParserService:   StreamingParserService,
  npsService:               NPSService,
  authAction:               AuthAction,
  implicit val etmpService: ETMPService
)(implicit ec:              ExecutionContext, implicit val mat: Materializer)
    extends BackendController(cc)
    with Logging {

  private def streamingParser: BodyParser[Source[ByteString, _]] = BodyParser("Streaming NDJSON") { _ =>
    Accumulator.source[ByteString].map(Right.apply)
  }

  private def ignoreBodyParser: BodyParser[Source[ByteString, _]] = BodyParser(_ => Accumulator.done(Right(Source.empty[ByteString])))

  def submit(zReference: String, taxYear: String, month: String): Action[Source[ByteString, _]] =
    ValidationHelper.validateParams(zReference, taxYear, month) match {
      case Left(errors) => Action.async(ignoreBodyParser)(_ => Future.successful(BadRequest(Json.toJson(errors))))
      case Right((zReference, _, _, _)) =>
        (Action andThen authAction(zReference)).async(streamingParser) { implicit request =>
          etmpService
            .validateEtmpSubmissionEligibility(zReference)
            .flatMap {
              case Right(_) =>
                streamingParserService.processSource(request.body).flatMap {
                  case Left(error: ValidationError) =>
                    error match {
                      case FirstLevelValidationFailure(err) =>
                        logger.warn(
                          s"Submission had first level validation error for IM ref: [$zReference] for [$month][$taxYear] with error: [$error]"
                        )
                        Future.successful(BadRequest(Json.toJson(err)))
                      case SecondLevelValidationFailure(errors) =>
                        logger.warn(
                          s"Submission had second level validation errors for IM ref: [$zReference] for [$month][$taxYear] with error: [$error]"
                        )
                        Future.successful(BadRequest(Json.toJson(SecondLevelValidationResponse(errors = errors))))
                      case err =>
                        logger.error(s"streamingParserService.processSource has failed with the error: $err")
                        Future.successful(InternalServerError(Json.toJson(InternalServerErr())))
                    }
                  case Right(subscriptions: Seq[IsaAccount]) =>
                    npsService.submitIsaAccounts(zReference, subscriptions) map {
                      case Left(error) =>
                        logger.error(
                          s"Submission of data to NPS for IM ref: [$zReference] for [$month][$taxYear] has failed with the error: [$error]"
                        )
                        HttpHelper.toHttpError(error)
                      case Right(_) =>
                        logger.info(s"Data submitted successfully for IM ref: [$zReference] for: [$month][$taxYear]")
                        NoContent
                    }
                }
              case Left(error: ErrorResponse) =>
                error match {
                  case _: InternalServerErr =>
                    logger.error(
                      s"Submission eligibility failed for IM ref: [$zReference] for [$month][$taxYear] has failed with the error: [$error]"
                    )
                  case _ =>
                    logger.warn(
                      s"Submission eligibility failed for IM ref: [$zReference] for [$month][$taxYear] has failed with the error: [$error]"
                    )
                }

                Future.successful(HttpHelper.toHttpError(error))
            }
        }: Action[Source[ByteString, _]]
    }
}
