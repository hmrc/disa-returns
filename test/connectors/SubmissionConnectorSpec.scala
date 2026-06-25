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
import uk.gov.hmrc.disareturns.connectors.SubmissionConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class SubmissionConnectorSpec extends BaseUnitSpec {

  trait TestSetup {

    val connector         = new SubmissionConnector(mockHttpClient, mockAppConfig)
    val nilReturnReported = false
    val testUrl           = "http://localhost:12103"
    val monthInt          = validMonth.id + 1

    implicit val hc: HeaderCarrier = HeaderCarrier()

    when(mockAppConfig.submissionBaseUrl).thenReturn(testUrl)
    when(mockHttpClient.post(url"$testUrl/disa-returns-submission/monthly/$validZReference/$validTaxYear/$monthInt/declarations"))
      .thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.withBody(any())(any, any, any)).thenReturn(mockRequestBuilder)
  }

  "SubmissionConnector.sendDeclaration" should {

    "return Right(HttpResponse) when the POST is successful" in new TestSetup {
      val httpResponse: HttpResponse = HttpResponse(200, "")

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(httpResponse))

      val result: Either[UpstreamErrorResponse, HttpResponse] =
        connector.sendDeclaration(validZReference, validTaxYear, validMonth, nilReturnReported).value.futureValue

      result shouldBe Right(httpResponse)
    }

    "return Left(UpstreamErrorResponse) with raw body when the POST returns a 422" in new TestSetup {
      val body = """{"code":"NO_SUBMISSION_DATA","error":"Cannot declare with nilReturn as false when no monthly return data has been submitted"}"""
      val httpResponse = HttpResponse(422, body)

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(httpResponse))

      val result: Either[UpstreamErrorResponse, HttpResponse] =
        connector.sendDeclaration(validZReference, validTaxYear, validMonth, nilReturnReported).value.futureValue

      result match {
        case Left(err) =>
          err.statusCode shouldBe 422
          err.message    shouldBe body
        case _ => fail("Expected Left(UpstreamErrorResponse)")
      }
    }

    "return Left(UpstreamErrorResponse) when the POST returns a 500" in new TestSetup {
      val httpResponse = HttpResponse(500, "Internal Server Error")

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(httpResponse))

      val result: Either[UpstreamErrorResponse, HttpResponse] =
        connector.sendDeclaration(validZReference, validTaxYear, validMonth, nilReturnReported).value.futureValue

      result match {
        case Left(err) =>
          err.statusCode shouldBe 500
          err.message    shouldBe "Internal Server Error"
        case _ => fail("Expected Left(UpstreamErrorResponse)")
      }
    }

    "return Left(UpstreamErrorResponse) when an unexpected exception occurs" in new TestSetup {
      val exception = new RuntimeException("Connection timeout")

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.failed(exception))

      val result: Either[UpstreamErrorResponse, HttpResponse] =
        connector.sendDeclaration(validZReference, validTaxYear, validMonth, nilReturnReported).value.futureValue

      result match {
        case Left(err) =>
          err.statusCode shouldBe INTERNAL_SERVER_ERROR
          err.message      should include("Unexpected error: Connection timeout")
        case _ => fail("Expected Left(UpstreamErrorResponse)")
      }
    }
  }
}
