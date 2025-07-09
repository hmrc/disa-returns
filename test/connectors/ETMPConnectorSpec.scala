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
import uk.gov.hmrc.disareturns.connectors.ETMPConnector
import uk.gov.hmrc.disareturns.connectors.response.{EtmpObligations, EtmpReportingWindow}
import uk.gov.hmrc.http.{StringContextOps, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class ETMPConnectorSpec extends BaseUnitSpec {

  "EtmpConnector.checkReturnsObligationStatus" should {

    "return Right(EtmpObligations) when call to ETMP returns an obligation status successfully" in new TestSetup {
      val expectedResponse: EtmpObligations = EtmpObligations(true)

      when(mockRequestBuilder.execute[EtmpObligations](any(), any()))
        .thenReturn(Future.successful(expectedResponse))

      val result: Either[UpstreamErrorResponse, EtmpObligations] = connector.checkReturnsObligationStatus(testIsaManagerReferenceNumber).futureValue

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

      val result: Either[UpstreamErrorResponse, EtmpObligations] = connector.checkReturnsObligationStatus(testIsaManagerReferenceNumber).futureValue

      result shouldBe Left(exception)
    }

    "return Left(UpstreamErrorResponse) when the call to ETMP fails with an unexpected Throwable exception" in new TestSetup {
      val runtimeException = new RuntimeException("Connection timeout")

      // Simulate a non-UpstreamErrorResponse exception
      when(mockRequestBuilder.execute[EtmpObligations](any(), any()))
        .thenReturn(Future.failed(runtimeException))

      val result: Either[UpstreamErrorResponse, EtmpObligations] =
        connector.checkReturnsObligationStatus("123456").futureValue

      result match {
        case Left(error) =>
          error.statusCode shouldBe 500
          error.message      should include("Unexpected error: Connection timeout")
        case Right(_) =>
          fail("Expected a Left, but got a Right")
      }
    }
  }

  "EtmpConnector.checkReportingWindowStatus" should {

    "return Right(EtmpReportingWindow) when call to ETMP returns an obligation status successfully" in new TestSetup {
      val expectedResponse: EtmpReportingWindow = EtmpReportingWindow(true)

      when(mockRequestBuilder.execute[EtmpReportingWindow](any(), any()))
        .thenReturn(Future.successful(expectedResponse))

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

      val result: Either[UpstreamErrorResponse, EtmpReportingWindow] = connector.checkReportingWindowStatus.futureValue

      result shouldBe Left(exception)
    }

    "return Left(UpstreamErrorResponse) when the call to ETMP fails with an unexpected Throwable exception" in new TestSetup {
      val runtimeException = new RuntimeException("Connection timeout")

      when(mockRequestBuilder.execute[EtmpObligations](any(), any()))
        .thenReturn(Future.failed(runtimeException))

      val result: Either[UpstreamErrorResponse, EtmpReportingWindow] =
        connector.checkReportingWindowStatus.futureValue

      result match {
        case Left(error) =>
          error.statusCode shouldBe 500
          error.message      should include("Unexpected error: Connection timeout")
        case Right(_) =>
          fail("Expected a Left, but got a Right")
      }
    }
  }

  trait TestSetup {
    val testIsaManagerReferenceNumber: String        = "123456"
    val connector:                     ETMPConnector = new ETMPConnector(mockHttpClient, mockAppConfig)
    val testUrl:                       String        = "http://localhost:1204"
    when(mockAppConfig.etmpBaseUrl).thenReturn(testUrl)
    when(mockHttpClient.get(url"$testUrl/disa-returns-stubs/etmp/check-obligation-status/$testIsaManagerReferenceNumber"))
      .thenReturn(mockRequestBuilder)
    when(mockHttpClient.get(url"$testUrl/disa-returns-stubs/etmp/check-reporting-window"))
      .thenReturn(mockRequestBuilder)
  }
}
