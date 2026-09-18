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
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.disareturns.connectors.NPSConnector
import uk.gov.hmrc.disareturns.models.returnResults.{IssueWithMessage, ReconciliationReportResponse, ReturnResults}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class NPSConnectorSpec extends BaseUnitSpec {

  trait TestSetup {

    val connector = new NPSConnector(mockHttpClient, mockAppConfig, retryConfig, actorSystem)
    val testUrl   = "http://localhost:1204"

    implicit val hc: HeaderCarrier = HeaderCarrier()

    when(mockAppConfig.npsBaseUrl).thenReturn(testUrl)
    when(mockHttpClient.get(url"$testUrl/monthly/$validZReference/$validTaxYear/$validMonthStr/results")).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.transform(any())).thenReturn(mockRequestBuilder)
  }

  "NPSConnector.retrieveReconciliationReport" should {

    "return Right(HttpResponse) when the GET is successful" in new TestSetup {
      val httpResponse: HttpResponse =
        HttpResponse(
          200,
          Json.toJson(ReconciliationReportResponse(Seq(ReturnResults("2", "A", IssueWithMessage("code", "message"))), None)).toString
        )

      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.successful(Right(httpResponse)))

      val result: Either[UpstreamErrorResponse, HttpResponse] =
        connector.retrieveReconciliationReport(validZReference, validTaxYear, validMonth, Some("raw-cursor"), 200).value.futureValue

      result shouldBe Right(httpResponse)
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
        connector.retrieveReconciliationReport(validZReference, validTaxYear, validMonth, None, 200).value.futureValue

      result shouldBe Left(upstreamError)
    }

    "return Left(UpstreamErrorResponse) when an unexpected Throwable occurs" in new TestSetup {
      val runtimeException = new RuntimeException("Connection timeout")

      when(mockRequestBuilder.execute[Either[UpstreamErrorResponse, HttpResponse]](any(), any()))
        .thenReturn(Future.failed(runtimeException))

      val result =
        connector.retrieveReconciliationReport(validZReference, validTaxYear, validMonth, None, 200).value.futureValue.left.value

      result.statusCode shouldBe 500
      result.message      should include("Unexpected error: Connection timeout")
    }
  }
}
