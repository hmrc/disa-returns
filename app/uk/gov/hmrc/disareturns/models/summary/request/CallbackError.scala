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

package uk.gov.hmrc.disareturns.models.summary.request

import play.api.libs.json.{Json, OWrites}
import play.api.mvc.Result
import play.api.mvc.Results.{BadRequest, InternalServerError, NotFound}

final case class Issue(fieldName: String, issue: String)
object Issue { implicit val w: OWrites[Issue] = Json.writes[Issue] }

final case class CallbackError(code: String, message: String, issues: Option[Seq[Issue]] = None)
object CallbackError { implicit val w: OWrites[CallbackError] = Json.writes[CallbackError] }

object CallbackResponses {

  def notFound(msg: String): Result =
    NotFound(Json.toJson(CallbackError("NOT_FOUND", msg)))

  def badRequestWith(issues: Seq[Issue]): Result =
    BadRequest(Json.toJson(CallbackError("BAD_REQUEST", "Issue(s) with your request", Some(issues))))

  def internalError(msg: String): Result =
    InternalServerError(Json.toJson(CallbackError("INTERNAL_SERVER_ERROR", msg)))
}
