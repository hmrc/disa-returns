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

import com.google.inject.Inject
import play.api.Logging
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InternalServerErr}
import uk.gov.hmrc.disareturns.models.summary.repository.NotificationContext
import uk.gov.hmrc.disareturns.repositories.NotificationContextRepository

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NotificationContextService @Inject() (repository: NotificationContextRepository)(implicit ec: ExecutionContext) extends Logging {

  def saveContext(clientId: String, boxId: Option[String], isaManagerReference: String): Future[Either[ErrorResponse, Unit]] =
    repository
      .insertNotificationContext(NotificationContext(clientId, boxId, isaManagerReference))
      .map(_ => Right())
      .recover { case ex: Throwable =>
        logger.error(s"Failed to insertNotificationContext for isaManagerReference [$isaManagerReference]. Error: ${ex.getMessage}", ex)
        Left(InternalServerErr())
      }
  def retrieveContext(zRef: String): Future[Option[NotificationContext]] =
    repository.findNotificationContext(zRef)

}
