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

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.test.Helpers._
import uk.gov.hmrc.disareturns.connectors.NPSConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class NPSConnectorSpec extends BaseUnitSpec {

  trait TestSetup {

    val connector  = new NPSConnector(mockHttpClient, mockAppConfig)
    val testIsaRef = "Z1234"
    val testUrl    = "http://localhost:1204"

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
}
