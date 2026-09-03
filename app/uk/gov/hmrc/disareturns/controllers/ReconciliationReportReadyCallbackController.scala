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
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.callback.{ReconciliationReportReady, ReconciliationReportReadyCallbackRequest}
import uk.gov.hmrc.disareturns.services.{PPNSService, ReconciliationReportReadyCallbackService}
import uk.gov.hmrc.disareturns.utils.ValidationHelper
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReconciliationReportReadyCallbackController @Inject() (
  cc:                                       ControllerComponents,
  reconciliationReportReadyCallbackService: ReconciliationReportReadyCallbackService,
  ppnsService:                              PPNSService
)(implicit ec:                              ExecutionContext)
    extends BackendController(cc)
    with Logging
    with WithJsonBodyWithBadRequest {

  def callback(zReference: String): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withJsonBody[ReconciliationReportReadyCallbackRequest] { body =>
        ValidationHelper.validateParams(zReference) match {
          case Left(errors) => Future.successful(BadRequest(Json.toJson(errors)))
          case Right((zReference, _)) =>
            val reportReady = ReconciliationReportReady(zReference, body.totalRecords)
            reconciliationReportReadyCallbackService.save(reportReady).flatMap {
              case Left(err: InternalServerErr) =>
                Future.successful(InternalServerError(Json.toJson(err)))
              case Left(err) =>
                logger.warn(
                  s"[ReconciliationReportReadyCallbackController][callback] Unexpected error [$err] saving callback for IM ref: [$zReference]"
                )
                Future.successful(InternalServerError(Json.toJson(err)))
              case Right(_) =>
                reconciliationReportReadyCallbackService.buildNotification(zReference).flatMap {
                  case Left(_) =>
                    Future.successful(NoContent)
                  case Right(notification) =>
                    ppnsService.sendReconciliationReportReadyNotification(zReference, notification).map { _ =>
                      logger.info(
                        s"[ReconciliationReportReadyCallbackController][callback] Reconciliation report ready callback successful for IM ref: [$zReference]"
                      )
                      NoContent
                    }
                }
            }
        }
      }
    }
}
