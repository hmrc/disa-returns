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
import play.api.mvc.{Action, ControllerComponents, Request}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.disareturns.connectors.response.{EtmpObligations, EtmpReportingWindow}
import uk.gov.hmrc.disareturns.models.common.{InitiateSubmission, SubmissionRequest}
import uk.gov.hmrc.disareturns.models.errors
import uk.gov.hmrc.disareturns.models.errors._
import uk.gov.hmrc.disareturns.models.errors.MultipleSubmissionErrorResponse.toErrorDetail
import uk.gov.hmrc.disareturns.models.errors.response.{SubmissionSuccessResponse, SubmitReturnToPaginatedApi}
import uk.gov.hmrc.disareturns.services.{ETMPService, MongoJourneyAnswersService, PPNSService}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class SubmissionController @Inject() (
                                       cc:                         ControllerComponents,
                                       val authConnector:          AuthConnector,
                                       etmpService: ETMPService,
                                       ppnsService: PPNSService,
                                       mongoJourneyAnswersService: MongoJourneyAnswersService
)(implicit
  ec: ExecutionContext
) extends BackendController(cc)
    with AuthorisedFunctions {

  def initiateSubmission(isManagerReferenceNumber: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    authorised() {
      parseBody[SubmissionRequest](request) match {
        case Success(jsResult) =>
          jsResult match {
            case JsSuccess(submission, _) =>
              val clientId = request.headers.get("X-Client-ID").getOrElse("")

              logic(isManagerReferenceNumber, clientId).flatMap {
                case Left(_) =>
                  Future.successful(InternalServerError(Json.toJson(errors.InternalServerError: SubmissionError)))

                case Right((obligation, reportingWindow, boxId)) =>
                  (obligation.obligationAlreadyMet, reportingWindow.reportingWindowOpen) match {
                    case (true, false) =>
                      val errors = Seq(ObligationClosed, ReportingWindowClosed).map(toErrorDetail)
                      val response = MultipleSubmissionErrorResponse(
                        code = "FORBIDDEN",
                        message = "Multiple issues found regarding your submission",
                        errors = errors
                      )
                      Future.successful(Forbidden(Json.toJson(response)))

                    case (true, _) =>
                      Future.successful(Forbidden(Json.toJson(ObligationClosed: SubmissionError)))

                    case (_, false) =>
                      Future.successful(Forbidden(Json.toJson(ReportingWindowClosed: SubmissionError)))

                    case (false, true) =>
                      mongoJourneyAnswersService
                        .save(InitiateSubmission.create(boxId, submission, isManagerReferenceNumber))
                        .map { returnId =>
                          val response = SubmissionSuccessResponse(returnId, SubmitReturnToPaginatedApi, boxId)
                          Ok(Json.toJson(response))
                        }
                  }

              }

            case JsError(errors) =>
              Future.successful(
                BadRequest(
                  Json.obj(
                    "message" -> "Invalid request",
                    "errors"  -> JsError.toJson(errors)
                  )
                )
              )
          }

        case Failure(ex) =>
          Future.successful(
            BadRequest(Json.obj("message" -> "Unable to parse JSON", "error" -> ex.getMessage))
          )
      }
    }
  }

  private def logic(
    isaManagerReference: String,
    clientId:            String
  )(implicit hc:         HeaderCarrier, ec: ExecutionContext): Future[Either[UpstreamErrorResponse, (EtmpObligations, EtmpReportingWindow, String)]] =
    for {
      obligationsResult     <- etmpService.checkObligationStatus(isaManagerReference)
      reportingWindowResult <- etmpService.checkReportingWindowStatus()
      //move this call elsewhere maybe ??
      boxIdResult           <- ppnsService.getBoxId(clientId)
    } yield for {
      obligations     <- obligationsResult
      reportingWindow <- reportingWindowResult
      boxId           <- boxIdResult
    } yield (obligations, reportingWindow, boxId)

  private def parseBody[A: Reads](request: Request[JsValue]): Try[JsResult[A]] =
    Try {
      request.body.validate[A]
    }

}
