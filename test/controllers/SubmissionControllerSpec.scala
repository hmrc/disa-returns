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

///*
// * Copyright 2025 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package controllers
//
//import org.apache.pekko.stream.Materializer
//import org.mockito.ArgumentMatchers._
//import org.mockito.Mockito._
//import org.scalatest.BeforeAndAfterEach
//import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
//import org.scalatest.matchers.should.Matchers
//import org.scalatest.wordspec.AnyWordSpec
//import org.scalatestplus.mockito.MockitoSugar
//import org.scalatestplus.play.guice.GuiceOneServerPerSuite
//import play.api.Application
//import play.api.inject.bind
//import play.api.inject.guice.GuiceApplicationBuilder
//import play.api.libs.json._
//import play.api.test.FakeRequest
//import play.api.test.Helpers._
//import uk.gov.hmrc.auth.core.AuthConnector
//import uk.gov.hmrc.auth.core.retrieve.Retrieval
//import uk.gov.hmrc.disareturns.connectors.response.{EtmpObligations, EtmpReportingWindow}
//import uk.gov.hmrc.disareturns.controllers.SubmissionController
//import uk.gov.hmrc.disareturns.services.{ETMPService, InitiateSubmissionDataService, PPNSService}
//import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
//
//import scala.concurrent.{ExecutionContext, Future}
//
//class SubmissionControllerSpec
//    extends AnyWordSpec
//    with Matchers
//    with MockitoSugar
//    with ScalaFutures
//    with BeforeAndAfterEach
//    with IntegrationPatience
//    with GuiceOneServerPerSuite {
//
//  val mockAuthConnector:              AuthConnector                 = mock[AuthConnector]
//  val mockEtmpService:                ETMPService                   = mock[ETMPService]
//  val mockPpnsService:                PPNSService                   = mock[PPNSService]
//  val mockMongoJourneyAnswersService: InitiateSubmissionDataService = mock[InitiateSubmissionDataService]
//
//  override def fakeApplication(): Application = GuiceApplicationBuilder()
//    .overrides(
//      bind[AuthConnector].toInstance(mockAuthConnector),
//      bind[PPNSService].toInstance(mockPpnsService),
//      bind[ETMPService].toInstance(mockEtmpService),
//      bind[InitiateSubmissionDataService].toInstance(mockMongoJourneyAnswersService)
//    )
//    .build()
//
//  implicit val ec:           ExecutionContext = app.injector.instanceOf[ExecutionContext]
//  implicit val hc:           HeaderCarrier    = HeaderCarrier()
//  implicit val materializer: Materializer     = app.materializer
//
//  private val controller: SubmissionController = app.injector.instanceOf[SubmissionController]
//
//  override def beforeEach(): Unit = {
//    super.beforeEach()
//    when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
//
//  }
//
//  val validSubmissionJson: JsValue = Json.obj(
//    "totalRecords"     -> 400,
//    "submissionPeriod" -> "JAN",
//    "taxYear"          -> 2024
//  )
//  val boxId               = "Box 1"
//  val isaManagerReference = "Manager1"
//  val returnId            = "6be12891-8fb7-44c9-aecf-a8dbc07fbbec"
//
//  "SubmissionController.init" should {
//
//    "return 200 OK with returnId when  obligation not already met and reporting window is open" in {
//      val obligations     = EtmpObligations(obligationAlreadyMet = false)
//      val reportingWindow = EtmpReportingWindow(reportingWindowOpen = true)
//
//      when(mockEtmpService.checkObligationStatus(any())(any()))
//        .thenReturn(Future.successful(Right(obligations)))
//      when(mockEtmpService.checkReportingWindowStatus()(any()))
//        .thenReturn(Future.successful(Right(reportingWindow)))
//      when(mockPpnsService.getBoxId(any())(any()))
//        .thenReturn(Future.successful(Right(boxId)))
//      when(mockMongoJourneyAnswersService.save(any()))
//        .thenReturn(Future.successful(returnId))
//
//      val fakeRequest: FakeRequest[JsValue] = FakeRequest("POST", "/monthly/123/init")
//        .withHeaders("X-Client-ID" -> "client123")
//        .withBody(validSubmissionJson)
//
//      val result = controller.initiateSubmission(isaManagerReference)(fakeRequest)
//
//      status(result) shouldBe OK
//
//      val json = contentAsJson(result)
//      (json \ "returnId").as[String] shouldBe returnId
//      (json \ "action").as[String]   shouldBe "SUBMIT_RETURN_TO_PAGINATED_API"
//      (json \ "boxId").as[String]    shouldBe boxId
//    }
//
//  }
//
//  "return 403 Forbidden if obligation already met" in {
//    val obligations     = EtmpObligations(obligationAlreadyMet = true)
//    val reportingWindow = EtmpReportingWindow(reportingWindowOpen = true)
//
//    when(mockEtmpService.checkObligationStatus(any())(any()))
//      .thenReturn(Future.successful(Right(obligations)))
//    when(mockEtmpService.checkReportingWindowStatus()(any()))
//      .thenReturn(Future.successful(Right(reportingWindow)))
//    when(mockPpnsService.getBoxId(any())(any()))
//      .thenReturn(Future.successful(Right(boxId)))
//
//    val fakeRequest: FakeRequest[JsValue] = FakeRequest("POST", "/monthly/123/init")
//      .withHeaders("X-Client-ID" -> "client123")
//      .withBody(validSubmissionJson)
//
//    val result = controller.initiateSubmission(isaManagerReference)(fakeRequest)
//
//    status(result) shouldBe FORBIDDEN
//
//    val json = contentAsJson(result)
//
//    (json \ "code").as[String]  shouldBe "OBLIGATION_CLOSED"
//    (json \ "message").as[String] should include("Obligation closed")
//
//  }
//
//  "return 403 Forbidden if reporting window is closed" in {
//    val obligations     = EtmpObligations(obligationAlreadyMet = false)
//    val reportingWindow = EtmpReportingWindow(reportingWindowOpen = false)
//
//    when(mockEtmpService.checkObligationStatus(any())(any()))
//      .thenReturn(Future.successful(Right(obligations)))
//    when(mockEtmpService.checkReportingWindowStatus()(any()))
//      .thenReturn(Future.successful(Right(reportingWindow)))
//    when(mockPpnsService.getBoxId(any())(any()))
//      .thenReturn(Future.successful(Right(boxId)))
//
//    val fakeRequest: FakeRequest[JsValue] = FakeRequest("POST", "/monthly/123/init")
//      .withHeaders("X-Client-ID" -> "client123")
//      .withBody(validSubmissionJson)
//
//    val result = controller.initiateSubmission(isaManagerReference)(fakeRequest)
//
//    status(result) shouldBe FORBIDDEN
//
//    val json = contentAsJson(result)
//
//    (json \ "code").as[String]  shouldBe "REPORTING_WINDOW_CLOSED"
//    (json \ "message").as[String] should include("Reporting window has been closed")
//  }
//
//  "return 500 InternalServerError if downstream call fails" in {
//    val reportingWindow = EtmpReportingWindow(reportingWindowOpen = false)
//    when(mockEtmpService.checkObligationStatus(any())(any()))
//      .thenReturn(Future.successful(Left(UpstreamErrorResponse("message", 500))))
//    when(mockEtmpService.checkReportingWindowStatus()(any()))
//      .thenReturn(Future.successful(Right(reportingWindow)))
//    when(mockPpnsService.getBoxId(any())(any()))
//      .thenReturn(Future.successful(Right(boxId)))
//    val fakeRequest: FakeRequest[JsValue] = FakeRequest("POST", "/monthly/123/init")
//      .withHeaders("X-Client-ID" -> "client123")
//      .withBody(validSubmissionJson)
//
//    val result = controller.initiateSubmission(isaManagerReference)(fakeRequest)
//
//    status(result) shouldBe INTERNAL_SERVER_ERROR
//
//    (contentAsJson(result) \ "code").as[String]    should include("INTERNAL_SERVER_ERROR")
//    (contentAsJson(result) \ "message").as[String] should include("There has been an issue processing your request")
//  }
//
//  "return 400 BadRequest for invalid JSON" in {
//    val invalidJson = Json.obj(
//      "totalRecords" -> 400
//    )
//    val request: FakeRequest[JsValue] = FakeRequest("POST", "/monthly/123/init")
//      .withHeaders("X-Client-ID" -> "client123")
//      .withBody(invalidJson)
//
//    val result = controller.initiateSubmission(isaManagerReference)(request)
//
//    status(result)                               shouldBe BAD_REQUEST
//    (contentAsJson(result) \ "message").as[String] should include("Invalid request")
//  }
//
//  "return 403 Forbidden with multiple errors when obligation is met and reporting window is closed" in {
//    val obligations     = EtmpObligations(obligationAlreadyMet = true)
//    val reportingWindow = EtmpReportingWindow(reportingWindowOpen = false)
//
//    when(mockEtmpService.checkObligationStatus(any())(any()))
//      .thenReturn(Future.successful(Right(obligations)))
//    when(mockEtmpService.checkReportingWindowStatus()(any()))
//      .thenReturn(Future.successful(Right(reportingWindow)))
//    when(mockPpnsService.getBoxId(any())(any()))
//      .thenReturn(Future.successful(Right(boxId)))
//
//    val fakeRequest: FakeRequest[JsValue] = FakeRequest("POST", "/monthly/123/init")
//      .withHeaders("X-Client-ID" -> "client123")
//      .withBody(validSubmissionJson)
//
//    val result = controller.initiateSubmission(isaManagerReference)(fakeRequest)
//
//    status(result) shouldBe FORBIDDEN
//
//    val json = contentAsJson(result)
//    (json \ "code").as[String]  shouldBe "FORBIDDEN"
//    (json \ "message").as[String] should include("Multiple issues found regarding your submission")
//
//    val errors = (json \ "errors").as[Seq[JsValue]]
//    errors.exists(e => (e \ "code").as[String] == "OBLIGATION_CLOSED")       shouldBe true
//    errors.exists(e => (e \ "code").as[String] == "REPORTING_WINDOW_CLOSED") shouldBe true
//  }
//
//}
