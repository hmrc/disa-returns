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
import uk.gov.hmrc.http.{HttpResponse, StringContextOps, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class ETMPConnectorSpec extends BaseUnitSpec {

  "EtmpConnector.sendDeclaration" should {

    "return Right(HttpResponse) when the call to ETMP is successful" in new TestSetup {
      val mockHttpResponse: HttpResponse = HttpResponse(status = 200, "")

      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.successful(Right(mockHttpResponse)))

      val result: Either[UpstreamErrorResponse, HttpResponse] = connector.sendDeclaration(testZReference).value.futureValue

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

      val result: Either[UpstreamErrorResponse, HttpResponse] = connector.sendDeclaration(testZReference).value.futureValue

      result shouldBe Left(upstreamErrorResponse)
    }

    "return Left(UpstreamErrorResponse) when the call to ETMP fails with an unexpected Throwable exception" in new TestSetup {
      val runtimeException = new RuntimeException("Connection timeout")

      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.failed(runtimeException))

      val result = connector.sendDeclaration(testZReference).value.futureValue.left.value

      result.statusCode shouldBe 500
      result.message      should include("Unexpected error: Connection timeout")
    }
  }

  trait TestSetup {
    val testZReference: String        = "123456"
    val connector:      ETMPConnector = new ETMPConnector(mockHttpClient, mockAppConfig, retryConfig, actorSystem)
    val testUrl:        String        = "http://localhost:1204"
    when(mockAppConfig.etmpBaseUrl).thenReturn(testUrl)
    when(mockHttpClient.post(url"$testUrl/etmp/declaration/$testZReference"))
      .thenReturn(mockRequestBuilder)
  }
}
