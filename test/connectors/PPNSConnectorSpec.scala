/*
 * Copyright 2023 HM Revenue & Customs
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

package connectors

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.Json
import uk.gov.hmrc.disareturns.config.Constants
import uk.gov.hmrc.disareturns.connectors.PPNSConnector
import uk.gov.hmrc.disareturns.models.ppns.{Box, BoxCreator}
import uk.gov.hmrc.http.{HttpResponse, StringContextOps, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class PPNSConnectorSpec extends BaseUnitSpec {

  trait TestSetup {

    val testClientId = "test-client-id-12345"
    val testUrl      = "http://localhost:6701"
    val testBoxId    = "Box1"

    when(mockAppConfig.ppnsBaseUrl).thenReturn(testUrl)
    when(mockHttpClient.get(url"$testUrl/box")).thenReturn(mockRequestBuilder)
    when(mockHttpClient.post(url"$testUrl/box/$testBoxId/notifications")).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.transform(any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.withBody(any())(any, any, any)).thenReturn(mockRequestBuilder)

    val connector = new PPNSConnector(mockHttpClient, mockAppConfig)
  }

  "PPNSConnector.getBox" should {

    "return Right(Some(boxId)) when PPNS responds with 200 and valid box" in new TestSetup {

      val boxId = "box_123"
      val expectedResponse: Box = Box(
        boxId = boxId,
        boxName = Constants.BoxName,
        boxCreator = BoxCreator(clientId = testClientId),
        applicationId = Some("applicationId"),
        subscriber = None
      )
      val httpResponse: HttpResponse = HttpResponse(200, Json.toJson(expectedResponse).toString())

      when(mockRequestBuilder.execute[HttpResponse](any(), any())).thenReturn(Future.successful(httpResponse))
      val result: Either[UpstreamErrorResponse, Option[String]] = connector.getBox(testClientId).futureValue

      result shouldBe Right(Some(boxId))
    }

    "return Right(None) when PPNS responds with 404" in new TestSetup {

      val httpResponse: HttpResponse = HttpResponse(404, "")

      when(mockRequestBuilder.execute[HttpResponse](any(), any())).thenReturn(Future.successful(httpResponse))
      val result: Either[UpstreamErrorResponse, Option[String]] = connector.getBox(testClientId).futureValue

      result shouldBe Right(None)
    }

    "return Left(UpstreamErrorResponse) when PPNS responds with unexpected status" in new TestSetup {

      val httpResponse: HttpResponse = HttpResponse(500, "Internal Server Error")

      when(mockRequestBuilder.execute[HttpResponse](any(), any())).thenReturn(Future.successful(httpResponse))
      val result: Either[UpstreamErrorResponse, Option[String]] = connector.getBox(testClientId).futureValue

      result shouldBe Left(UpstreamErrorResponse("Unexpected status from PPNS: 500", 500))
    }

  }

  "PPNSConnector.sendNotification" should {

    "return Unit when the POST notification is successful" in new TestSetup {
      val httpResponse: HttpResponse = HttpResponse(201, "")
      when(mockRequestBuilder.execute[HttpResponse](any(), any())).thenReturn(Future.successful(httpResponse))
      connector.sendNotification(testBoxId, returnSummaryResults).futureValue shouldBe ()
    }
  }
}
