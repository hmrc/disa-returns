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

package services

import cats.data.EitherT
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.http.Status._
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.services.SubmissionService
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import utils.BaseUnitSpec

import java.nio.file.Files
import scala.concurrent.Future

class SubmissionServiceSpec extends BaseUnitSpec {

  val service           = new SubmissionService(mockSubmissionConnector)
  val nilReturnReported = false

  "SubmissionService.declare" should {

    "return Right(HttpResponse) when the connector returns a successful response" in {
      val httpResponse = HttpResponse(OK, "")
      when(mockSubmissionConnector.sendDeclaration(validZReference, validTaxYear, validMonth, nilReturnReported))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val result = service.declare(validZReference, validTaxYear, validMonth, nilReturnReported).value.futureValue

      result shouldBe Right(httpResponse)
    }

    "return Left(UnauthorisedErr) when the connector returns a 401 UpstreamErrorResponse" in {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = UNAUTHORIZED,
        reportAs = UNAUTHORIZED,
        headers = Map.empty
      )

      when(mockSubmissionConnector.sendDeclaration(validZReference, validTaxYear, validMonth, nilReturnReported))
        .thenReturn(EitherT.leftT[Future, HttpResponse](exception))

      val result = service.declare(validZReference, validTaxYear, validMonth, nilReturnReported).value.futureValue

      result shouldBe Left(UnauthorisedErr)
    }

    "return Left(InternalServerErr) when the connector returns a 500 UpstreamErrorResponse" in {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Internal server error",
        statusCode = INTERNAL_SERVER_ERROR,
        reportAs = INTERNAL_SERVER_ERROR,
        headers = Map.empty
      )

      when(mockSubmissionConnector.sendDeclaration(validZReference, validTaxYear, validMonth, nilReturnReported))
        .thenReturn(EitherT.leftT[Future, HttpResponse](exception))

      val result = service.declare(validZReference, validTaxYear, validMonth, nilReturnReported).value.futureValue

      result shouldBe Left(InternalServerErr())
    }

    "return Right(HttpResponse) when declaring with nilReturn true" in {
      val httpResponse = HttpResponse(200, "")
      when(mockSubmissionConnector.sendDeclaration(validZReference, validTaxYear, validMonth, true))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val result = service.declare(validZReference, validTaxYear, validMonth, nilReturnReported = true).value.futureValue

      result shouldBe Right(httpResponse)
    }
  }

  "SubmissionService.submitMonthlyReturn" should {

    "return Right(()) when both connector calls return a successful response" in {
      val tempFile = Files.createTempFile("test-monthly-return", ".ndjson")
      Files.writeString(tempFile, """{"nino":"AB000001C","accountNumber":"STD000001"}""" + "\n")

      when(mockSubmissionConnector.createMonthlyReturn(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(())))
      when(mockSubmissionConnector.sendMonthlyReturn(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(())))

      val result = service.submitMonthlyReturn(validZReference, validTaxYear, validMonth, tempFile).futureValue

      Files.deleteIfExists(tempFile)
      result shouldBe Right(())
    }

    "return Right(()) when createMonthlyReturn returns 409 (monthly return already exists)" in {
      val tempFile = Files.createTempFile("test-monthly-return", ".ndjson")
      Files.writeString(tempFile, """{"nino":"AB000001C","accountNumber":"STD000001"}""" + "\n")

      val conflict = UpstreamErrorResponse(
        message = """{"submissionId":"existing-id"}""",
        statusCode = CONFLICT,
        reportAs = CONFLICT,
        headers = Map.empty
      )

      when(mockSubmissionConnector.createMonthlyReturn(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Left(conflict)))
      when(mockSubmissionConnector.sendMonthlyReturn(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(())))

      val result = service.submitMonthlyReturn(validZReference, validTaxYear, validMonth, tempFile).futureValue

      Files.deleteIfExists(tempFile)
      result shouldBe Right(())
    }

    "return Left(InternalServerErr) when createMonthlyReturn returns a 500 UpstreamErrorResponse" in {
      val tempFile = Files.createTempFile("test-monthly-return", ".ndjson")
      Files.writeString(tempFile, """{"nino":"AB000001C","accountNumber":"STD000001"}""" + "\n")

      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Internal server error",
        statusCode = INTERNAL_SERVER_ERROR,
        reportAs = INTERNAL_SERVER_ERROR,
        headers = Map.empty
      )

      when(mockSubmissionConnector.createMonthlyReturn(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Left(exception)))

      val result = service.submitMonthlyReturn(validZReference, validTaxYear, validMonth, tempFile).futureValue

      Files.deleteIfExists(tempFile)
      result shouldBe Left(InternalServerErr())
    }

    "return Left(UnauthorisedErr) when sendMonthlyReturn returns a 401 UpstreamErrorResponse" in {
      val tempFile = Files.createTempFile("test-monthly-return", ".ndjson")
      Files.writeString(tempFile, """{"nino":"AB000001C","accountNumber":"STD000001"}""" + "\n")

      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = UNAUTHORIZED,
        reportAs = UNAUTHORIZED,
        headers = Map.empty
      )

      when(mockSubmissionConnector.createMonthlyReturn(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(())))
      when(mockSubmissionConnector.sendMonthlyReturn(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Left(exception)))

      val result = service.submitMonthlyReturn(validZReference, validTaxYear, validMonth, tempFile).futureValue

      Files.deleteIfExists(tempFile)
      result shouldBe Left(UnauthorisedErr)
    }

    "return Left(InternalServerErr) when sendMonthlyReturn returns a 500 UpstreamErrorResponse" in {
      val tempFile = Files.createTempFile("test-monthly-return", ".ndjson")
      Files.writeString(tempFile, """{"nino":"AB000001C","accountNumber":"STD000001"}""" + "\n")

      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Internal server error",
        statusCode = INTERNAL_SERVER_ERROR,
        reportAs = INTERNAL_SERVER_ERROR,
        headers = Map.empty
      )

      when(mockSubmissionConnector.createMonthlyReturn(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(())))
      when(mockSubmissionConnector.sendMonthlyReturn(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Left(exception)))

      val result = service.submitMonthlyReturn(validZReference, validTaxYear, validMonth, tempFile).futureValue

      Files.deleteIfExists(tempFile)
      result shouldBe Left(InternalServerErr())
    }
  }
}
