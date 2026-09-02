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

package uk.gov.hmrc.disareturns.testOnly.connectors

import play.api.http.Status.OK
import play.api.libs.json.{JsError, JsSuccess, Json, Reads}
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}

import java.time.{Instant, LocalDate}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class TestOnlySubmissionOverridesConnector @Inject() (
  httpClient: HttpClientV2,
  appConfig:  AppConfig
)(implicit ec: ExecutionContext) {

  import TestOnlySubmissionOverridesConnector.*

  def get(zReference: String)(implicit hc: HeaderCarrier): Future[TestOverride] = {
    val endpoint = s"${appConfig.submissionBaseUrl}/disa-returns-submission/test-only/overrides/$zReference"

    httpClient.get(url"$endpoint").execute[HttpResponse].flatMap { response =>
      if (response.status == OK) Future.fromTry(Try(response.json.as[TestOverride]))
      else Future.failed(UpstreamErrorResponse("Failed to get submission clock context", response.status))
    }
  }
}

object TestOnlySubmissionOverridesConnector {
  final case class ClockOverride(date: LocalDate)
  final case class ReportingWindowOverride(startDate: Instant, endDate: Instant)
  final case class TestOverride(
    zReference:      String,
    clock:           Option[ClockOverride],
    reportingWindow: Option[ReportingWindowOverride]
  )

  private def temporalReads[A](parse: String => A, error: String): Reads[A] =
    Reads.StringReads.flatMapResult(value => Try(parse(value)).fold(_ => JsError(error), JsSuccess(_)))

  private given Reads[LocalDate] = temporalReads(LocalDate.parse, "error.expected.date.iso")
  private given Reads[Instant]   = temporalReads(Instant.parse, "error.expected.instant.iso")
  given Reads[ClockOverride]           = Json.reads[ClockOverride]
  given Reads[ReportingWindowOverride] = Json.reads[ReportingWindowOverride]
  given Reads[TestOverride]            = Json.reads[TestOverride]
}
