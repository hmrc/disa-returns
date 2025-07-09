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

package uk.gov.hmrc.disareturns.services

import cats.data.EitherT
import uk.gov.hmrc.disareturns.connectors.PPNSConnector
import uk.gov.hmrc.disareturns.models.response.ppns.Box
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PPNSService @Inject() (connector: PPNSConnector)(implicit ec: ExecutionContext) {

  def getBoxId(clientId: String)(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, String] =
    connector.getBox(clientId).map(_.json.as[Box].boxId)
}
