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
import play.api.libs.json.{JsError, JsSuccess}
import play.api.mvc.{Action, BodyParser, ControllerComponents, Result}
import uk.gov.hmrc.disareturns.models.common.Month
import uk.gov.hmrc.disareturns.models.common.Month.Month
import uk.gov.hmrc.disareturns.models.summary.repository.SaveReturnsSummaryResult
import uk.gov.hmrc.disareturns.models.summary.request.CallbackResponses._
import uk.gov.hmrc.disareturns.models.summary.request.{Issue, MonthlyReturnsSummaryReq}
import uk.gov.hmrc.disareturns.services.ReturnsSummaryService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReturnsSummaryController @Inject() (
  cc:                    ControllerComponents,
  returnsSummaryService: ReturnsSummaryService
)(implicit ec:           ExecutionContext)
    extends BackendController(cc)
    with Logging {

  private val returnsSummaryParser: BodyParser[MonthlyReturnsSummaryReq] = parse.json.validate { json =>
    json.validate[MonthlyReturnsSummaryReq] match {
      case JsSuccess(value, _) => Right(value)
      case JsError(errors) =>
        val response = badRequestWith(
          errors.map { case (path, err) =>
            Issue(path.toString(), err.toString())
          }.toSeq
        )

        Left(response)
    }
  }

  def returnsSummaryCallback(zRef: String, year: Int, month: Int): Action[MonthlyReturnsSummaryReq] =
    Action.async(returnsSummaryParser) { implicit req =>
      parseAndValidateYearMonth(year, month) match {
        case Left(badResult) =>
          Future.successful(badResult)

        case Right((yy, mm)) =>
          returnsSummaryService.saveReturnsSummary(zRef, yy, mm, req.body.totalRecords).map {
            case SaveReturnsSummaryResult.Saved         => NoContent
            case SaveReturnsSummaryResult.NotFound(msg) => notFound(msg)
            case SaveReturnsSummaryResult.Error(msg)    => internalError(msg)
          }
      }
    }

  private def parseAndValidateYearMonth(year: Int, month: Int): Either[Result, (Int, Month)] = {
    val monthToEnum = Month.fromInt(month)

    val issues =
      (if (year < 2026) Seq(Issue("year", "Invalid parameter for year")) else Nil) ++
        (if (monthToEnum.isEmpty) Seq(Issue("month", "Invalid parameter for month")) else Nil)

    if (issues.nonEmpty) Left(badRequestWith(issues))
    else Right((year, monthToEnum.get))
  }
}
