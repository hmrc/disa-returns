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
import org.mockito.Mockito._
import play.api.http.Status.{NO_CONTENT, OK}
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InternalServerErr, UnauthorisedErr}
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaAccount
import uk.gov.hmrc.disareturns.services.NPSService
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class NPSServiceSpec extends BaseUnitSpec {

  val service = new NPSService(mockNPSConnector)

  val isaManagerReference = "Z1234"

  "NPSService.notification" should {

    "return Right(HttpResponse) when connector returns a 204" in {
      val httpResponse = HttpResponse(204, "")
      when(mockNPSConnector.sendNotification(isaManagerReference))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val result = service.notification(isaManagerReference).value.futureValue

      result shouldBe Right(httpResponse)
    }

    "return Left(ErrorResponse) when nps connector  returns an UpstreamErrorResponse" in {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
        headers = Map.empty
      )

      when(mockNPSConnector.sendNotification(isaManagerReference))
        .thenReturn(EitherT.leftT[Future, HttpResponse](exception))

      val result = service.notification(isaManagerReference).value.futureValue

      result shouldBe Left(UnauthorisedErr)
    }
  }

  "NPSService.submitIsaAccounts" should {

    "return Right(()) when connector responds with 204 NO_CONTENT" in {
      val httpResponse: HttpResponse = HttpResponse(NO_CONTENT, "")

      when(mockNPSConnector.submit(validZRef, Seq.empty[IsaAccount]))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val result: Either[ErrorResponse, Unit] =
        service.submitIsaAccounts(validZRef, Seq.empty).futureValue

      result shouldBe Right(())
    }

    "return Left(UnauthorisedErr) when connector fails with 401 UpstreamErrorResponse" in {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
        headers = Map.empty
      )

      when(mockNPSConnector.submit(validZRef, Seq.empty[IsaAccount]))
        .thenReturn(EitherT.leftT[Future, HttpResponse](exception))

      val result: Either[ErrorResponse, Unit] =
        service.submitIsaAccounts(validZRef, Seq.empty).futureValue

      result shouldBe Left(UnauthorisedErr)
    }

    "return Left(InternalServerErr) when a non-204 success status is returned (e.g. 200 OK)" in {
      val httpResponse: HttpResponse = HttpResponse(OK, "ignored body")

      when(mockNPSConnector.submit(validZRef, Seq.empty[IsaAccount]))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val result: Either[ErrorResponse, Unit] =
        service.submitIsaAccounts(validZRef, Seq.empty).futureValue

      result shouldBe Left(InternalServerErr("Unexpected status 200 was received from NPS submission"))
    }
  }
}
