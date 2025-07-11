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
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.disareturns.models.common.SubmissionRequest
import uk.gov.hmrc.disareturns.models.errors.connector.responses.{ErrorResponse, ValidationFailureResponse}
import uk.gov.hmrc.disareturns.models.errors.response.{ResponseAction, SuccessResponse}
import uk.gov.hmrc.disareturns.services.{ETMPService, InitiateSubmissionDataService, PPNSService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionController @Inject() (
  cc:                         ControllerComponents,
  val authConnector:          AuthConnector,
  etmpService:                ETMPService,
  ppnsService:                PPNSService,
  mongoJourneyAnswersService: InitiateSubmissionDataService
)(implicit
  ec: ExecutionContext
) extends BackendController(cc)
    with AuthorisedFunctions {

  def initiateSubmission(isaManagerReferenceNumber: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    authorised() {
      etmpService.checkEtmpSubmissionStatuses(isaManagerReferenceNumber).value.map {
        case Right((_, _)) =>
          Ok(Json.toJson(SuccessResponse(returnId = "", action = ResponseAction.SUBMIT_RETURN_TO_PAGINATED_API, boxId = "")))
        case Left(error) =>
          // Map ValidationError to HTTP response
          Forbidden(Json.toJson(error))
      }
    }
  }

  def initiateSubmission2(isaManagerReferenceNumber: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    authorised() {
      request.body
        .validate[SubmissionRequest]
        .fold(
          errors => {
              val jsError = JsError(errors)
              val validationFailure = ValidationFailureResponse.convertErrors(jsError)
              Future.successful(BadRequest(Json.toJson(validationFailure)))
            },
          submissionRequest =>
            (for {
              _     <- etmpService.checkEtmpSubmissionStatuses(isaManagerReferenceNumber)
              boxId <- ppnsService.getBoxId(isaManagerReferenceNumber) // swap to clientId, build into authorisedAction
              returnId <- EitherT.right[ErrorResponse](mongoJourneyAnswersService
                .saveInitiateSubmission(
                  boxId = boxId,
                  submissionRequest = submissionRequest,
                  isaManagerReference = isaManagerReferenceNumber))
            } yield SuccessResponse(
              returnId = returnId,
              action = ResponseAction.SUBMIT_RETURN_TO_PAGINATED_API, //Update for Nil Return
              boxId = boxId
            )).value.map {
              case Right(response) => Ok(Json.toJson(response))
              case Left(error)     => Forbidden(Json.toJson(error))
            }
        )
    }
  }
}
