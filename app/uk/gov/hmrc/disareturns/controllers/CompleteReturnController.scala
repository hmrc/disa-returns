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
import uk.gov.hmrc.disareturns.controllers.actionBuilders._
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.services.{CompleteReturnService, ETMPService, ReturnMetadataService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.disareturns.utils.HttpHelper

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CompleteReturnController @Inject() (
  cc:                    ControllerComponents,
  etmpService:           ETMPService,
  returnMetadataService: ReturnMetadataService,
  returnsService:        CompleteReturnService,
  authAction:            AuthAction
)(implicit ec:           ExecutionContext)
    extends BackendController(cc)
    with WithJsonBodyWithBadRequest {

  def complete(isaManagerReferenceNumber: String, returnId: String): Action[AnyContent] =
    (Action andThen authAction).async { implicit request =>
      if (!IsaRefValidator.isValid(isaManagerReferenceNumber)) {
        Future.successful(BadRequest(Json.toJson(BadRequestErr(message = "ISA Manager Reference Number format is invalid"): ErrorResponse)))
      } else {
        for {
          returnIdExists <- returnMetadataService.existsByIsaManagerReferenceAndReturnId(isaManagerReferenceNumber, returnId)
          result <- if (!returnIdExists) { Future.successful(NotFound(Json.toJson(ReturnIdNotMatchedErr: ErrorResponse))) }
                    else {
                      etmpService.validateEtmpSubmissionEligibility(isaManagerReferenceNumber).flatMap {
                        case Left(error) => Future.successful(HttpHelper.toHttpError(error))
                        case Right(_) =>
                          returnsService.validateRecordCount(isaManagerReferenceNumber, returnId).map {
                            case Left(err)       => BadRequest(Json.toJson(err))
                            case Right(response) => Ok(Json.toJson(response))
                          }
                      }
                    }
        } yield result
      }
    }

}
