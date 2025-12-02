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
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.disareturns.controllers.actionBuilders._
import uk.gov.hmrc.disareturns.models.common.ErrorResponse
import uk.gov.hmrc.disareturns.services.{ETMPService, NPSService, NotificationContextService, PPNSService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.disareturns.models.helpers.ValidationHelper
import uk.gov.hmrc.disareturns.utils.HttpHelper
import cats.data.EitherT
import cats.implicits._
import play.api.Logging
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.models.declaration.DeclarationSuccessfulResponse

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeclarationController @Inject() (
  cc:                         ControllerComponents,
  etmpService:                ETMPService,
  ppnsService:                PPNSService,
  npsService:                 NPSService,
  notificationContextService: NotificationContextService,
  authAction:                 AuthAction,
  clientIdAction:             ClientIdAction,
  nilReturnAction:            NilReturnAction,
  config:                     AppConfig
)(implicit ec:                ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def declare(isaManagerReferenceNumber: String, taxYear: String, month: String): Action[AnyContent] =
    ValidationHelper.validateParams(isaManagerReferenceNumber, taxYear, month) match {
      case Left(errors) =>
        Action(_ => BadRequest(Json.toJson(errors)))
      case Right((isaManagerReferenceNumber, _, _, _)) =>
        (Action andThen authAction(isaManagerReferenceNumber) andThen clientIdAction andThen nilReturnAction).async { implicit request =>
          val result: EitherT[Future, ErrorResponse, Option[String]] = for {
            _             <- EitherT(etmpService.validateEtmpSubmissionEligibility(isaManagerReferenceNumber))
            _             <- etmpService.declaration(isaManagerReferenceNumber)
            _             <- npsService.notification(isaManagerReferenceNumber, request.nilReturnReported)
            boxIdResponse <- EitherT(ppnsService.getBoxId(request.clientId))
          } yield boxIdResponse
          result.value.flatMap {
            case Left(error) =>
              logger.error(s"Failed to declare return for IM ref: [$isaManagerReferenceNumber] for [$month][$taxYear] with error: [$error]")
              Future.successful(HttpHelper.toHttpError(error))
            case Right(optBoxId) =>
              notificationContextService.saveContext(request.clientId, optBoxId, isaManagerReferenceNumber).map {
                case Left(error) =>
                  logger.error(s"Failed to save notification context for IM ref: [$isaManagerReferenceNumber], error: [$error]")
                  HttpHelper.toHttpError(error)
                case Right(_) =>
                  logger.info(s"Declaration of return successful for IM ref: [$isaManagerReferenceNumber] for [$month][$taxYear]")
                  val returnResultsSummaryLocation =
                    config.selfHost + routes.ReturnsSummaryController.retrieveReturnSummary(isaManagerReferenceNumber, taxYear, month).url
                  Ok(Json.toJson(DeclarationSuccessfulResponse(returnResultsSummaryLocation, optBoxId)))
              }
          }
        }
    }
}
