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

import cats.data.EitherT
import uk.gov.hmrc.disareturns.config.{AppConfig, Constants}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.HttpReads.Implicits._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PPNSConnector @Inject() (httpClient: HttpClientV2, appConfig: AppConfig)(implicit val ec: ExecutionContext) extends BaseConnector {

  def getBox(clientId: String)(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, HttpResponse] = {
    val url = s"${appConfig.ppnsBaseUrl}/box"
    read(
      httpClient
        .get(url"$url")
        .transform(_.withQueryStringParameters(Seq("clientId" -> clientId, "boxName" -> Constants.BoxName): _*))
        .execute[Either[UpstreamErrorResponse, HttpResponse]],
      context = "PPNSConnector: getBox"
    )
  }
}
