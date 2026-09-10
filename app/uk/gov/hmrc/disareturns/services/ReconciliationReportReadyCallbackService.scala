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
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.controllers.routes
import uk.gov.hmrc.disareturns.models.callback.ReconciliationReportReady
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InternalServerErr}
import uk.gov.hmrc.disareturns.models.ppns.ReconciliationReportReadyNotification
import uk.gov.hmrc.disareturns.repositories.ReconciliationReportReadyRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReconciliationReportReadyCallbackService @Inject() (
  repository:  ReconciliationReportReadyRepository,
  appConfig:   AppConfig
)(implicit ec: ExecutionContext)
    extends Logging {

  def save(reportReady: ReconciliationReportReady): Future[Either[ErrorResponse, Unit]] = {
    logger.info(s"[ReconciliationReportReadyCallbackService][save] Saving callback for IM ref: [${reportReady.zRef}]")

    repository
      .upsert(reportReady)
      .map(_ => Right(()))
      .recover { case e =>
        logger.error(
          s"[ReconciliationReportReadyCallbackService][save] Failed to save callback for IM ref: [${reportReady.zRef}] due to error: [$e]"
        )
        Left(InternalServerErr())
      }
  }

  def buildNotification(zReference: String): Future[Either[ErrorResponse, ReconciliationReportReadyNotification]] = {
    logger.info(s"[ReconciliationReportReadyCallbackService][buildNotification] Building notification for IM ref: [$zReference]")

    lazy val returnResultsLocation =
      s"${appConfig.selfHost}${routes.ReconciliationResultController.retrieveReconciliationReport(zReference).url}"

    repository
      .findByZReference(zReference)
      .map {
        case Some(_) => Right(ReconciliationReportReadyNotification(returnResultsLocation))
        case _       => Left(InternalServerErr())
      }
      .recover { case e =>
        logger.error(
          s"[ReconciliationReportReadyCallbackService][buildNotification] Failed to retrieve callback for IM ref: [$zReference] due to error: [$e]"
        )
        Left(InternalServerErr())
      }
  }
}
