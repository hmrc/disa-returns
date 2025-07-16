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

package models

import cats.data.EitherT
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status
import uk.gov.hmrc.disareturns.connectors.HttpClientResponse
import uk.gov.hmrc.http.{HttpException, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class HttpClientResponseSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  val httpClientResponse = new HttpClientResponse()

  "HttpClientResponse.read" should {

    "return Right when Future is successful with status < 400" in {
      val response = Future.successful(Right(HttpResponse(200, "OK")))

      val result = httpClientResponse.read(response)

      whenReady(result.value) {
        case Right(resp) =>
          resp.status shouldBe 200
          resp.body shouldBe "OK"
        case other =>
          fail(s"Expected Right(HttpResponse), but got $other")
      }
    }

    "return Left UpstreamErrorResponse when Future is successful with status >= 400" in {
      val httpResponse = HttpResponse(500, "Internal Server Error")
      val response = Future.successful(Right(httpResponse))

      val result = httpClientResponse.read(response)

      whenReady(result.value) {
        case Left(err) =>
          err.message should include("Received error status 500")
          err.statusCode shouldBe 500
        case _ => fail("Expected Left UpstreamErrorResponse")
      }
    }

    "return Left when Future contains Left error" in {
      val upstreamError = UpstreamErrorResponse("some error", 400, 400)
      val response = Future.successful(Left(upstreamError))

      val result = httpClientResponse.read(response)

      whenReady(result.value) { res =>
        res shouldBe Left(upstreamError)
      }
    }

    "recover and return Left UpstreamErrorResponse when Future fails with HttpException" in {
      val httpException = new HttpException("http exception occurred", Status.INTERNAL_SERVER_ERROR)
      val failedFuture = Future.failed[Either[UpstreamErrorResponse, HttpResponse]](httpException)

      val result = httpClientResponse.read(failedFuture)

      whenReady(result.value) {
        case Left(err) =>
          err.message should include("Unexpected error")
          err.statusCode shouldBe Status.INTERNAL_SERVER_ERROR
        case _ => fail("Expected Left UpstreamErrorResponse")
      }
    }

    "recover and return Left UpstreamErrorResponse when Future fails with generic Exception" in {
      val exception = new RuntimeException("something bad happened")
      val failedFuture = Future.failed[Either[UpstreamErrorResponse, HttpResponse]](exception)

      val result = httpClientResponse.read(failedFuture)

      whenReady(result.value) {
        case Left(err) =>
          err.message should include("Unexpected error")
          err.statusCode shouldBe Status.INTERNAL_SERVER_ERROR
        case _ => fail("Expected Left UpstreamErrorResponse")
      }
    }
  }
}
