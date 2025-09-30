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
import play.api.mvc.{Action, ControllerComponents, Result}
import uk.gov.hmrc.disareturns.models.common.Month.Month
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.summary.repository.{MonthlyReturnsSummary, SaveReturnsSummaryResult}
import uk.gov.hmrc.disareturns.models.summary.request.MonthlyReturnsSummaryReq
import uk.gov.hmrc.disareturns.services.ReturnsSummaryService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class ReturnsSummaryController @Inject() (
  cc:                    ControllerComponents,
  returnsSummaryService: ReturnsSummaryService
)(implicit ec:           ExecutionContext)
    extends BackendController(cc)
    with Logging
    with WithJsonBodyWithBadRequest {

  def returnsSummaryCallback(zRef: String, year: String, month: String): Action[JsValue] =
    Action.async(parse.json) { implicit req =>
      if (IsaRefValidator.isValid(zRef)) {
        withJsonBody[MonthlyReturnsSummaryReq] { req =>
          parseAndValidateYearMonth(year, month) match {
            case Left(badResult) =>
              Future.successful(badResult)

            case Right((yy, mm)) =>
              returnsSummaryService.saveReturnsSummary(MonthlyReturnsSummary(zRef, yy, mm, req.totalRecords)).map {
                case SaveReturnsSummaryResult.Saved      => NoContent
                case SaveReturnsSummaryResult.Error(msg) => InternalServerError(Json.toJson(InternalServerErr(msg)))
              }
          }
        }
      } else {
        Future.successful(BadRequest(Json.toJson(BadRequestErr(message = "ISA Manager Reference Number format is invalid"))))
      }
    }

  private def parseAndValidateYearMonth(year: String, month: String): Either[Result, (Int, Month)] = {
    val taxYearPattern  = "^20\\d{2}-\\d{2}$".r
    val monthToEnum     = Try(Month.withName(month)).toOption
    val taxYearValid    = taxYearPattern.matches(year)
    lazy val taxYearEnd = year.take(4).toInt + 1

    val issues =
      (if (!taxYearValid || taxYearEnd < 2026) Seq(BadRequestErr(message = "Invalid parameter for tax year")) else Nil) ++
        (if (monthToEnum.isEmpty) Seq(BadRequestErr(message = "Invalid parameter for month")) else Nil)

    if (issues.nonEmpty) Left(BadRequest(Json.toJson(MultipleErrorResponse("BAD_REQUEST", "Issue(s) with your request", issues))))
    else Right((taxYearEnd, monthToEnum.get))
  }
}
