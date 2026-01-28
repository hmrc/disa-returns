/*
 * Copyright 2026 HM Revenue & Customs
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

import com.google.inject.Inject
import jakarta.inject.Singleton
import play.api.Logging
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.disareturns.models.common.{DeclarationRequest, MalformedJsonFailureErr}
import uk.gov.hmrc.disareturns.models.declaration.ReportingNilReturn

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NilReturnAction @Inject() (implicit ec: ExecutionContext) extends ActionRefiner[DeclarationRequest, DeclarationRequest] with Logging {

  override protected def executionContext: ExecutionContext = ec

  override def refine[A](request: DeclarationRequest[A]): Future[Either[Result, DeclarationRequest[A]]] = Future {

    val nilReturnReported: Either[Result, Boolean] = request.body match {
      case jsOpt: Option[play.api.libs.json.JsValue] =>
        jsOpt match {
          case Some(js: JsValue) =>
            js.validate[ReportingNilReturn]
              .fold(
                errors => {
                  logger.warn(s"Failed to parse NilReturn JSON: ${JsError.toJson(errors)}")
                  Left(Results.BadRequest(Json.toJson(MalformedJsonFailureErr)))
                },
                model => Right(model.nilReturn)
              )
          case None => Right(false)
        }
      case _ => Right(false)
    }
    nilReturnReported.map(nr => request.copy(nilReturnReported = nr))
  }
}
