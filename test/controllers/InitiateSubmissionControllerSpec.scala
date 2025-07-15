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

package controllers

import cats.data.EitherT
import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.disareturns.connectors.response.{EtmpObligations, EtmpReportingWindow}
import uk.gov.hmrc.disareturns.controllers.InitiateSubmissionController
import uk.gov.hmrc.disareturns.services.{ETMPService, InitiateSubmissionDataService, PPNSService}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class InitiateSubmissionControllerSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with BeforeAndAfterEach
    with GuiceOneServerPerSuite {

  val mockAuthConnector: AuthConnector                 = mock[AuthConnector]
  val mockEtmpService:   ETMPService                   = mock[ETMPService]
  val mockPpnsService:   PPNSService                   = mock[PPNSService]
  val mockMongoService:  InitiateSubmissionDataService = mock[InitiateSubmissionDataService]

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .overrides(
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[ETMPService].toInstance(mockEtmpService),
      bind[PPNSService].toInstance(mockPpnsService),
      bind[InitiateSubmissionDataService].toInstance(mockMongoService)
    )
    .build()

  implicit val ec:  ExecutionContext = app.injector.instanceOf[ExecutionContext]
  implicit val hc:  HeaderCarrier    = HeaderCarrier()
  implicit val mat: Materializer     = app.materializer

  val controller: InitiateSubmissionController = app.injector.instanceOf[InitiateSubmissionController]

  val isaManagerRef   = "Z123456"
  val boxId           = "box-123"
  val returnId        = "return-789"
  val obligation: EtmpObligations = EtmpObligations(false)
  val reportingWindow: EtmpReportingWindow = EtmpReportingWindow(true)

  val validSubmissionJson: JsValue = Json.obj(
    "totalRecords"     -> 400,
    "submissionPeriod" -> "JAN",
    "taxYear"          -> 2025
  )

  override def beforeEach(): Unit =
    reset(mockEtmpService, mockPpnsService, mockMongoService)

  "InitiateSubmissionController.initiate" should {

    "return 200 OK for valid submission" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockEtmpService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(EitherT.rightT((reportingWindow,obligation)))
      when(mockPpnsService.getBoxId(any())(any()))
        .thenReturn(EitherT.rightT(boxId))
      when(mockMongoService.saveInitiateSubmission(any(), any(), any()))
        .thenReturn(Future.successful(returnId))

      val fakeRequest = FakeRequest("POST", s"/initiate/$isaManagerRef")
        .withHeaders("X-Client-ID" -> "client-999")
        .withBody(validSubmissionJson)

      val result = controller.initiate(isaManagerRef)(fakeRequest)

      status(result)                                  shouldBe OK
      (contentAsJson(result) \ "returnId").as[String] shouldBe returnId
      (contentAsJson(result) \ "boxId").as[String]    shouldBe boxId
      (contentAsJson(result) \ "action").as[String]   shouldBe "SUBMIT_RETURN_TO_PAGINATED_API"
    }

//    "return 400 BadRequest for invalid JSON" in {
//      val invalidJson = Json.obj("totalRecords" -> 1) // Missing required fields
//      val request = FakeRequest("POST", s"/initiate/$isaManagerRef")
//        .withHeaders("X-Client-ID" -> "client-abc")
//        .withBody(invalidJson)
//
//      val result = controller.initiate(isaManagerRef)(request)
//
//      status(result) shouldBe BAD_REQUEST
//      (contentAsJson(result) \ "message").as[String] should include("Invalid request")
//    }
//
//    "return 403 if validateEtmpSubmissionEligibility fails" in {
//      val error = ErrorResponse("NOT_ELIGIBLE", "You are not eligible")
//
//      when(mockEtmpService.validateEtmpSubmissionEligibility(any())(any()))
//        .thenReturn(EitherT.leftT(error))
//
//      val request = FakeRequest("POST", s"/initiate/$isaManagerRef")
//        .withHeaders("X-Client-ID" -> "client-abc")
//        .withBody(validSubmissionJson)
//
//      val result = controller.initiate(isaManagerRef)(request)
//
//      status(result) shouldBe FORBIDDEN
//      (contentAsJson(result) \ "code").as[String] shouldBe "NOT_ELIGIBLE"
//    }
//
//    "return 403 if getBoxId fails" in {
//      val error = ErrorResponse("BOX_ERROR", "Cannot get box ID")
//
//      when(mockEtmpService.validateEtmpSubmissionEligibility(any())(any()))
//        .thenReturn(EitherT.rightT(()))
//      when(mockPpnsService.getBoxId(any())(any()))
//        .thenReturn(EitherT.leftT(error))
//
//      val request = FakeRequest("POST", s"/initiate/$isaManagerRef")
//        .withHeaders("X-Client-ID" -> "client-abc")
//        .withBody(validSubmissionJson)
//
//      val result = controller.initiate(isaManagerRef)(request)
//
//      status(result) shouldBe FORBIDDEN
//      (contentAsJson(result) \ "code").as[String] shouldBe "BOX_ERROR"
//    }
//
//    "return 500 if saveInitiateSubmission throws exception" in {
//      when(mockEtmpService.validateEtmpSubmissionEligibility(any())(any()))
//        .thenReturn(EitherT.rightT(()))
//      when(mockPpnsService.getBoxId(any())(any()))
//        .thenReturn(EitherT.rightT(boxId))
//      when(mockMongoService.saveInitiateSubmission(any(), any(), any()))
//        .thenReturn(Future.failed(new RuntimeException("DB error")))
//
//      val request = FakeRequest("POST", s"/initiate/$isaManagerRef")
//        .withHeaders("X-Client-ID" -> "client-abc")
//        .withBody(validSubmissionJson)
//
//      val result = controller.initiate(isaManagerRef)(request)
//
//      status(result) shouldBe INTERNAL_SERVER_ERROR
//    }
//  }
  }
}
