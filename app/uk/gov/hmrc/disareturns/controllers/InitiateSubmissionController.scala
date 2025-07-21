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
import com.google.inject.Inject
import jakarta.inject.Singleton
import play.api.libs.json._
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.disareturns.controllers.actionBuilders._
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.initiate.inboundRequest.SubmissionRequest
import uk.gov.hmrc.disareturns.models.initiate.response.SuccessResponse
import uk.gov.hmrc.disareturns.services.{ETMPService, PPNSService, ReturnMetadataService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InitiateSubmissionController @Inject() (
  cc:                    ControllerComponents,
  etmpService:           ETMPService,
  ppnsService:           PPNSService,
  returnMetadataService: ReturnMetadataService,
  clientIdAction:        ClientIdAction,
  authAction:            AuthAction
)(implicit ec:           ExecutionContext)
    extends BackendController(cc)
    with WithJsonBodyWithBadRequest {

  def initiate(isaManagerReferenceNumber: String): Action[JsValue] =
    (Action andThen authAction andThen clientIdAction).async(parse.json) { implicit request =>
      if (IsaRefValidator.isValid(isaManagerReferenceNumber)) {
        withJsonBody[SubmissionRequest] { submissionRequest =>
          (for {
            _     <- etmpService.validateEtmpSubmissionEligibility(isaManagerReferenceNumber)
            boxId <- ppnsService.getBoxId(request.clientId)
            returnId <- EitherT.right[ErrorResponse] {
                          returnMetadataService.saveReturnMetadata(
                            boxId = boxId,
                            submissionRequest = submissionRequest,
                            isaManagerReference = isaManagerReferenceNumber
                          )
                        }
          } yield SuccessResponse(
            returnId = returnId,
            action = SubmissionRequest.setAction(submissionRequest.totalRecords),
            boxId = boxId
          )).value.map {
            case Right(response)         => Ok(Json.toJson(response))
            case Left(InternalServerErr) => InternalServerError(Json.toJson(InternalServerErr: ErrorResponse))
            case Left(Unauthorised)      => Unauthorized(Json.toJson(Unauthorised: ErrorResponse))
            case Left(error)             => Forbidden(Json.toJson(error))
          }
        }
      } else {
        Future.successful(BadRequest(Json.toJson(BadRequestInvalidIsaRefErr: ErrorResponse)))
      }
    }
}
