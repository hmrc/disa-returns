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

package uk.gov.hmrc.disareturns.controllers.actionBuilders

import jakarta.inject.Singleton
import play.api.libs.json.Json
import play.api.mvc.Results.BadRequest
import play.api.mvc.{ActionFilter, Request, Result}
import uk.gov.hmrc.disareturns.models.common.{BadRequestInvalidIsaRefErr, ErrorResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex

@Singleton
class IsaRefAction(isaManagerReferenceNumber: String)(implicit ec: ExecutionContext)
  extends ActionFilter[Request] {

  val isaRefRegex: Regex = "^Z([0-9]{4}|[0-9]{6})$".r

  override protected def filter[A](request: Request[A]): Future[Option[Result]] = {
    val isaRefChecker = isaRefRegex.pattern.matcher(isaManagerReferenceNumber).matches()
    isaRefChecker match {
      case true =>
        Future.successful(None)
      case false =>
        Future.successful(Some(BadRequest(Json.toJson(BadRequestInvalidIsaRefErr: ErrorResponse))))
    }
  }

  override protected def executionContext: ExecutionContext = ec
}