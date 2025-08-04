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

package uk.gov.hmrc.disareturns.controllers

import com.github.tomakehurst.wiremock.client.WireMock.{get, serverError, stubFor, urlEqualTo}
import play.api.http.Status.{BAD_REQUEST, FORBIDDEN, INTERNAL_SERVER_ERROR, NO_CONTENT, OK}
import play.api.libs.json._
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, MultipleErrorResponse, NinoOrAccountNumInvalidErr, NinoOrAccountNumMissingErr, ObligationClosed, ReportingWindowClosed, SecondLevelValidationError, SecondLevelValidationResponse}
import uk.gov.hmrc.disareturns.repositories.ReportingRepository
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec

class ReturnsSubmissionControllerISpec extends BaseIntegrationSpec {

  implicit val mongo: ReportingRepository = app.injector.instanceOf[ReportingRepository]

  val isaManagerRef = "Z123456"
  val returnId      = "return-789"

  val validNdJson =
    """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

  "POST /monthly/:isaManagerRef/:returnId" should {

    "return 204 for successful submission" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val result = initiateRequest(validNdJson)
      result.status shouldBe NO_CONTENT
    }

    "return 400 with correct error response body request body with missing account number" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"nino":"AB000001C","firstName":"First1","lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)
      result.json.as[ErrorResponse] shouldBe NinoOrAccountNumMissingErr

    }

    "return 400 with correct error response body request body with invalid accountNumber" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber": 1.0, "nino": "AB000001C", "firstName":"First1","lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)
      result.json.as[ErrorResponse] shouldBe NinoOrAccountNumInvalidErr

    }

    "return 400 with correct error response body request body with missing nino" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","firstName":"First1","lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)
      result.json.as[ErrorResponse] shouldBe NinoOrAccountNumMissingErr

    }

    "return 400 with correct error response body request body with invalid nino" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino": 123, "firstName":"First1","lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)
      result.json.as[ErrorResponse] shouldBe NinoOrAccountNumInvalidErr

    }

    "return 400 with correct error response body request body with invalid first name" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName": 123,"middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_FIRST_NAME",
            message = "First name must be a valid string"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing first name" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_FIRST_NAME",
            message = "First name field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body request body with invalid middle name" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName": 123,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_MIDDLE_NAME",
            message = "Middle name must be a valid string"
          )
        )
      )
    }

    "return 400 with correct error response body request body with invalid last name" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName": 123,"dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_LAST_NAME",
            message = "Last name must be a valid string"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing last name" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_LAST_NAME",
            message = "Last name field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body request body with invalid DOB" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","lastName":"Last1","dateOfBirth":"1980-0-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_DOB_FORMAT",
            message = "Date of birth must be in YYYY-MM-DD format"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing DOB" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","lastName":"Last1","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_DOB",
            message = "Date of birth field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body request body with invalid ISA type" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"INVALID","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_ISA_TYPE",
            message = "ISA type must be one of: CASH, STOCKS_AND_SHARES, INNOVATIVE_FINANCE, LIFETIME_CASH, or LIFETIME_STOCKS_AND_SHARES"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing ISA type" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_ISA_TYPE",
            message = "ISA type field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body request body with invalid reportingATransfer" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":"true","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_REPORTING_A_TRANSFER_FORMAT",
            message = "Reporting a transfer must be a boolean value (true or false)"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing reportingATransfer" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_REPORTING_A_TRANSFER",
            message = "Reporting a transfer field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body request body with invalid dateOfLastSubscription" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","dateOfLastSubscription":"wrong","reportingATransfer":true,"totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_DATE_OF_LAST_SUBSCRIPTION_FORMAT",
            message = "Date of last subscription must be in YYYY-MM-DD format"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing dateOfLastSubscription" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_DATE_OF_LAST_SUBSCRIPTION",
            message = "Date of last subscription field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body request body with invalid totalCurrentYearSubscriptionsToDate" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.0,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_TOTAL_CURRENT_YEAR_SUBSCRIPTION_TO_DATE",
            message = "Total current year subscriptions to date must be decimal (e.g. 123.45)"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing totalCurrentYearSubscriptionsToDate" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_TOTAL_CURRENT_YEAR_SUBSCRIPTION_TO_DATE",
            message = "Total current year subscription to date field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body request body with invalid marketValueOfAccount" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":"10000.0","accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_MARKET_VALUE_OF_ACCOUNT",
            message = "Market value of account must be decimal (e.g. 123.45)"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing marketValueOfAccount" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_MARKET_VALUE_OF_ACCOUNT",
            message = "Market value of account field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body request body with invalid accountNumberOfTransferringAccount" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":123,"amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_ACCOUNT_NUMBER_OF_TRANSFERRING_ACCOUNT",
            message = "Account number of transferring account must be a valid string"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing accountNumberOfTransferringAccount" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_ACCOUNT_NUMBER_OF_TRANSFERRING_ACCOUNT",
            message = "Account number of transferring account field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body request body with invalid amountTransferred" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":"5000.0","flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_AMOUNT_TRANSFERRED",
            message = "Amount transferred must have 2 decimal places"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing amountTransferred" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_AMOUNT_TRANSFERRED",
            message = "Amount transferred field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body request body with invalid flexibleIsa" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":"false"}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_FLEXIBLE_ISA",
            message = "Flexible ISA must be a boolean value (true or false)"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing flexibleIsa" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_FLEXIBLE_ISA",
            message = "Flexible ISA field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body request body when missing errors displayed across multiple records submitted" in {
      val invalidNdJsonLine1 =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""
      val invalidNdJsonLine2 =
        """{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First2","middleName":null,"dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)
      val result = initiateRequest(invalidNdJsonLine1 + "\n" + invalidNdJsonLine2 + "\n")
      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_FIRST_NAME",
            message = "First name must not be empty"
          ),
          SecondLevelValidationError(
            nino = "AB000002C",
            accountNumber = "STD000002",
            code = "MISSING_LAST_NAME",
            message = "Last name field is missing"
          )
        )
      )
    }

    //TODO: missing headers -> clientId
    //TODO: invalid returnId for Zref - Forbidden/NotFound
    //TODO: return doesn't exist - NotFound

    "return FORBIDDEN if ETMP obligationAlreadyMet check returns true" in {

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> true), isaManagerRef = isaManagerRef)

      val result = initiateRequest(validNdJson)
      result.status                 shouldBe FORBIDDEN
      result.json.as[ErrorResponse] shouldBe ObligationClosed

    }

    "return FORBIDDEN if ETMP reportingWindowOpen check returns false" in {

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> false))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = isaManagerRef)

      val result = initiateRequest(validNdJson)
      result.status                 shouldBe FORBIDDEN
      result.json.as[ErrorResponse] shouldBe ReportingWindowClosed

    }

    "return FORBIDDEN if ETMP reportingWindowOpen check returns false & obligationAlreadyMet check returns true" in {

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> false))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> true), isaManagerRef = isaManagerRef)

      val result = initiateRequest(validNdJson)
      result.status                 shouldBe FORBIDDEN
      result.json.as[ErrorResponse] shouldBe MultipleErrorResponse(errors = Seq(ReportingWindowClosed, ObligationClosed))

    }

    "return 500 Internal Server Error when upstream 503 serviceUnavailable returned from ETMP" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubFor(
        get(urlEqualTo(s"/etmp/check-obligation-status/$isaManagerRef"))
          .willReturn(serverError)
      )
      val result = initiateRequest(validNdJson)

      result.status                        shouldBe INTERNAL_SERVER_ERROR
      (result.json \ "code").as[String]    shouldBe "INTERNAL_SERVER_ERROR"
      (result.json \ "message").as[String] shouldBe "There has been an issue processing your request"
    }
  }

  override val testHeaders: Seq[(String, String)] = Seq(
    "X-Client-ID"   -> testClientId,
    "Authorization" -> "mock-bearer-token",
    "Content-Type"  -> "application/x-ndjson"
  )

  def initiateRequest(
    requestBody:         String,
    headers:             Seq[(String, String)] = testHeaders,
    isaManagerReference: String = isaManagerRef
  ): WSResponse = {
    stubAuth()
    mongo.dropCollection()
    await(
      ws.url(
        s"http://localhost:$port/monthly/$isaManagerReference/$returnId"
      ).withFollowRedirects(follow = false)
        .withHttpHeaders(
          headers: _*
        )
        .post(requestBody + "\n")
    )
  }
}
