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

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.test.Helpers._
import uk.gov.hmrc.disareturns.connectors.SubmissionConnector
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class SubmissionConnectorSpec extends BaseUnitSpec {

  trait TestSetup {

    reset(mockHttpClient, mockAppConfig, mockRequestBuilder)

    val connector         = new SubmissionConnector(mockHttpClient, mockAppConfig)
    val nilReturnReported = false
    val testUrl           = "http://localhost:12103"
    val monthInt          = validMonth.id
    val internalAuthToken = "valid-internal-auth-token-disa-returns"

    implicit val hc: HeaderCarrier = HeaderCarrier()

    when(mockAppConfig.submissionBaseUrl).thenReturn(testUrl)
    when(mockAppConfig.internalAuthToken).thenReturn(internalAuthToken)
    when(mockHttpClient.post(url"$testUrl/disa-returns-submission/monthly/$validZReference/$validTaxYear/$monthInt/declarations"))
      .thenReturn(mockRequestBuilder)
    when(mockHttpClient.post(url"$testUrl/disa-returns-submission/monthly/$validZReference/$validTaxYear/$monthInt"))
      .thenReturn(mockRequestBuilder)
    when(mockHttpClient.post(url"$testUrl/disa-returns-submission/monthly/$validZReference/$validTaxYear/$monthInt/submissions"))
      .thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.setHeader("Authorization" -> internalAuthToken)).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.withBody(any())(any, any, any)).thenReturn(mockRequestBuilder)
  }

  "SubmissionConnector.sendDeclaration" should {

    "return Right(HttpResponse) when the POST is successful" in new TestSetup {
      val httpResponse: HttpResponse = HttpResponse(OK, "")

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(httpResponse))

      val result: Either[UpstreamErrorResponse, HttpResponse] =
        connector.sendDeclaration(validZReference, validTaxYear, validMonth, nilReturnReported).value.futureValue

      result                                         shouldBe Right(httpResponse)
      verify(mockRequestBuilder).setHeader("Authorization" -> internalAuthToken)
    }

    "return Left(UpstreamErrorResponse) with raw body when the POST returns a 422" in new TestSetup {
      val body = """{"code":"NO_SUBMISSION_DATA","error":"Cannot declare with nilReturn as false when no monthly return data has been submitted"}"""
      val httpResponse = HttpResponse(UNPROCESSABLE_ENTITY, body)

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(httpResponse))

      val result: Either[UpstreamErrorResponse, HttpResponse] =
        connector.sendDeclaration(validZReference, validTaxYear, validMonth, nilReturnReported).value.futureValue

      result match {
        case Left(err) =>
          err.statusCode shouldBe UNPROCESSABLE_ENTITY
          err.message    shouldBe body
        case _ => fail("Expected Left(UpstreamErrorResponse)")
      }
    }

    "return Left(UpstreamErrorResponse) when the POST returns a 500" in new TestSetup {
      val httpResponse = HttpResponse(INTERNAL_SERVER_ERROR, "Internal Server Error")

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(httpResponse))

      val result: Either[UpstreamErrorResponse, HttpResponse] =
        connector.sendDeclaration(validZReference, validTaxYear, validMonth, nilReturnReported).value.futureValue

      result match {
        case Left(err) =>
          err.statusCode shouldBe INTERNAL_SERVER_ERROR
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

  "SubmissionConnector.createMonthlyReturn" should {

    "return Right(()) when the POST is successful" in new TestSetup {
      val httpResponse: HttpResponse = HttpResponse(CREATED, "")

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(httpResponse))

      val result: Either[UpstreamErrorResponse, Unit] =
        connector.createMonthlyReturn(validZReference, validTaxYear, validMonth, nilReturn = false).futureValue

      result                                         shouldBe Right(())
      verify(mockRequestBuilder).setHeader("Authorization" -> internalAuthToken)
    }

    "return Left(UpstreamErrorResponse) when the POST returns a 409" in new TestSetup {
      val body         = """{"submissionId":"existing-id"}"""
      val httpResponse = HttpResponse(CONFLICT, body)

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(httpResponse))

      val result: Either[UpstreamErrorResponse, Unit] =
        connector.createMonthlyReturn(validZReference, validTaxYear, validMonth, nilReturn = false).futureValue

      result match {
        case Left(err) =>
          err.statusCode shouldBe CONFLICT
          err.message    shouldBe body
        case _ => fail("Expected Left(UpstreamErrorResponse)")
      }
    }

    "return Left(UpstreamErrorResponse) when the POST returns a 500" in new TestSetup {
      val httpResponse = HttpResponse(INTERNAL_SERVER_ERROR, "Internal Server Error")

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(httpResponse))

      val result: Either[UpstreamErrorResponse, Unit] =
        connector.createMonthlyReturn(validZReference, validTaxYear, validMonth, nilReturn = false).futureValue

      result match {
        case Left(err) =>
          err.statusCode shouldBe INTERNAL_SERVER_ERROR
          err.message    shouldBe "Internal Server Error"
        case _ => fail("Expected Left(UpstreamErrorResponse)")
      }
    }

    "return Left(UpstreamErrorResponse) when an unexpected exception occurs" in new TestSetup {
      val exception = new RuntimeException("Connection timeout")

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.failed(exception))

      val result: Either[UpstreamErrorResponse, Unit] =
        connector.createMonthlyReturn(validZReference, validTaxYear, validMonth, nilReturn = false).futureValue

      result match {
        case Left(err) =>
          err.statusCode shouldBe INTERNAL_SERVER_ERROR
          err.message      should include("Unexpected error: Connection timeout")
        case _ => fail("Expected Left(UpstreamErrorResponse)")
      }
    }
  }

  "SubmissionConnector.sendSubmission" should {

    val ndjsonSource: Source[ByteString, _] = Source.single(ByteString("""{"nino":"AB000001C"}"""))

    "return Right(()) when the POST is successful" in new TestSetup {
      val httpResponse: HttpResponse = HttpResponse(OK, "")

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(httpResponse))

      val result: Either[UpstreamErrorResponse, Unit] =
        connector.sendSubmission(validZReference, validTaxYear, validMonth, ndjsonSource).futureValue

      result                                         shouldBe Right(())
      verify(mockRequestBuilder).setHeader("Authorization" -> internalAuthToken)
    }

    "return Left(UpstreamErrorResponse) when the POST returns a 400" in new TestSetup {
      val body         = """{"code":"VALIDATION_FAILURE","message":"Bad request"}"""
      val httpResponse = HttpResponse(BAD_REQUEST, body)

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(httpResponse))

      val result: Either[UpstreamErrorResponse, Unit] =
        connector.sendSubmission(validZReference, validTaxYear, validMonth, ndjsonSource).futureValue

      result match {
        case Left(err) =>
          err.statusCode shouldBe BAD_REQUEST
          err.message    shouldBe body
        case _ => fail("Expected Left(UpstreamErrorResponse)")
      }
    }

    "return Left(UpstreamErrorResponse) when the POST returns a 500" in new TestSetup {
      val httpResponse = HttpResponse(INTERNAL_SERVER_ERROR, "Internal Server Error")

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(httpResponse))

      val result: Either[UpstreamErrorResponse, Unit] =
        connector.sendSubmission(validZReference, validTaxYear, validMonth, ndjsonSource).futureValue

      result match {
        case Left(err) =>
          err.statusCode shouldBe INTERNAL_SERVER_ERROR
          err.message    shouldBe "Internal Server Error"
        case _ => fail("Expected Left(UpstreamErrorResponse)")
      }
    }

    "return Left(UpstreamErrorResponse) when an unexpected exception occurs" in new TestSetup {
      val exception = new RuntimeException("Connection timeout")

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.failed(exception))

      val result: Either[UpstreamErrorResponse, Unit] =
        connector.sendSubmission(validZReference, validTaxYear, validMonth, ndjsonSource).futureValue

      result match {
        case Left(err) =>
          err.statusCode shouldBe INTERNAL_SERVER_ERROR
          err.message      should include("Unexpected error: Connection timeout")
        case _ => fail("Expected Left(UpstreamErrorResponse)")
      }
    }
  }
}
