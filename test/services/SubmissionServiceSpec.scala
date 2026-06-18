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
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.services.SubmissionService
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import utils.BaseUnitSpec

import org.mockito.Mockito._
import scala.concurrent.Future

class SubmissionServiceSpec extends BaseUnitSpec {

  val service          = new SubmissionService(mockSubmissionConnector)
  val nilReturnReported = false

  "SubmissionService.declare" should {

    "return Right(HttpResponse) when the connector returns a successful response" in {
      val httpResponse = HttpResponse(200, "")
      when(mockSubmissionConnector.sendDeclaration(validZReference, validTaxYear, validMonth, nilReturnReported))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val result = service.declare(validZReference, validTaxYear, validMonth, nilReturnReported).value.futureValue

      result shouldBe Right(httpResponse)
    }

    "return Left(UnauthorisedErr) when the connector returns a 401 UpstreamErrorResponse" in {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
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
        statusCode = 500,
        reportAs = 500,
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
}
