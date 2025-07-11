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
import uk.gov.hmrc.disareturns.connectors.PPNSConnector
import uk.gov.hmrc.disareturns.models.response.ppns.{Box, BoxCreator}
import uk.gov.hmrc.http.{HttpResponse, StringContextOps, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class PPNSConnectorSpec extends BaseUnitSpec {

  "PPNSConnector.getBoxId" should {

    "return Right(Box) when call to PPNS returns a Box successfully" in new TestSetup {
      val expectedResponse: Box = Box(
        boxId = "boxId1",
        boxName = "Test_Box",
        boxCreator = BoxCreator(clientId = testClientId),
        applicationId = Some("applicationId"),
        subscriber = None
      )
      val httpResponse: HttpResponse = HttpResponse(200, Json.toJson(expectedResponse).toString())

      when(mockHttpClientResponse.read(any()))
        .thenAnswer { invocation =>
          val future = invocation.getArgument[Future[Either[UpstreamErrorResponse, HttpResponse]]](0, classOf[Future[Either[UpstreamErrorResponse, HttpResponse]]])
          EitherT(future)
        }

      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.successful(Right(httpResponse)))

      val result: Either[UpstreamErrorResponse, HttpResponse] = connector.getBox(testClientId).value.futureValue

      result shouldBe Right(httpResponse)
    }

    "return Left(UpstreamErrorResponse) when the call to PPNS fails with an UpstreamErrorResponse" in new TestSetup {
      val upstreamErrorResponse: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
        headers = Map.empty
      )

      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.successful(Left(upstreamErrorResponse)))

      val result: Either[UpstreamErrorResponse, HttpResponse] = connector.getBox(testClientId).value.futureValue

      result shouldBe Left(upstreamErrorResponse)
    }

    "return Left(UpstreamErrorResponse) when the call to PPNS fails with an unexpected Throwable exception" in new TestSetup {
      val runtimeException = new RuntimeException("Connection timeout")

      when(mockHttpClientResponse.read(any()))
        .thenAnswer { invocation =>
          val future = invocation.getArgument[Future[Either[UpstreamErrorResponse, HttpResponse]]](0, classOf[Future[Either[UpstreamErrorResponse, HttpResponse]]])
          EitherT(
            future.recover {
              case e =>
                Left(UpstreamErrorResponse(s"Unexpected error: ${e.getMessage}", 500, 500))
            }
          )
        }

      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.failed(runtimeException))

      val result: Either[UpstreamErrorResponse, HttpResponse] = connector.getBox(testClientId).value.futureValue

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
    val connector: PPNSConnector = new PPNSConnector(mockHttpClient, mockAppConfig, mockHttpClientResponse)
    val testClientId = "test-client-id-12345"
    val testUrl: String = "http://localhost:6701"
    when(mockAppConfig.ppnsBaseUrl).thenReturn(testUrl)
    when(mockHttpClient.get(url"$testUrl/box?clientId=$testClientId"))
      .thenReturn(mockRequestBuilder)
  }
}
