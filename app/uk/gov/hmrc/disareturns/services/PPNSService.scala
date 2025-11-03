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

import play.api.Logging
import uk.gov.hmrc.disareturns.connectors.PPNSConnector
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InternalServerErr}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PPNSService @Inject() (connector: PPNSConnector)(implicit ec: ExecutionContext) extends Logging {

  def getBoxId(clientId: String)(implicit hc: HeaderCarrier): Future[Either[ErrorResponse, Option[String]]] = {
    logger.info(s"Getting boxId for clientId: [$clientId]")
    connector.getBox(clientId).map {
      case Right(boxOpt) => Right(boxOpt)
      case Left(_)       => Left(InternalServerErr())
    }
  }
}
