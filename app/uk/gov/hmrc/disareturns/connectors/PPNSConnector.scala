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

import com.typesafe.config.Config
import org.apache.pekko.actor.ActorSystem
import play.api.libs.json.Json
import uk.gov.hmrc.disareturns.config.{AppConfig, Constants}
import uk.gov.hmrc.disareturns.models.summary.ReturnSummaryResults
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps, UpstreamErrorResponse}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PPNSConnector @Inject() (
  httpClient:                 HttpClientV2,
  appConfig:                  AppConfig,
  override val configuration: Config,
  override val actorSystem:   ActorSystem
)(implicit val ec:            ExecutionContext)
    extends BaseConnector {

  def getBox(clientId: String)(implicit hc: HeaderCarrier): Future[Either[UpstreamErrorResponse, Option[String]]] = {
    val url = s"${appConfig.ppnsBaseUrl}/box"

    retryFor("get PPNS box")(retryCondition) {
      httpClient
        .get(url"$url")
        .transform(_.withQueryStringParameters(Seq("clientId" -> clientId, "boxName" -> Constants.BoxName): _*))
        .executeOrFail
    }
      .map { response =>
        response.status match {
          case 200 =>
            val optBoxId = (response.json \ "boxId").asOpt[String]
            logger.info(s"[PPNSConnector][getBox] Successfully retrieved boxId: $optBoxId for clientId=$clientId")
            Right(optBoxId)
          case 404 =>
            logger.warn(s"[PPNSConnector][getBox] Box not found for clientId=$clientId (status 404)")
            Right(None)
          case other =>
            logger.error(s"[PPNSConnector][getBox] Unexpected response from PPNS: status=$other, body=${response.body}")
            Left(UpstreamErrorResponse(s"Unexpected status from PPNS: $other", other))
        }
      }
      .recover {
        case upstream: UpstreamErrorResponse if upstream.statusCode == 404 =>
          logger.warn(s"[PPNSConnector][getBox] Box not found for clientId=$clientId (status 404)")
          Right(None)
        case upstream: UpstreamErrorResponse =>
          Left(UpstreamErrorResponse(s"Unexpected status from PPNS: ${upstream.statusCode}", upstream.statusCode))
      }
  }

  def sendNotification(
    boxId:       String,
    payload:     ReturnSummaryResults
  )(implicit hc: HeaderCarrier): Future[Unit] =
    httpClient
      .post(url"${appConfig.ppnsBaseUrl}/box/$boxId/notifications")
      .withBody(Json.toJson(payload))
      .executeOrFail
      .map { response =>
        if (response.status == 201) logger.info(s"[PPNSConnector][sendNotification] Sent notification to boxId=$boxId")
        else logger.error(s"[PPNSConnector][sendNotification] Unexpected status=${response.status}, body=${response.body}, boxId=$boxId")
        ()
      }
      .recover { case upstream: UpstreamErrorResponse =>
        logger.error(s"[PPNSConnector][sendNotification] Unexpected status=${upstream.statusCode}, body=${upstream.message}, boxId=$boxId")
        ()
      }

}
