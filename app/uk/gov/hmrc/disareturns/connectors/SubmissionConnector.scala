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
import com.typesafe.config.Config
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR}
import play.api.libs.json.Json
import play.api.libs.ws.{BodyWritable, SourceBody}
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.models.common.Month.Month
import uk.gov.hmrc.disareturns.models.declaration.ReportingNilReturn
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SubmissionConnector @Inject() (
  httpClient:                 HttpClientV2,
  appConfig:                  AppConfig,
  override val configuration: Config,
  override val actorSystem:   ActorSystem
)(implicit val ec:            ExecutionContext)
    extends BaseConnector {

  private implicit val ndjsonBodyWritable: BodyWritable[Source[ByteString, _]] =
    BodyWritable(SourceBody(_), "application/x-ndjson")

  def sendDeclaration(zReference: String, taxYear: String, month: Month, nilReturnReported: Boolean)(implicit
    hc:                           HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, HttpResponse] = {
    val url = s"${appConfig.submissionBaseUrl}/disa-returns-submission/monthly/$zReference/$taxYear/${month.id}/declarations"
    EitherT(
      httpClient
        .post(url"$url")
        .setHeader(authorizationHeader)
        .withBody(Json.toJson(ReportingNilReturn(nilReturn = nilReturnReported)))
        .executeOrFail
        .map { response =>
          if (response.status >= BAD_REQUEST) {
            logger.warn(s"[SubmissionConnector][sendDeclaration] Received error status ${response.status} with body: ${response.body}")
            Left(UpstreamErrorResponse(response.body, response.status, response.status))
          } else {
            Right(response)
          }
        }
        .recover {
          case upstream: UpstreamErrorResponse => Left(upstream)
          case ex =>
            logger.error(s"[SubmissionConnector][sendDeclaration] Unexpected error: ${ex.getMessage}", ex)
            Left(UpstreamErrorResponse(s"Unexpected error: ${ex.getMessage}", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR))
        }
    )
  }

  def createMonthlyReturn(zReference: String, taxYear: String, month: Month, nilReturn: Boolean)(implicit
    hc:                               HeaderCarrier
  ): Future[Either[UpstreamErrorResponse, Unit]] = {
    val url = s"${appConfig.submissionBaseUrl}/disa-returns-submission/monthly/$zReference/$taxYear/${month.id}"
    retryFor("create monthly return submission")(retryCondition) {
      httpClient
        .post(url"$url")
        .setHeader(authorizationHeader)
        .withBody(Json.toJson(ReportingNilReturn(nilReturn = nilReturn)))
        .executeOrFail
    }
      .map { response =>
        if (response.status >= BAD_REQUEST) {
          logger.warn(s"[SubmissionConnector][createMonthlyReturn] Received error status ${response.status} with body: ${response.body}")
          Left(UpstreamErrorResponse(response.body, response.status, response.status))
        } else {
          Right(())
        }
      }
      .recover {
        case upstream: UpstreamErrorResponse => Left(upstream)
        case ex =>
          logger.error(s"[SubmissionConnector][createMonthlyReturn] Unexpected error: ${ex.getMessage}", ex)
          Left(UpstreamErrorResponse(s"Unexpected error: ${ex.getMessage}", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR))
      }
  }

  def sendSubmission(zReference: String, taxYear: String, month: Month, submissionId: UUID, source: Source[ByteString, _])(implicit
    hc:                          HeaderCarrier
  ): Future[Either[UpstreamErrorResponse, Unit]] = {
    val url =
      s"${appConfig.submissionBaseUrl}/disa-returns-submission/monthly/$zReference/$taxYear/${month.id}/submissions/$submissionId"
    retryFor("send monthly return submission")(retryCondition) {
      httpClient
        .put(url"$url")
        .setHeader(authorizationHeader)
        .withBody(source)
        .executeOrFail
    }
      .map { response =>
        if (response.status >= BAD_REQUEST) {
          logger.warn(s"[SubmissionConnector][sendSubmission] Received error status ${response.status} with body: ${response.body}")
          Left(UpstreamErrorResponse(response.body, response.status, response.status))
        } else {
          Right(())
        }
      }
      .recover {
        case upstream: UpstreamErrorResponse => Left(upstream)
        case ex =>
          logger.error(s"[SubmissionConnector][sendSubmission] Unexpected error: ${ex.getMessage}", ex)
          Left(UpstreamErrorResponse(s"Unexpected error: ${ex.getMessage}", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR))
      }
  }

  def getReportingWindowStatus(credId: String)(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, HttpResponse] = {
    val url = s"${appConfig.submissionBaseUrl}/disa-returns-submission/reporting-window/status"
    read(
      retryFor("get submission reporting window status")(retryCondition) {
        httpClient
          .get(url"$url")
          .setHeader(authorizationHeader, "X-Cred-Id" -> credId)
          .executeOrFail
          .map(Right(_))
      },
      context = "[SubmissionConnector][getReportingWindowStatus]"
    )
  }

  private def authorizationHeader: (String, String) =
    AUTHORIZATION -> appConfig.internalAuthToken
}
