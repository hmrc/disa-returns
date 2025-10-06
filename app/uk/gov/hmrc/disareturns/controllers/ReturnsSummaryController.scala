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
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.disareturns.controllers.actionBuilders.AuthAction
import uk.gov.hmrc.disareturns.models.common.Month.Month
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.summary.TaxYearValidator
import uk.gov.hmrc.disareturns.models.summary.repository.MonthlyReturnsSummary
import uk.gov.hmrc.disareturns.models.summary.request.MonthlyReturnsSummaryReq
import uk.gov.hmrc.disareturns.services.ReturnsSummaryService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

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
      parseAndValidate(isaManagerReferenceNumber, taxYear, month) match {
        case Left(badResult) =>
          Future.successful(badResult)

        case Right((ty, m)) =>
          returnsSummaryService.retrieveReturnSummary(isaManagerReferenceNumber, ty, m).map {
            case Left(e: InternalServerErr) => InternalServerError(Json.toJson(e))
            case Left(e: ReturnNotFoundErr) => NotFound(Json.toJson(e))
            case Right(summary)             => Ok(Json.toJson(summary))
          }
      }
    }

  def returnsSummaryCallback(zRef: String, taxYear: String, month: String): Action[JsValue] =
    Action.async(parse.json) { implicit req =>
      withJsonBody[MonthlyReturnsSummaryReq] { req =>
        parseAndValidate(zRef, taxYear, month) match {
          case Left(badResult) =>
            Future.successful(badResult)

          case Right((yy, mm)) =>
            returnsSummaryService.saveReturnsSummary(MonthlyReturnsSummary(zRef, yy, mm, req.totalRecords)).map {
              case Right(_)                   => NoContent
              case Left(e: InternalServerErr) => InternalServerError(Json.toJson(e))
            }
        }
      }
    }

  private def parseAndValidate(zRef: String, taxYear: String, month: String): Either[Result, (String, Month)] = {
    val zRefValid    = IsaRefValidator.isValid(zRef)
    val monthToEnum  = Try(Month.withName(month)).toOption
    val taxYearValid = TaxYearValidator.isValid(taxYear)

    val issues = {
      (if (!zRefValid) Seq(BadRequestErr(message = "ISA Manager Reference Number format is invalid")) else Nil) ++
        (if (!taxYearValid) Seq(BadRequestErr(message = "Invalid parameter for tax year")) else Nil) ++
        (if (monthToEnum.isEmpty) Seq(BadRequestErr(message = "Invalid parameter for month")) else Nil)
    }

    if (issues.nonEmpty) Left(BadRequest(Json.toJson(MultipleErrorResponse("BAD_REQUEST", "Issue(s) with your request", issues))))
    else Right((taxYear, monthToEnum.get))
  }
}
