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

import cats.data.EitherT
import cats.implicits._
import com.google.inject.Inject
import jakarta.inject.Singleton
import play.api.Logging
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc.{Action, ControllerComponents, Request}
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.controllers.actionBuilders._
import uk.gov.hmrc.disareturns.controllers.parsers.StrictJsonBodyParser
import uk.gov.hmrc.disareturns.models.common.{DeclarationRequest, MalformedJsonFailureErr}
import uk.gov.hmrc.disareturns.models.declaration.{DeclarationSuccessfulResponse, ReportingNilReturn}
import uk.gov.hmrc.disareturns.models.helpers.ValidationHelper
import uk.gov.hmrc.disareturns.services.{ETMPService, NotificationContextService, PPNSService, SubmissionService}
import uk.gov.hmrc.disareturns.utils.HttpHelper
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeclarationController @Inject() (
  cc:                         ControllerComponents,
  etmpService:                ETMPService,
  ppnsService:                PPNSService,
  submissionService:          SubmissionService,
  notificationContextService: NotificationContextService,
  authAction:                 AuthAction,
  clientIdAction:             ClientIdAction,
  config:                     AppConfig,
  strictJsonBodyParser:       StrictJsonBodyParser
)(implicit ec:                ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def declare(zReference: String, taxYear: String, month: String): Action[JsValue] =
    ValidationHelper.validateParams(zReference, taxYear, month) match {
      case Left(errors) =>
        Action(strictJsonBodyParser) { _: Request[JsValue] =>
          BadRequest(Json.toJson(errors))
        }
      case Right((zReference, validTaxYear, validMonth, _)) =>
        (
          Action(strictJsonBodyParser)
            andThen authAction(zReference)
            andThen clientIdAction
        ).async { implicit request: DeclarationRequest[JsValue] =>
          request.body.validate[ReportingNilReturn] match {
            case JsError(_) =>
              Future.successful(BadRequest(Json.toJson(MalformedJsonFailureErr(message = "Request body contains malformed JSON"))))
            case _ =>
              val nilReturnReported = request.body.as[ReportingNilReturn].nilReturn
              val result = for {
                _             <- EitherT(etmpService.validateEtmpSubmissionEligibility(zReference))
                _             <- submissionService.declare(zReference, validTaxYear, validMonth, nilReturnReported)
                boxIdResponse <- EitherT(ppnsService.getBoxId(request.clientId))
              } yield boxIdResponse

              result.value.flatMap {
                case Left(error) =>
                  logger.error(s"Failed to declare return for IM ref: [$zReference] for [$month][$taxYear] with error: [$error]")
                  Future.successful(HttpHelper.toHttpError(error))
                case Right(optBoxId) =>
                  notificationContextService.saveContext(request.clientId, optBoxId, zReference).map {
                    case Left(error) =>
                      logger.error(s"Failed to save notification context for IM ref: [$zReference]for [$month][$taxYear], error: [$error]")
                      HttpHelper.toHttpError(error)
                    case Right(_) =>
                      logger.info(s"Declaration of return successful for IM ref: [$zReference] for [$month][$taxYear]")
                      val returnResultsSummaryLocation =
                        config.selfHost +
                          routes.ReturnsSummaryController
                            .retrieveReturnSummary(zReference, taxYear, month)
                            .url
                      Ok(Json.toJson(DeclarationSuccessfulResponse(returnResultsSummaryLocation, optBoxId)))
                  }
              }
          }
        }
    }
}
