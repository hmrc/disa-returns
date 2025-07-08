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

package uk.gov.hmrc.disareturns.connectors

import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.connectors.response.{EtmpObligations, EtmpReportingWindow}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps, UpstreamErrorResponse}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ETMPConnector @Inject() (http: HttpClientV2, appConfig: AppConfig)(implicit ec: ExecutionContext) {

  def checkReturnsObligationStatus(
    isaManagerReferenceNumber: String
  )(implicit hc:               HeaderCarrier): Future[Either[UpstreamErrorResponse, EtmpObligations]] = {

    val url = s"${appConfig.etmpBaseUrl}/disa-returns-stubs/etmp/check-obligation-status/$isaManagerReferenceNumber"
    http
      .get(url"$url")
      .execute[EtmpObligations]
      .map(Right(_)) // Successful HTTP response -> wrap in Right
      .recover {
        case e:     UpstreamErrorResponse => Left(e) //Failed HTTP response -> wrap error in Left
        case other: Throwable             =>
          // wrap unexpected errors into an UpstreamErrorResponse??
          Left(UpstreamErrorResponse(s"Unexpected error: ${other.getMessage}", 500))
      }
  }

  def checkReportingWindowStatus(implicit hc: HeaderCarrier): Future[Either[UpstreamErrorResponse, EtmpReportingWindow]] = {

    val url = s"${appConfig.etmpBaseUrl}/disa-returns-stubs/etmp/check-reporting-window"

    http
      .get(url"$url")
      .execute[EtmpReportingWindow]
      .map(Right(_))
      .recover {
        case e:     UpstreamErrorResponse => Left(e)
        case other: Throwable             =>
          // wrap unexpected errors into an UpstreamErrorResponse??
          Left(UpstreamErrorResponse(s"Unexpected error: ${other.getMessage}", 500))
      }
  }
}
