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

import com.google.inject.Inject
import jakarta.inject.Singleton
import play.api.libs.json.Json
import play.api.mvc.Results.BadRequest
import play.api.mvc.{ActionRefiner, Request, Result}
import uk.gov.hmrc.disareturns.models.common.{BadRequestMissingHeaderErr, ClientIdRequest, ErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClientIdAction @Inject() (implicit ec: ExecutionContext) extends ActionRefiner[Request, ClientIdRequest] {

  private val ClientIdHeader = "X-Client-ID"
  override protected def executionContext: ExecutionContext = ec

  override def refine[A](request: Request[A]): Future[Either[Result, ClientIdRequest[A]]] = {
    val optionClientId = request.headers.get(ClientIdHeader)
    optionClientId match {
      case Some(clientId) =>
        Future.successful(Right(ClientIdRequest(clientId, request)))
      case None =>
        Future.successful(Left(BadRequest(Json.toJson(BadRequestMissingHeaderErr: ErrorResponse))))
    }
  }
}
