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
import uk.gov.hmrc.disareturns.services.{ETMPService, NPSService, PPNSService}
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
  cc:             ControllerComponents,
  etmpService:    ETMPService,
  ppnsService:    PPNSService,
  npsService:     NPSService,
  authAction:     AuthAction,
  clientIdAction: ClientIdAction,
  config:         AppConfig
)(implicit ec:    ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def declare(isaManagerReferenceNumber: String, taxYear: String, month: String): Action[AnyContent] =
    (Action andThen authAction andThen clientIdAction).async { implicit request =>
      ValidationHelper.validateParams(isaManagerReferenceNumber, taxYear, month) match {
        case Left(errors) =>
          Future.successful(BadRequest(Json.toJson(errors)))
        case Right((isaManagerReferenceNumber, _, _)) =>
          val result: EitherT[Future, ErrorResponse, Option[String]] = for {
            _             <- EitherT(etmpService.validateEtmpSubmissionEligibility(isaManagerReferenceNumber))
            _             <- etmpService.declaration(isaManagerReferenceNumber)
            _             <- npsService.notification(isaManagerReferenceNumber)
            boxIdResponse <- EitherT(ppnsService.getBoxId(request.clientId))
          } yield boxIdResponse
          result.fold(
            error => {
              logger.error(s"Failed to declare return for IM ref: [$isaManagerReferenceNumber] for [$month][$taxYear] with error: [$error]")
              HttpHelper.toHttpError(error)
            },
            optBoxId => {
              logger.info(s"Declaration of return successful for IM ref: [$isaManagerReferenceNumber] for [$month][$taxYear]")
              Ok(Json.toJson(successfulResponse(isaManagerReferenceNumber, taxYear, month, optBoxId)))
            }
          )
      }
    }
  private def successfulResponse(
    isaManagerReferenceNumber: String,
    taxYear:                   String,
    month:                     String,
    optBoxId:                  Option[String]
  ): DeclarationSuccessfulResponse = {
    val returnResultsSummaryLocation =
      config.selfHost + routes.ReturnsSummaryController.retrieveReturnSummary(isaManagerReferenceNumber, taxYear, month).url
    DeclarationSuccessfulResponse(returnResultsSummaryLocation, optBoxId)
  }

}
