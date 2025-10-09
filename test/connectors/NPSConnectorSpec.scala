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
import play.api.test.Helpers._
import uk.gov.hmrc.disareturns.connectors.NPSConnector
import uk.gov.hmrc.disareturns.models.submission.isaAccounts.IsaAccount
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class NPSConnectorSpec extends BaseUnitSpec {

  trait TestSetup {

    val connector  = new NPSConnector(mockHttpClient, mockAppConfig)
    val testIsaRef = "Z1234"
    val testUrl    = "http://localhost:1204"
    val testSubscriptions: Seq[IsaAccount] = Seq.empty

    implicit val hc: HeaderCarrier = HeaderCarrier()

    when(mockAppConfig.npsBaseUrl).thenReturn(testUrl)
    when(mockHttpClient.post(url"$testUrl/nps/declaration/$testIsaRef")).thenReturn(mockRequestBuilder)
  }

  "NPSConnector.notify" should {

    "return Right(HttpResponse) when the POST is successful" in new TestSetup {
      val httpResponse: HttpResponse = HttpResponse(204, "")

      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.successful(Right(httpResponse)))

      val result: Either[UpstreamErrorResponse, HttpResponse] = connector.sendNotification(testIsaRef).value.futureValue

      result shouldBe Right(httpResponse)
    }

    "return Left(UpstreamErrorResponse) when the POST fails" in new TestSetup {
      val error: UpstreamErrorResponse = UpstreamErrorResponse("Forbidden", 403)

      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.successful(Left(error)))

      val result: Either[UpstreamErrorResponse, HttpResponse] = connector.sendNotification(testIsaRef).value.futureValue

      result shouldBe Left(error)
    }

    "return Left(UpstreamErrorResponse) when an unexpected exception occurs" in new TestSetup {
      val exception = new RuntimeException("Connection timeout")

      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.failed(exception))

      val result: Either[UpstreamErrorResponse, HttpResponse] = connector.sendNotification(testIsaRef).value.futureValue

      result match {
        case Left(err) =>
          err.statusCode shouldBe INTERNAL_SERVER_ERROR
          err.message      should include("Unexpected error: Connection timeout")
        case _ => fail("Expected Left(UpstreamErrorResponse)")
      }
    }
  }

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
}
