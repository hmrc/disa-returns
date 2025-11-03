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
import play.api.Logging
import play.api.http.Status.NO_CONTENT
import uk.gov.hmrc.disareturns.connectors.NPSConnector
import uk.gov.hmrc.disareturns.models.common.UpstreamErrorMapper.mapToErrorResponse
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InternalServerErr}
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaAccount
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NPSService @Inject() (connector: NPSConnector)(implicit ec: ExecutionContext) extends Logging {

  def notification(isaManagerReference: String)(implicit hc: HeaderCarrier): EitherT[Future, ErrorResponse, HttpResponse] = {
    logger.info(s"Sending notification to NPS for IM ref: [$isaManagerReference]")
    connector.sendNotification(isaManagerReference).leftMap(mapToErrorResponse)
  }

  def submitIsaAccounts(isaManagerReferenceNumber: String, isaAccounts: Seq[IsaAccount])(implicit
    hc:                                            HeaderCarrier
  ): Future[Either[ErrorResponse, Unit]] = {
    logger.info(s"Submitting ISA Accounts to NPS for IM ref: [$isaManagerReferenceNumber]")

    connector.submit(isaManagerReferenceNumber, isaAccounts).value.map {
      case Left(upstreamError) => Left(mapToErrorResponse(upstreamError))
      case Right(response) =>
        response.status match {
          case NO_CONTENT  => Right(())
          case otherStatus => Left(InternalServerErr(s"Unexpected status $otherStatus was received from NPS submission"))
        }
    }
  }
}
