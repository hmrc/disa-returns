/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.libs.json.Json
import uk.gov.hmrc.disareturns.models.errors.connector.responses.{ErrorResponse, InternalServerErr, Unauthorised}
import uk.gov.hmrc.disareturns.models.ppns.response.{Box, BoxCreator}
import uk.gov.hmrc.disareturns.services.PPNSService
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class PPNSServiceSpec extends BaseUnitSpec {

  val testClientId: String      = "123456"
  val service:      PPNSService = new PPNSService(mockPPNSConnector)

  "PPNSService.getBoxId" should {

    "return BoxId when call to PPNS connector returns a Box" in {
      val expectedResponse: Box = Box(
        boxId = "boxId1",
        boxName = "Test_Box",
        boxCreator = BoxCreator(clientId = testClientId),
        applicationId = Some("applicationId"),
        subscriber = None
      )
      val boxJson      = Json.toJson(expectedResponse)
      val httpResponse = HttpResponse(200, boxJson.toString())

      when(mockPPNSConnector.getBox(testClientId))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val result: Either[ErrorResponse, String] = service.getBoxId(testClientId).value.futureValue

      result shouldBe Right(expectedResponse.boxId)
    }

    "return Left(UpstreamErrorResponse) when the call to ETMP connector fails with an UpstreamErrorResponse" in {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
        headers = Map.empty
      )

      when(mockPPNSConnector.getBox(testClientId))
        .thenReturn(EitherT.leftT[Future, HttpResponse](exception))

      val result: Either[ErrorResponse, String] = service.getBoxId(testClientId).value.futureValue

      result shouldBe Left(Unauthorised)
    }

    "return Left(InternalServerErr) when the call to ETMP connector fails with an UpstreamErrorResponse" in {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Forbidden to access this service",
        statusCode = 403,
        reportAs = 403,
        headers = Map.empty
      )

      when(mockPPNSConnector.getBox(testClientId))
        .thenReturn(EitherT.leftT[Future, HttpResponse](exception))

      val result: Either[ErrorResponse, String] = service.getBoxId(testClientId).value.futureValue

      result shouldBe Left(InternalServerErr)
    }
  }
}
