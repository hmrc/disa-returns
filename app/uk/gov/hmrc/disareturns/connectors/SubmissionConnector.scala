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
import play.api.libs.json.Json
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.models.common.Month.Month
import uk.gov.hmrc.disareturns.models.declaration.ReportingNilReturn
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SubmissionConnector @Inject() (httpClient: HttpClientV2, appConfig: AppConfig)(implicit val ec: ExecutionContext) extends BaseConnector {

  def sendDeclaration(zReference: String, taxYear: String, month: Month, nilReturnReported: Boolean)(implicit
    hc:                           HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, HttpResponse] = {
    val monthInt = month.id + 1
    val url      = s"${appConfig.submissionBaseUrl}/disa-returns-submission/monthly/$zReference/$taxYear/$monthInt/declarations"
    read(
      httpClient
        .post(url"$url")
        .withBody(Json.toJson(ReportingNilReturn(nilReturn = nilReturnReported)))
        .execute[Either[UpstreamErrorResponse, HttpResponse]],
      context = "SubmissionConnector: sendDeclaration"
    )
  }
}
