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
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.connectors.{ETMPConnector, PPNSConnector}
import uk.gov.hmrc.disareturns.connectors.response.{EtmpObligations, EtmpReportingWindow}
import uk.gov.hmrc.disareturns.models.response.ppns.{Box, BoxCreator, Subscriber}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{StringContextOps, UpstreamErrorResponse}
import utils.BaseUnitSpec

import java.time.Instant
import scala.concurrent.Future

class PPNSConnectorSpec extends BaseUnitSpec {

  "PPNSConnector.getBoxId" should {

    "return Right(Box) when call to PPNS returns a Box successfully" in new TestSetup {
      val expectedResponse: Box = Box(
        boxId = "boxId1" ,
        boxName = "Test_Box",
        boxCreator =
          BoxCreator(
            clientId = testClientId),
        applicationId = Some("applicationId"),
        subscriber = None)

      when(mockRequestBuilder.execute[Box](any(), any()))
        .thenReturn(Future.successful(expectedResponse))

      val connector: PPNSConnector = new PPNSConnector(mockHttpClient)

      val result: Either[UpstreamErrorResponse, Box] = connector.getBoxId("123456").futureValue

      result shouldBe Right(expectedResponse)
    }

    "return Left(UpstreamErrorResponse) when the call to PPNS fails with an UpstreamErrorResponse" in new TestSetup {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
        headers = Map.empty
      )
      when(mockRequestBuilder.execute[EtmpObligations](any(), any()))
        .thenReturn(Future.failed(exception))

      val connector: ETMPConnector = new ETMPConnector(mockHttpClient, mockAppConfig)

      val result: Either[UpstreamErrorResponse, EtmpObligations] = connector.checkReturnsObligationStatus("123456").futureValue

      result shouldBe Left(exception)
    }
  }

  trait TestSetup {
    val endpointUrl: String = ""
    val testClientId = "test-client-id-12345"
    val mockHttpClient: HttpClientV2 = mock[HttpClientV2]
    val mockAppConfig: AppConfig = mock[AppConfig]
    val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]
    val testUrl: String = "http://localhost:1204"
    when(mockAppConfig.etmpBaseUrl).thenReturn(testUrl)
    when(mockHttpClient.get(url"$testUrl/box?clientId=$testClientId"))
      .thenReturn(mockRequestBuilder)
    when(mockHttpClient.get(url"$testUrl/disa-returns-stubs/etmp/check-reporting-window"))
      .thenReturn(mockRequestBuilder)
  }
}
