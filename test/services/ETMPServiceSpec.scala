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
import uk.gov.hmrc.disareturns.services.ETMPService
import uk.gov.hmrc.http.UpstreamErrorResponse
import utils.BaseUnitSpec

import scala.concurrent.Future

class ETMPServiceSpec extends BaseUnitSpec {

  "ETMPService.checkObligationStatus" should {

    "return Right(EtmpObligations) when call to ETMP connector returns an obligation status" in new TestSetup {
      val expectedResponse: EtmpObligations = EtmpObligations(true)

      when(mockETMPConnector.checkReturnsObligationStatus(testIsaManagerReferenceNumber))
        .thenReturn(Future.successful(Right(expectedResponse)))

      val result: Either[UpstreamErrorResponse, EtmpObligations] = service.checkObligationStatus(testIsaManagerReferenceNumber).futureValue

      result shouldBe Right(expectedResponse)
    }

    "return Left(UpstreamErrorResponse) when the call to ETMP connector fails with an UpstreamErrorResponse" in new TestSetup {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
        headers = Map.empty
      )
      when(mockETMPConnector.checkReturnsObligationStatus(testIsaManagerReferenceNumber))
        .thenReturn(Future.successful(Left(exception)))

      val result: Either[UpstreamErrorResponse, EtmpObligations] = service.checkObligationStatus(testIsaManagerReferenceNumber).futureValue

      result shouldBe Left(exception)
    }
  }

  "ETMPService.checkReportingWindowStatus" should {

    "return Right(EtmpReportingWindow) when call to ETMP connector returns a reporting window status" in new TestSetup {
      val expectedResponse: EtmpReportingWindow = EtmpReportingWindow(true)

      when(mockETMPConnector.checkReportingWindowStatus).thenReturn(Future.successful(Right(expectedResponse)))
      val result: Either[UpstreamErrorResponse, EtmpReportingWindow] = service.checkReportingWindowStatus().futureValue

      result shouldBe Right(expectedResponse)
    }

    "return Left(UpstreamErrorResponse) when the call to ETMP connector returns an UpstreamErrorResponse" in new TestSetup {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
        headers = Map.empty
      )
      when(mockETMPConnector.checkReportingWindowStatus).thenReturn(Future.successful(Left(exception)))

      val result: Either[UpstreamErrorResponse, EtmpReportingWindow] = service.checkReportingWindowStatus().futureValue

      result shouldBe Left(exception)
    }
  }

  trait TestSetup {
    val testIsaManagerReferenceNumber: String = "123456"
    val service: ETMPService = new ETMPService(mockETMPConnector)
  }
}
