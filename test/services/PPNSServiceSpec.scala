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

import org.mockito.Mockito._
import uk.gov.hmrc.disareturns.connectors.response.{EtmpObligations, EtmpReportingWindow}
import uk.gov.hmrc.disareturns.models.response.ppns.{Box, BoxCreator}
import uk.gov.hmrc.disareturns.services.{ETMPService, PPNSService}
import uk.gov.hmrc.http.UpstreamErrorResponse
import utils.BaseUnitSpec

import scala.concurrent.Future

class PPNSServiceSpec extends BaseUnitSpec {

  val testClientId: String = "123456"
  val service: PPNSService = new PPNSService(mockPPNSConnector)

  "PPNSService.getBoxId" should {

    "return BoxId when call to PPNS connector returns a Box" in {
      val expectedResponse: Box = Box(
        boxId = "boxId1",
        boxName = "Test_Box",
        boxCreator = BoxCreator(clientId = testClientId),
        applicationId = Some("applicationId"),
        subscriber = None
      )
      when(mockPPNSConnector.getBox(testClientId))
        .thenReturn(Future.successful(Right(expectedResponse)))

      val result: Either[UpstreamErrorResponse, String] = service.getBoxId(testClientId).futureValue

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
        .thenReturn(Future.successful(Left(exception)))

      val result: Either[UpstreamErrorResponse, String] = service.getBoxId(testClientId).futureValue

      result shouldBe Left(exception)
    }
  }
}
