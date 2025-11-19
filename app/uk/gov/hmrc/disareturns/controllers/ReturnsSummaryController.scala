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
import uk.gov.hmrc.disareturns.services.ReturnsSummaryService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReturnsSummaryController @Inject() (
  cc:                    ControllerComponents,
  returnsSummaryService: ReturnsSummaryService,
  authAction:            AuthAction
)(implicit ec:           ExecutionContext)
    extends BackendController(cc)
    with Logging
    with WithJsonBodyWithBadRequest {

  def retrieveReturnSummary(isaManagerReferenceNumber: String, taxYear: String, month: String): Action[AnyContent] =
    (Action andThen authAction).async { _ =>
      ValidationHelper.validateParams(isaManagerReferenceNumber, taxYear, month) match {
        case Left(errors) =>
          Future.successful(BadRequest(Json.toJson(errors)))

        case Right((isaManagerReferenceNumber, taxYear, month, _)) =>
          returnsSummaryService.retrieveReturnSummary(isaManagerReferenceNumber, taxYear, month).map {
            case Left(e: InternalServerErr) =>
              InternalServerError(Json.toJson(e))
            case Left(e: ReturnNotFoundErr) =>
              logger.warn(s"Return summary not found for IM ref: [$isaManagerReferenceNumber] for [$month][$taxYear]")
              NotFound(Json.toJson(e))
            case Right(summary) =>
              logger.info(s"Retrieval of return summary successful for IM ref: [$isaManagerReferenceNumber] for [$month][$taxYear]")
              Ok(Json.toJson(summary))
          }
      }
    }

  def returnsSummaryCallback(isaManagerReferenceNumber: String, taxYear: String, month: String): Action[JsValue] =
    Action.async(parse.json) { implicit req =>
      withJsonBody[MonthlyReturnsSummaryReq] { req =>
        ValidationHelper.validateParams(isaManagerReferenceNumber, taxYear, month) match {
          case Left(errors) =>
            Future.successful(BadRequest(Json.toJson(errors)))

          case Right((isaManagerReferenceNumber, taxYear, month, _)) =>
            returnsSummaryService.saveReturnsSummary(MonthlyReturnsSummary(isaManagerReferenceNumber, taxYear, month, req.totalRecords)).map {
              case Right(_) =>
                logger.info(s"Callback with return summary successful for IM ref: [$isaManagerReferenceNumber] for [$month][$taxYear]")
                NoContent
              case Left(e: InternalServerErr) =>
                InternalServerError(Json.toJson(e))
            }
        }
      }
    }
}
