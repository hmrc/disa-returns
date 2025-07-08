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
import uk.gov.hmrc.disareturns.connectors.ETMPConnector
import uk.gov.hmrc.disareturns.connectors.response.{EtmpObligations, EtmpReportingWindow}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{StringContextOps, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class ETMPConnectorSpec extends BaseUnitSpec {

  "EtmpConnector.checkReturnsObligationStatus" should {

    "return Right(EtmpObligations) when call to ETMP returns an obligation status successfully" in new TestSetup {
      val expectedResponse: EtmpObligations = EtmpObligations(true)

      when(mockRequestBuilder.execute[EtmpObligations](any(), any()))
        .thenReturn(Future.successful(expectedResponse))

      val connector: ETMPConnector = new ETMPConnector(mockHttpClient, mockAppConfig)

      val result: Either[UpstreamErrorResponse, EtmpObligations] = connector.checkReturnsObligationStatus("123456").futureValue

      result shouldBe Right(expectedResponse)
    }

    "return Left(UpstreamErrorResponse) when the call to ETMP fails with an UpstreamErrorResponse" in new TestSetup {
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

    "return Left(UpstreamErrorResponse) when the call to ETMP fails with an unexpected Throwable exception" in new TestSetup {
      val runtimeException = new RuntimeException("Connection timeout")

      // Simulate a non-UpstreamErrorResponse exception
      when(mockRequestBuilder.execute[EtmpObligations](any(), any()))
        .thenReturn(Future.failed(runtimeException))

      val connector: ETMPConnector = new ETMPConnector(mockHttpClient, mockAppConfig)

      val result: Either[UpstreamErrorResponse, EtmpObligations] =
        connector.checkReturnsObligationStatus("123456").futureValue

      result.isLeft shouldBe true
      result.left.get.statusCode shouldBe 500
      result.left.get.message should include("Unexpected error: Connection timeout")
    }
  }

  "EtmpConnector.checkReportingWindowStatus" should {

    "return Right(EtmpReportingWindow) when call to ETMP returns an obligation status successfully" in new TestSetup {
      val expectedResponse: EtmpReportingWindow = EtmpReportingWindow(true)

      when(mockRequestBuilder.execute[EtmpReportingWindow](any(), any()))
        .thenReturn(Future.successful(expectedResponse))

      val connector: ETMPConnector = new ETMPConnector(mockHttpClient, mockAppConfig)

      val result: Either[UpstreamErrorResponse, EtmpReportingWindow] = connector.checkReportingWindowStatus.futureValue

      result shouldBe Right(expectedResponse)
    }

    "return Left(UpstreamErrorResponse) when the call to ETMP fails with an UpstreamErrorResponse" in new TestSetup {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
        headers = Map.empty
      )
      when(mockRequestBuilder.execute[EtmpReportingWindow](any(), any()))
        .thenReturn(Future.failed(exception))

      val connector: ETMPConnector = new ETMPConnector(mockHttpClient, mockAppConfig)

      val result: Either[UpstreamErrorResponse, EtmpReportingWindow] = connector.checkReportingWindowStatus.futureValue

      result shouldBe Left(exception)
    }

    "return Left(UpstreamErrorResponse) when the call to ETMP fails with an unexpected Throwable exception" in new TestSetup {
      val runtimeException = new RuntimeException("Connection timeout")

      // Simulate a non-UpstreamErrorResponse exception
      when(mockRequestBuilder.execute[EtmpObligations](any(), any()))
        .thenReturn(Future.failed(runtimeException))

      val connector: ETMPConnector = new ETMPConnector(mockHttpClient, mockAppConfig)

      val result: Either[UpstreamErrorResponse, EtmpObligations] =
        connector.checkReturnsObligationStatus("123456").futureValue

      result.isLeft shouldBe true
      result.left.get.statusCode shouldBe 500
      result.left.get.message should include("Unexpected error: Connection timeout")
    }
  }

  trait TestSetup {
    val endpointUrl: String = ""
    val mockHttpClient: HttpClientV2 = mock[HttpClientV2]
    val mockAppConfig: AppConfig = mock[AppConfig]
    val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]
    val testUrl: String = "http://localhost:1204"
    when(mockAppConfig.etmpBaseUrl).thenReturn(testUrl)
    when(mockHttpClient.get(url"$testUrl/disa-returns-stubs/etmp/check-obligation-status/123456"))
      .thenReturn(mockRequestBuilder)
    when(mockHttpClient.get(url"$testUrl/disa-returns-stubs/etmp/check-reporting-window"))
      .thenReturn(mockRequestBuilder)
  }
}
