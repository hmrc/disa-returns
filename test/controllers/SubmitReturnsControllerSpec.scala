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

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json._
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.disareturns.connectors.response.{EtmpObligations, EtmpReportingWindow}
import uk.gov.hmrc.disareturns.controllers.SubmitReturnsController
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.submission.isaAccounts.{IsaAccount, IsaType, StandardIsaTransfer}
import utils.BaseUnitSpec

import java.time.LocalDate
import scala.concurrent.Future

class SubmitReturnsControllerSpec extends BaseUnitSpec {

  val controller: SubmitReturnsController = app.injector.instanceOf[SubmitReturnsController]

  val isaManagerRef = "Z123456"
  val boxId         = "box-123"
  val obligation:      EtmpObligations     = EtmpObligations(false)
  val reportingWindow: EtmpReportingWindow = EtmpReportingWindow(true)

  val ndJsonLine =
    """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

  val validModel: StandardIsaTransfer = StandardIsaTransfer(
    accountNumber = "STD000001",
    nino = "AB000001C",
    firstName = "First1",
    middleName = None,
    lastName = "Last1",
    dateOfBirth = LocalDate.parse("1980-01-02"),
    isaType = IsaType.STOCKS_AND_SHARES,
    reportingATransfer = true,
    dateOfLastSubscription = LocalDate.parse("2025-06-01"),
    totalCurrentYearSubscriptionsToDate = BigDecimal(2500.00),
    marketValueOfAccount = BigDecimal(10000.00),
    accountNumberOfTransferringAccount = "OLD000001",
    amountTransferred = BigDecimal(5000.00),
    flexibleIsa = false
  )
  def fakeRequestWithStream(ndJsonString: String = ndJsonLine): Request[Source[ByteString, _]] = FakeRequest()
    .withBody(Source.single(ByteString(ndJsonString + "\n")))
    .withHeaders("X-Client-ID" -> "client-999")
    .withHeaders("Content-Type" -> "application/x-ndjson")

  val validSubmissionJson: JsValue = Json.obj(
    "totalRecords"     -> 400,
    "submissionPeriod" -> "JAN",
    "taxYear"          -> LocalDate.now.getYear
  )

  "ReturnsSubmissionController#submit" should {

    "return 204 when processing is successful" in {

      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockMonthlyReportDocumentService.existsByIsaManagerReferenceAndReturnId(any(), any()))
        .thenReturn(Future.successful(true))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Right((reportingWindow, obligation))))
      when(mockStreamingParserService.processValidatedStream(any()))
        .thenReturn(Future.successful(Right(Seq.empty)))
      when(mockNPSService.submitSubscriptionData(any, any)(any)).thenReturn(Future.successful(Right(())))

      val result = controller.submit(isaManagerReferenceNumber = isaManagerRef, validTaxYear, validMonthStr)(fakeRequestWithStream())

      status(result) shouldBe NO_CONTENT
    }

    "return 400 for FirstLevelValidationException - missing accountNumber" in {
      val ndJsonLineError =
        """{"nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockMonthlyReportDocumentService.existsByIsaManagerReferenceAndReturnId(any(), any()))
        .thenReturn(Future.successful(true))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Right((reportingWindow, obligation))))
      when(mockStreamingParserService.processValidatedStream(any()))
        .thenReturn(Future.successful(Left(FirstLevelValidationFailure(NinoOrAccountNumMissingErr))))

      val result = controller.submit("Z123", validTaxYear, validMonthStr).apply(fakeRequestWithStream(ndJsonLineError))

      status(result)                                 shouldBe BAD_REQUEST
      (contentAsJson(result) \ "code").as[String]    shouldBe "NINO_OR_ACC_NUM_MISSING"
      (contentAsJson(result) \ "message").as[String] shouldBe "All models sent must include an account number and nino in order to process correctly"
    }

    "return 400 for SecondLevelValidationException - invalid DOB" in {
      val ndJsonLine =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-0-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockMonthlyReportDocumentService.existsByIsaManagerReferenceAndReturnId(any(), any()))
        .thenReturn(Future.successful(true))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Right((reportingWindow, obligation))))
      when(mockStreamingParserService.processValidatedStream(any()))
        .thenReturn(
          Future.successful(
            Left(
              SecondLevelValidationFailure(
                Seq(
                  SecondLevelValidationError(
                    nino = "AB000001C",
                    accountNumber = "STD000001",
                    code = "INVALID_DATE_OF_BIRTH",
                    message = "Date of birth is not formatted correctly"
                  )
                )
              )
            )
          )
        )

      val result = controller.submit("Z123", validTaxYear, validMonthStr).apply(fakeRequestWithStream(ndJsonLine))
      val json   = contentAsJson(result)

      status(result)                shouldBe BAD_REQUEST
      (json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
      (json \ "message").as[String] shouldBe "One or more models failed validation"

      val errors = (json \ "errors").as[Seq[JsValue]]
      errors should have size 1

      val error = errors.head
      (error \ "nino").as[String]          shouldBe "AB000001C"
      (error \ "accountNumber").as[String] shouldBe "STD000001"
      (error \ "code").as[String]          shouldBe "INVALID_DATE_OF_BIRTH"
      (error \ "message").as[String]       shouldBe "Date of birth is not formatted correctly"
    }

    "return 400 for SecondLevelValidationException - invalid DOB & firstName - should only return first failure for each model" in {
      val ndJsonLine =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"","middleName":null,"lastName":"Last1","dateOfBirth":"1980-0-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockMonthlyReportDocumentService.existsByIsaManagerReferenceAndReturnId(any(), any()))
        .thenReturn(Future.successful(true))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Right((reportingWindow, obligation))))
      when(mockStreamingParserService.processValidatedStream(any()))
        .thenReturn(
          Future.successful(
            Left(
              SecondLevelValidationFailure(
                Seq(
                  SecondLevelValidationError(
                    nino = "AB000001C",
                    accountNumber = "STD000001",
                    code = "INVALID_DATE_OF_BIRTH",
                    message = "Date of birth is not formatted correctly"
                  )
                )
              )
            )
          )
        )

      val result = controller.submit("Z123", validTaxYear, validMonthStr).apply(fakeRequestWithStream(ndJsonLine))
      val json   = contentAsJson(result)

      status(result)                shouldBe BAD_REQUEST
      (json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
      (json \ "message").as[String] shouldBe "One or more models failed validation"

      val errors = (json \ "errors").as[Seq[JsValue]]
      errors should have size 1

      val error = errors.head
      (error \ "nino").as[String]          shouldBe "AB000001C"
      (error \ "accountNumber").as[String] shouldBe "STD000001"
      (error \ "code").as[String]          shouldBe "INVALID_DATE_OF_BIRTH"
      (error \ "message").as[String]       shouldBe "Date of birth is not formatted correctly"
    }

    "return 400 for SecondLevelValidationException - invalid DOB & firstName in two different models - should report both errors" in {
      val ndJsonLine =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First","middleName":null,"lastName":"Last1","dateOfBirth":"1980-0-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000002","nino":"AB000002C","firstName":"","middleName":"Middle2","lastName":"Last2","dateOfBirth":"1980-01-03","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":5000.00,"flexibleIsa":true}
          |""".stripMargin

      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockMonthlyReportDocumentService.existsByIsaManagerReferenceAndReturnId(any(), any()))
        .thenReturn(Future.successful(true))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Right((reportingWindow, obligation))))
      when(mockStreamingParserService.processValidatedStream(any()))
        .thenReturn(
          Future.successful(
            Left(
              SecondLevelValidationFailure(
                Seq(
                  SecondLevelValidationError(
                    nino = "AB000001C",
                    accountNumber = "STD000001",
                    code = "INVALID_DATE_OF_BIRTH",
                    message = "Date of birth is not formatted correctly"
                  ),
                  SecondLevelValidationError(
                    nino = "AB000002C",
                    accountNumber = "STD000002",
                    code = "INVALID_FIRST_NAME",
                    message = "First name must not be empty"
                  )
                )
              )
            )
          )
        )

      val result = controller.submit("Z123", validTaxYear, validMonthStr).apply(fakeRequestWithStream(ndJsonLine))
      val json   = contentAsJson(result)

      status(result) shouldBe BAD_REQUEST

      (json \ "code").as[String]    shouldBe "VALIDATION_FAILURE"
      (json \ "message").as[String] shouldBe "One or more models failed validation"

      val errors = (json \ "errors").as[Seq[JsValue]]
      errors should have size 2

      val error1 = errors.find(e => (e \ "nino").as[String] == "AB000001C").get
      (error1 \ "accountNumber").as[String] shouldBe "STD000001"
      (error1 \ "code").as[String]          shouldBe "INVALID_DATE_OF_BIRTH"
      (error1 \ "message").as[String]       shouldBe "Date of birth is not formatted correctly"

      val error2 = errors.find(e => (e \ "nino").as[String] == "AB000002C").get
      (error2 \ "accountNumber").as[String] shouldBe "STD000002"
      (error2 \ "code").as[String]          shouldBe "INVALID_FIRST_NAME"
      (error2 \ "message").as[String]       shouldBe "First name must not be empty"
    }

    "return 401 Unauthorised  when ETMP responds with an unauthorised error" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Left(UnauthorisedErr)))
      val request = FakeRequest("POST", s"/monthly/$isaManagerRef/init")
        .withHeaders("X-Client-ID" -> "client-abc")
        .withBody(validSubmissionJson)

      val result = controller.submit("Z123", validTaxYear, validMonthStr).apply(fakeRequestWithStream())

      status(result)                                 shouldBe UNAUTHORIZED
      (contentAsJson(result) \ "message").as[String] shouldBe "Unauthorised"
      (contentAsJson(result) \ "code").as[String]    shouldBe "UNAUTHORISED"
    }

    "return 403 when ETMP returns ErrorResponse" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockMonthlyReportDocumentService.existsByIsaManagerReferenceAndReturnId(any(), any()))
        .thenReturn(Future.successful(true))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Left(ObligationClosed)))

      val result = controller.submit("Z123", validTaxYear, validMonthStr).apply(fakeRequestWithStream())

      status(result)                                 shouldBe FORBIDDEN
      (contentAsJson(result) \ "code").as[String]    shouldBe "OBLIGATION_CLOSED"
      (contentAsJson(result) \ "message").as[String] shouldBe "Obligation closed"
    }

    "return 500 for unexpected errors" in {
      when(mockAuthConnector.authorise(any, any[Retrieval[Unit]])(any, any)).thenReturn(Future.successful(()))
      when(mockMonthlyReportDocumentService.existsByIsaManagerReferenceAndReturnId(any(), any()))
        .thenReturn(Future.successful(true))
      when(mockETMPService.validateEtmpSubmissionEligibility(any())(any(), any()))
        .thenReturn(Future.successful(Right((reportingWindow, obligation))))
      when(
        mockStreamingParserService.processValidatedStream(any[Source[Either[ValidationError, IsaAccount], _]])
      ).thenReturn(Future.failed(new RuntimeException("boom")))

      val result = controller.submit("Z123", validTaxYear, validMonthStr).apply(fakeRequestWithStream())
      status(result)        shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe Json.toJson(InternalServerErr())
    }
  }
}
