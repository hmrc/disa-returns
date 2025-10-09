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

package connectors

import cats.data.EitherT
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.Json
import uk.gov.hmrc.disareturns.connectors.NPSConnector
import uk.gov.hmrc.disareturns.models.submission.isaAccounts.IsaAccount
import uk.gov.hmrc.http.{HttpResponse, StringContextOps, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class NPSConnectorSpec extends BaseUnitSpec {

  "NPSConnector.submit" should {

    "return Right(HttpResponse) when the NPS submit call succeeds" in new TestSetup {
      val mockHttpResponse: HttpResponse =
        HttpResponse(status = 204, json = Json.toJson(testSubscriptions), headers = Map.empty)

      when(mockBaseConnector.read(any(), any()))
        .thenAnswer { invocation =>
          val fut =
            invocation.getArgument[Future[Either[UpstreamErrorResponse, HttpResponse]]](
              0,
              classOf[Future[Either[UpstreamErrorResponse, HttpResponse]]]
            )
          EitherT(fut)
        }

      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.successful(Right(mockHttpResponse)))

      val result: Either[UpstreamErrorResponse, HttpResponse] =
        connector.submit(validZRef, testSubscriptions).value.futureValue

      result shouldBe Right(mockHttpResponse)
    }

    "return Left(UpstreamErrorResponse) when NPS returns an upstream error" in new TestSetup {
      val upstreamError: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
        headers = Map.empty
      )

      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.successful(Left(upstreamError)))

      val result: Either[UpstreamErrorResponse, HttpResponse] =
        connector.submit(validZRef, testSubscriptions).value.futureValue

      result shouldBe Left(upstreamError)
    }

    "return Left(UpstreamErrorResponse) when an unexpected Throwable occurs" in new TestSetup {
      val runtimeException = new RuntimeException("Connection timeout")

      when(mockBaseConnector.read(any(), any()))
        .thenAnswer { invocation =>
          val fut =
            invocation.getArgument[Future[Either[UpstreamErrorResponse, HttpResponse]]](
              0,
              classOf[Future[Either[UpstreamErrorResponse, HttpResponse]]]
            )
          EitherT(
            fut.recover { case e =>
              Left(UpstreamErrorResponse(s"Unexpected error: ${e.getMessage}", 500, 500))
            }
          )
        }

      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.failed(runtimeException))

      val Left(result): Either[UpstreamErrorResponse, HttpResponse] =
        connector.submit(validZRef, testSubscriptions).value.futureValue

      result.statusCode shouldBe 500
      result.message      should include("Unexpected error: Connection timeout")
    }
  }

  trait TestSetup {
    val testUrl:           String          = "http://localhost:1204"
    val testSubscriptions: Seq[IsaAccount] = Seq.empty

    val connector: NPSConnector = new NPSConnector(mockHttpClient, mockAppConfig)

    when(mockAppConfig.npsBaseUrl).thenReturn(testUrl)
    when(mockHttpClient.post(url"$testUrl/nps/submit/$validZRef"))
      .thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.withBody(any())(any, any, any))
      .thenReturn(mockRequestBuilder)
  }
}
