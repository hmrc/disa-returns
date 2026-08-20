/*
 * Copyright 2026 HM Revenue & Customs
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
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InternalServerErr, UnauthorisedErr}
import uk.gov.hmrc.disareturns.models.submission.ReportingWindowStatus
import uk.gov.hmrc.disareturns.services.ReportingWindowService
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class ReportingWindowServiceSpec extends BaseUnitSpec {

  "ReportingWindowService.getReportingWindowStatus" should {

    "return Right(ReportingWindowStatus) when the call to disa-returns-submission returns a reporting window status" in new TestSetup {
      val expectedResponse:          ReportingWindowStatus = ReportingWindowStatus(true)
      val reportingWindowStatusJson: JsValue               = Json.toJson(expectedResponse)
      val httpResponse:              HttpResponse          = HttpResponse(200, reportingWindowStatusJson.toString())

      when(mockSubmissionConnector.getReportingWindowStatus(testCredentialId))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val result: Either[ErrorResponse, ReportingWindowStatus] = service.getReportingWindowStatus(testCredentialId).value.futureValue

      result shouldBe Right(expectedResponse)
    }

    "return Left(UnauthorisedErr) when the call to disa-returns-submission returns an UpstreamErrorResponse" in new TestSetup {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = 401,
        reportAs = 401,
        headers = Map.empty
      )

      when(mockSubmissionConnector.getReportingWindowStatus(testCredentialId))
        .thenReturn(EitherT.leftT[Future, HttpResponse](exception))

      val result: Either[ErrorResponse, ReportingWindowStatus] = service.getReportingWindowStatus(testCredentialId).value.futureValue

      result shouldBe Left(UnauthorisedErr)
    }

    "return Left(InternalServerErr) when the response JSON cannot be parsed into a reporting window status" in new TestSetup {
      val reportingWindow: String = """{
                                      |  "reportingWindowOpen": "false"
                                      |}""".stripMargin
      val httpResponse: HttpResponse = HttpResponse(200, reportingWindow)

      when(mockSubmissionConnector.getReportingWindowStatus(testCredentialId))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val result: Either[ErrorResponse, ReportingWindowStatus] = service.getReportingWindowStatus(testCredentialId).value.futureValue

      result shouldBe Left(InternalServerErr())
    }
  }

  trait TestSetup {
    val service: ReportingWindowService = new ReportingWindowService(mockSubmissionConnector)
  }
}
