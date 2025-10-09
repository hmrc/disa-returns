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
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.controllers.actionBuilders._
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.complete.CompleteReturnResponse
import uk.gov.hmrc.disareturns.services.{ETMPService, ReturnMetadataService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.disareturns.utils.HttpHelper

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CompleteReturnController @Inject() (
  cc:                    ControllerComponents,
  etmpService:           ETMPService,
  returnMetadataService: ReturnMetadataService,
  authAction:            AuthAction,
  appConfig:             AppConfig
)(implicit ec:           ExecutionContext)
    extends BackendController(cc) {
  def complete(isaManagerReferenceNumber: String, returnId: String): Action[AnyContent] =
    (Action andThen authAction).async { implicit request =>
      if (!IsaRefValidator.isValid(isaManagerReferenceNumber)) {
        Future.successful(BadRequest(Json.toJson(BadRequestErr(message = "ISA Manager Reference Number format is invalid"): ErrorResponse)))
      } else {
        returnMetadataService
          .existsByIsaManagerReferenceAndReturnId(isaManagerReferenceNumber, returnId)
          .flatMap { returnIdExists =>
            if (!returnIdExists) {
              Future.successful(NotFound(Json.toJson(ReturnIdNotMatchedErr: ErrorResponse)))
            } else {
              etmpService.validateEtmpSubmissionEligibility(isaManagerReferenceNumber).flatMap {
                case Left(error) => Future.successful(HttpHelper.toHttpError(error))
                case Right(_) =>
                  val returnResultsSummaryLocation = appConfig.getReturnResultsSummaryLocation(isaManagerReferenceNumber, returnId)
                  val response                     = CompleteReturnResponse(returnResultsSummaryLocation)
                  etmpService
                    .closeObligationStatus(isaManagerReferenceNumber)
                    .foldF(
                      error => Future.successful(HttpHelper.toHttpError(error)),
                      _ => Future.successful(Ok(Json.toJson(response)))
                    )
              }
            }
          }
      }
    }
}
