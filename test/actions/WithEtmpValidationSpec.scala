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

package actions

import cats.data.EitherT
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.libs.json._
import play.api.mvc.Results.Ok
import play.api.mvc._
import play.api.test.Helpers._
import uk.gov.hmrc.disareturns.connectors.response.{EtmpObligations, EtmpReportingWindow}
import uk.gov.hmrc.disareturns.controllers.WithEtmpValidation
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, MultipleErrorResponse, ObligationClosed, ReportingWindowClosed}
import uk.gov.hmrc.disareturns.services.ETMPService
import uk.gov.hmrc.http.HeaderCarrier
import utils.BaseUnitSpec

import scala.concurrent.Future

class WithEtmpValidationSpec
  extends BaseUnitSpec {


  trait Setup extends WithEtmpValidation {
    implicit val hc: HeaderCarrier                = HeaderCarrier()
    val etmpService: ETMPService                  = mock[ETMPService]
    val isaManagerRef                             = "Z123456"
    val testSuccessResult: Result                 = Ok("success")

    def testBlock(): () => Future[Result] = () => Future.successful(testSuccessResult)
    val obligation:      EtmpObligations     = EtmpObligations(false)
    val reportingWindow: EtmpReportingWindow = EtmpReportingWindow(true)
  }

  "validateEtmpAndThen" should {

    "call the block when ETMP validation returns Right" in new Setup {
      when(etmpService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(EitherT.rightT((reportingWindow, obligation)))

      val result: Future[Result] = validateEtmpAndThen(isaManagerRef)(testBlock())(etmpService, hc, ec, ErrorResponse.format)
        status(result) shouldBe OK
        contentAsString(result) shouldBe "success"
      }
    }

  "return Forbidden with error JSON when ETMP validation returns ReportingWindowClosed" in new Setup {
    val error: ReportingWindowClosed.type = ReportingWindowClosed
    when(etmpService.validateEtmpSubmissionEligibility(any())(any(), any()))
      .thenReturn(EitherT.leftT(error))

    val result: Future[Result] = validateEtmpAndThen(isaManagerRef)(testBlock())(etmpService, hc, ec, ErrorResponse.format)
    status(result) shouldBe FORBIDDEN
    contentAsJson(result) shouldBe Json.toJson(error: ErrorResponse)
  }

  "return Forbidden with error JSON when ETMP validation returns ObligationClosed" in new Setup {
    val error: ObligationClosed.type = ObligationClosed
    when(etmpService.validateEtmpSubmissionEligibility(any())(any(), any()))
      .thenReturn(EitherT.leftT(error))

    val result: Future[Result] = validateEtmpAndThen(isaManagerRef)(testBlock())(etmpService, hc, ec, ErrorResponse.format)
    status(result) shouldBe FORBIDDEN
    contentAsJson(result) shouldBe Json.toJson(error: ErrorResponse)
  }

  "return Forbidden with error JSON when ETMP validation returns ReportingWindowClosed & ObligationClosed" in new Setup {
    val errors: MultipleErrorResponse = MultipleErrorResponse(errors = Seq(ReportingWindowClosed, ObligationClosed))
    when(etmpService.validateEtmpSubmissionEligibility(any())(any(), any()))
      .thenReturn(EitherT.leftT(errors))

    val result: Future[Result] = validateEtmpAndThen(isaManagerRef)(testBlock())(etmpService, hc, ec, ErrorResponse.format)
    status(result) shouldBe FORBIDDEN
    contentAsJson(result) shouldBe Json.toJson(errors: ErrorResponse)
  }
}
