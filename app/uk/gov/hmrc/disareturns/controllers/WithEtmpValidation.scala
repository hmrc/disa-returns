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

import play.api.libs.json.{Json, Writes}
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, MultipleErrorResponse, ObligationClosed, ReportingWindowClosed}
import uk.gov.hmrc.disareturns.services.ETMPService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}


trait WithEtmpValidation {

  def validateEtmpAndThen[A](isaManagerReference: String)(block: () => Future[Result])(
                              implicit etmpService: ETMPService,
                              hc: HeaderCarrier,
                              ec: ExecutionContext,
                              writes: Writes[ErrorResponse]
                            ): Future[Result] = {
    etmpService
      .validateEtmpSubmissionEligibility(isaManagerReference)
      .value
      .flatMap {
        case Right((_, _)) =>
          block()
        case Left(error: ErrorResponse) =>
          error match {
            case ReportingWindowClosed | ObligationClosed =>
              Future.successful(Forbidden(Json.toJson(error)))
            case MultipleErrorResponse(_, _, errors)
              if errors.exists(e => e == ReportingWindowClosed || e == ObligationClosed) =>
              Future.successful(Forbidden(Json.toJson(error)))
            case _ =>
              Future.successful(InternalServerError(Json.toJson(error)))
          }
      }

  }
}
