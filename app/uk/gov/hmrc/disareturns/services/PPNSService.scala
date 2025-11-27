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
import uk.gov.hmrc.disareturns.models.summary.ReturnSummaryResults
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PPNSService @Inject() (ppnsConnector: PPNSConnector, notificationContextService: NotificationContextService)(implicit ec: ExecutionContext)
    extends Logging {

  def getBoxId(clientId: String)(implicit hc: HeaderCarrier): Future[Either[ErrorResponse, Option[String]]] = {
    logger.info(s"Getting boxId for clientId: [$clientId]")
    ppnsConnector.getBox(clientId).map {
      case Right(boxOpt) => Right(boxOpt)
      case Left(_)       => Left(InternalServerErr())
    }
  }

  def sendNotification(
    isaManagerReference:  String,
    returnSummaryResults: ReturnSummaryResults
  )(implicit hc:          HeaderCarrier): Future[Unit] =
    retrieveBoxId(isaManagerReference).flatMap {
      case Some(boxId) => ppnsConnector.sendNotification(boxId, returnSummaryResults)
      case None =>
        logger.warn(s"Unable to send notification: no boxId found for $isaManagerReference")
        Future.successful(())
    }

  private def retrieveBoxId(
    isaManagerReference: String
  )(implicit hc:         HeaderCarrier): Future[Option[String]] =
    notificationContextService.retrieveContext(isaManagerReference).flatMap {
      case None =>
        logger.warn(s"No notification context found for isaManagerReference: $isaManagerReference")
        Future.successful(None)
      case Some(notificationContext) =>
        notificationContext.boxId match {
          case Some(boxId) => Future.successful(Some(boxId))
          case None =>
            getBoxId(notificationContext.clientId).map {
              case Right(Some(boxId)) => Some(boxId)
              case Right(None) =>
                logger.warn(s"No boxId found for clientId: ${notificationContext.clientId}")
                None
              case Left(err) =>
                logger.warn(s"Failed to a retrieve boxId: $err")
                None
            }
        }
    }

}
