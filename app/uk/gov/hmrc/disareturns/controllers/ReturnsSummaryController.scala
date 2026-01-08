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
import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.disareturns.controllers.actionBuilders.AuthAction
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.helpers.ValidationHelper
import uk.gov.hmrc.disareturns.models.summary.repository.MonthlyReturnsSummary
import uk.gov.hmrc.disareturns.models.summary.request.MonthlyReturnsSummaryReq
import uk.gov.hmrc.disareturns.services.{PPNSService, ReturnsSummaryService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReturnsSummaryController @Inject() (
  cc:                    ControllerComponents,
  returnsSummaryService: ReturnsSummaryService,
  ppnsService:           PPNSService,
  authAction:            AuthAction
)(implicit ec:           ExecutionContext)
    extends BackendController(cc)
    with Logging
    with WithJsonBodyWithBadRequest {

  def retrieveReturnSummary(zReference: String, taxYear: String, month: String): Action[AnyContent] =
    ValidationHelper.validateParams(zReference, taxYear, month) match {
      case Left(errors) =>
        Action(_ => BadRequest(Json.toJson(errors)))
      case Right((zReference, taxYear, month, _)) =>
        (Action andThen authAction(zReference)).async { _ =>
          returnsSummaryService.retrieveReturnSummary(zReference, taxYear, month).map {
            case Left(e: InternalServerErr) =>
              InternalServerError(Json.toJson(e))
            case Left(e: ReturnNotFoundErr) =>
              logger.warn(s"Return summary not found for IM ref: [$zReference] for [$month][$taxYear]")
              NotFound(Json.toJson(e))
            case Right(summary) =>
              logger.info(s"Retrieval of return summary successful for IM ref: [$zReference] for [$month][$taxYear]")
              Ok(Json.toJson(summary))
          }
        }
    }

  def returnsSummaryCallback(
    zReference: String,
    taxYear:    String,
    month:      String
  ): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withJsonBody[MonthlyReturnsSummaryReq] { body =>
        ValidationHelper.validateParams(zReference, taxYear, month) match {
          case Left(errors) => Future.successful(BadRequest(Json.toJson(errors)))
          case Right((zReference, taxYear, month, _)) =>
            val summary = MonthlyReturnsSummary(zReference, taxYear, month, body.totalRecords)
            //TODO Should we consider doing the logic for the numberOfPages etc as part of saving the summary instead of doing it on retrieveReturnSummary???
            returnsSummaryService.saveReturnsSummary(summary).flatMap {
              case Left(err: InternalServerErr) =>
                Future.successful(InternalServerError(Json.toJson(err)))
              case Right(_) =>
                returnsSummaryService.retrieveReturnSummary(zReference, taxYear, month).flatMap {
                  case Left(_) =>
                    Future.successful(NoContent)
                  case Right(returnSummaryResults) =>
                    ppnsService.sendNotification(zReference, returnSummaryResults).map { _ =>
                      logger.info(s"Callback with return summary successful for IM ref: [$zReference] for [$month][$taxYear]")
                      NoContent
                    }
                }
            }
        }
      }
    }
}
