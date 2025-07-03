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
import uk.gov.hmrc.disareturns.models.common.SubmissionRequest
import uk.gov.hmrc.disareturns.services.EtmpService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class SubmissionController @Inject() (cc: ControllerComponents, val authConnector: AuthConnector, etmpService: EtmpService)(implicit
  ec:                                     ExecutionContext
) extends BackendController(cc)
    with AuthorisedFunctions {

  def init(isManagerReferenceNumber: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    authorised() {
      parseBody[SubmissionRequest](request) match {
        case Success(jsResult) =>
          jsResult match {
            case JsSuccess(submission, _) =>
              etmpService.checkReportingWindowStatus().flatMap {
                case Left(value) => Future.successful(InternalServerError)
                case Right(value) =>
                  Future.successful(
                    Ok(Json.obj("message" -> "Submission accepted", "data" -> Json.toJson(submission)))
                  )
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

  private def parseBody[A: Reads](request: Request[JsValue]): Try[JsResult[A]] =
    Try {
      request.body.validate[A]
    }

}
