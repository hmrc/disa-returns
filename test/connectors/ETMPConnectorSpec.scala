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

import cats.data.EitherT
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.Json
import uk.gov.hmrc.disareturns.connectors.ETMPConnector
import uk.gov.hmrc.disareturns.connectors.response.{EtmpObligations, EtmpReportingWindow}
import uk.gov.hmrc.http.{HttpResponse, StringContextOps, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class ETMPConnectorSpec extends BaseUnitSpec {

  "EtmpConnector.checkReturnsObligationStatus" should {

    "return Right(EtmpObligations) when call to ETMP returns an obligation status successfully" in new TestSetup {
      val expectedResponse: EtmpObligations = EtmpObligations(true)
      val mockHttpResponse: HttpResponse = HttpResponse(
        status = 200,
        json = Json.toJson(expectedResponse),
        headers = Map.empty
      )

      when(mockHttpClientResponse.read(any(), any()))
        .thenAnswer { invocation =>
          val future = invocation
            .getArgument[Future[Either[UpstreamErrorResponse, HttpResponse]]](0, classOf[Future[Either[UpstreamErrorResponse, HttpResponse]]])
          EitherT(future)
        }

      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.successful(Right(mockHttpResponse)))

      val result: Either[UpstreamErrorResponse, HttpResponse] = connector.getReturnsObligationStatus(testIsaManagerReferenceNumber).value.futureValue

      result shouldBe Right(mockHttpResponse)
    }

    "return Left(UpstreamErrorResponse) when the call to ETMP fails with an UpstreamErrorResponse" in new TestSetup {
      val upstreamErrorResponse: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
        headers = Map.empty
      )
      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.successful(Left(upstreamErrorResponse)))

      val result: Either[UpstreamErrorResponse, HttpResponse] = connector.getReturnsObligationStatus(testIsaManagerReferenceNumber).value.futureValue

      result shouldBe Left(upstreamErrorResponse)
    }

    "return Left(UpstreamErrorResponse) when the call to ETMP fails with an unexpected Throwable exception" in new TestSetup {
      val runtimeException = new RuntimeException("Connection timeout")

      when(mockHttpClientResponse.read(any(), any()))
        .thenAnswer { invocation =>
          val future = invocation
            .getArgument[Future[Either[UpstreamErrorResponse, HttpResponse]]](0, classOf[Future[Either[UpstreamErrorResponse, HttpResponse]]])
          // Wrap with recover so that failures in Future are converted to Left
          EitherT(
            future.recover { case e =>
              Left(UpstreamErrorResponse(s"Unexpected error: ${e.getMessage}", 500, 500))
            }
          )
        }
      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.failed(runtimeException))

      val Left(result): Either[UpstreamErrorResponse, HttpResponse] =
        connector.getReturnsObligationStatus("123456").value.futureValue

      result.statusCode shouldBe 500
      result.message      should include("Unexpected error: Connection timeout")
    }
  }

  "EtmpConnector.checkReportingWindowStatus" should {

    "return Right(EtmpReportingWindow) when call to ETMP returns an obligation status successfully" in new TestSetup {
      val expectedResponse: EtmpReportingWindow = EtmpReportingWindow(true)
      val mockHttpResponse: HttpResponse        = HttpResponse(status = 200, json = Json.toJson(expectedResponse), headers = Map.empty)

      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.successful(Right(mockHttpResponse)))

      val result: Either[UpstreamErrorResponse, HttpResponse] = connector.getReportingWindowStatus.value.futureValue

      result shouldBe Right(mockHttpResponse)
    }

    "return Left(UpstreamErrorResponse) when the call to ETMP fails with an UpstreamErrorResponse" in new TestSetup {
      val upstreamErrorResponse: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
        headers = Map.empty
      )
      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.successful(Left(upstreamErrorResponse)))

      val result: Either[UpstreamErrorResponse, HttpResponse] = connector.getReportingWindowStatus.value.futureValue

      result shouldBe Left(upstreamErrorResponse)
    }

    "return Left(UpstreamErrorResponse) when the call to ETMP fails with an unexpected Throwable exception" in new TestSetup {
      val runtimeException = new RuntimeException("Connection timeout")

      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.failed(runtimeException))

      val Left(result): Either[UpstreamErrorResponse, HttpResponse] =
        connector.getReportingWindowStatus.value.futureValue

      result.statusCode shouldBe 500
      result.message      should include("Unexpected error: Connection timeout")
    }
  }

  trait TestSetup {
    val testIsaManagerReferenceNumber: String        = "123456"
    val connector:                     ETMPConnector = new ETMPConnector(mockHttpClient, mockAppConfig, mockHttpClientResponse)
    val testUrl:                       String        = "http://localhost:1204"
    when(mockAppConfig.etmpBaseUrl).thenReturn(testUrl)
    when(mockHttpClient.get(url"$testUrl/etmp/check-obligation-status/$testIsaManagerReferenceNumber"))
      .thenReturn(mockRequestBuilder)
    when(mockHttpClient.get(url"$testUrl/etmp/check-reporting-window"))
      .thenReturn(mockRequestBuilder)
  }
}
