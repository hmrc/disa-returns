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

package uk.gov.hmrc.disareturns.connectors

import play.api.http.Status.{NOT_FOUND, OK}
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DisaAccountConnector @Inject() (
  httpClient:  HttpClientV2,
  appConfig:   AppConfig
)(implicit ec: ExecutionContext) {

  def getCompanyName(zRef: String)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    val registrationUrl = s"${appConfig.disaAccountBaseUrl}/disa-account/registration/$zRef"

    httpClient.get(url"$registrationUrl").execute[HttpResponse].flatMap { response =>
      response.status match {
        case OK        => Future.successful((response.json \ "businessVerification" \ "companyName").asOpt[String])
        case NOT_FOUND => Future.successful(None)
        case status    => Future.failed(UpstreamErrorResponse("Failed to get disa-account registration details", status))
      }
    }
  }
}
