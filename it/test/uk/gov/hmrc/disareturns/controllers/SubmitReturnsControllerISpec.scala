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

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status._
import play.api.libs.json._
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec

class SubmitReturnsControllerISpec extends BaseIntegrationSpec {

  val testIsaManagerReference = "Z1234"
  val testTaxYear             = "2026-27"

  override val testHeaders: Seq[(String, String)] = Seq(
    "X-Client-ID"   -> testClientId,
    "Authorization" -> "mock-bearer-token",
    "Content-Type"  -> "application/x-ndjson"
  )

  val validLifetimeIsaSubscription =
    """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":-5000.00,"lisaBonusClaim":5000.00}"""
  val validLifetimeIsaClosure =
    """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":-5000.00,"reasonForClosure":"CANCELLED","closureDate":"2025-06-01"}"""
  val validStandardIsaSubscription =
    """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"flexibleIsa":false}"""
  val validStandardIsaClosure =
    """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"reasonForClosure":"CANCELLED","closureDate":"2025-06-01","flexibleIsa":false}"""

  "POST /monthly/:isaManagerRef/:taxYear/:month" should {

    "return 204 for successful submission - LifetimeIsaSubscription" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      stubNpsSubmission(NO_CONTENT, testIsaManagerReference)

      val result = submitMonthlyReturnRequest(validLifetimeIsaSubscription)
      result.status shouldBe NO_CONTENT
    }

    "return 204 for successful submission - LifetimeIsaClosure" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      stubNpsSubmission(NO_CONTENT, testIsaManagerReference)

      val result = submitMonthlyReturnRequest(validLifetimeIsaClosure)
      result.status shouldBe NO_CONTENT
    }

    "return 204 for successful submission - StandardIsaSubscription" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      stubNpsSubmission(NO_CONTENT, testIsaManagerReference)

      val result = submitMonthlyReturnRequest(validStandardIsaSubscription)
      result.status shouldBe NO_CONTENT
    }

    "return 204 for successful submission - StandardIsaClosure" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      stubNpsSubmission(NO_CONTENT, testIsaManagerReference)

      val result = submitMonthlyReturnRequest(validStandardIsaClosure)
      result.status shouldBe NO_CONTENT
    }
  }

  "POST /monthly/:isaManagerRef/:taxYear/:month path parameter validation checks" should {

    "return 400 with correct error response when an invalid isaManagerReference is provided" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val result = submitMonthlyReturnRequest(isaManagerReference = "Invalid", requestBody = validStandardIsaClosure)
      result.json.as[ErrorResponse] shouldBe InvalidIsaManagerRef
    }

    "return 400 with correct error response when an invalid taxYear is provided" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val result = submitMonthlyReturnRequest(taxYear = "Invalid", requestBody = validStandardIsaClosure)
      result.json.as[ErrorResponse] shouldBe InvalidTaxYear
    }

    "return 400 with correct error response when an invalid month is provided" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val result = submitMonthlyReturnRequest(month = "Invalid", requestBody = validStandardIsaClosure)
      result.json.as[ErrorResponse] shouldBe InvalidMonth
    }

    "return 400 with correct error response when invalid isaManagerRef, taxYear, month are provided" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val result =
        submitMonthlyReturnRequest(isaManagerReference = "Invalid", taxYear = "Invalid", month = "Invalid", requestBody = validStandardIsaClosure)
      result.json
        .as[ErrorResponse] shouldBe MultipleErrorResponse(code = "BAD_REQUEST", errors = Seq(InvalidIsaManagerRef, InvalidTaxYear, InvalidMonth))
    }

    "return 400 with correct error response when invalid taxYear & month are provided" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val result = submitMonthlyReturnRequest(taxYear = "Invalid", month = "Invalid", requestBody = validStandardIsaClosure)
      result.json.as[ErrorResponse] shouldBe MultipleErrorResponse(code = "BAD_REQUEST", errors = Seq(InvalidTaxYear, InvalidMonth))
    }
  }

  "POST /monthly/:isaManagerRef/:taxYear/:month payload validation checks" should {

    "return 400 with correct error response when request body is missing accountNumber" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)
      result.json.as[ErrorResponse] shouldBe NinoOrAccountNumMissingErr

    }

    "return 400 with correct error response body when request body has invalid accountNumber" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":123,"nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)
      result.json.as[ErrorResponse] shouldBe NinoOrAccountNumInvalidErr

    }

    "return 400 with correct error response body when request body has invalid accountNumber that doesn't match the regex" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"=!","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "=!",
            code = "INVALID_ACCOUNT_NUMBER",
            message = "Account number is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response when request body is missing nino" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)
      result.json.as[ErrorResponse] shouldBe NinoOrAccountNumMissingErr
    }

    "return 400 with correct error response body when request body has invalid nino" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":123,"firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)
      result.json.as[ErrorResponse] shouldBe NinoOrAccountNumInvalidErr

    }

    "return 400 with correct single error response body when request body has multiple validation errors but only displays one - invalid first & last name" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"","middleName":null,"lastName":"","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_FIRST_NAME",
            message = "First name is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has invalid nino that doesn't match the regex" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB",
            accountNumber = "STD000001",
            code = "INVALID_NINO",
            message = "Nino is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has invalid first name" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":123,"middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_FIRST_NAME",
            message = "First name is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response when request body is missing first name" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

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

    "return 400 with correct error response body when request body has invalid middle name" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":123,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_MIDDLE_NAME",
            message = "Middle name is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has invalid last name" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":123,"dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_LAST_NAME",
            message = "Last name is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response when request body is missing last name" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"flexibleIsa":false}"""

      val result = submitMonthlyReturnRequest(invalidJson)

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

    "return 400 with correct error response body when request body has invalid DOB - wrong format" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-0-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_DATE_OF_BIRTH",
            message = "Date of birth is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has invalid DOB - JsNumber" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":123,"isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_DATE_OF_BIRTH",
            message = "Date of birth is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response when request body is missing DOB" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_DATE_OF_BIRTH",
            message = "Date of birth field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has invalid ISA type" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"INVALID","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_ISA_TYPE",
            message = "Isa type is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has an invalid standard ISA type for LifetimeIsaClosure" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaClosure =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":-5000.00,"reasonForClosure":"CANCELLED","closureDate":"2025-06-01"}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaClosure)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_ISA_TYPE",
            message = "Isa type is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has an invalid standard ISA type for LifetimeIsaSubscription" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"INNOVATIVE_FINANCE","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_ISA_TYPE",
            message = "Isa type is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has an invalid lifetime ISA type for StandardIsaSubscription" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidStandardIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"flexibleIsa":false}"""

      val result = submitMonthlyReturnRequest(invalidStandardIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_ISA_TYPE",
            message = "Isa type is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has an invalid lifetime ISA type for StandardIsaClosure" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidStandardIsaClosure =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"reasonForClosure":"LIFETIME","closureDate":"2025-06-01","flexibleIsa":false}"""

      val result = submitMonthlyReturnRequest(invalidStandardIsaClosure)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_ISA_TYPE",
            message = "Isa type is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has a missing ISA type for LifetimeIsaClosure" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaClosure =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":-5000.00,"reasonForClosure":"CANCELLED","closureDate":"2025-06-01"}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaClosure)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_ISA_TYPE",
            message = "Isa type field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has a missing ISA type for LifetimeIsaSubscription" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_ISA_TYPE",
            message = "Isa type field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has a missing ISA type for StandardIsaSubscription" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidStandardIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"flexibleIsa":false}"""

      val result = submitMonthlyReturnRequest(invalidStandardIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_ISA_TYPE",
            message = "Isa type field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has a missing ISA type for StandardIsaClosure" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidStandardIsaClosure =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"reasonForClosure":"LIFETIME","closureDate":"2025-06-01","flexibleIsa":false}"""

      val result = submitMonthlyReturnRequest(invalidStandardIsaClosure)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_ISA_TYPE",
            message = "Isa type field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has invalid dateOfLastSubscription" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-1","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""
          .strip()

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_DATE_OF_LAST_SUBSCRIPTION",
            message = "Date of last subscription is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response when request body is missing dateOfLastSubscription" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

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

    "return 400 with correct error response body when request body has invalid dateOfFirstSubscription" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-1","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_DATE_OF_FIRST_SUBSCRIPTION",
            message = "Date of first subscription is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response when request body is missing amountTransferredIn" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-11","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_AMOUNT_TRANSFERRED_IN",
            message = "Amount transferred in field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has invalid amountTransferredIn" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn":2500.0,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-11","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_AMOUNT_TRANSFERRED_IN",
            message = "Amount transferred in is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has a negative amountTransferredIn" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn":-20.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-11","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_AMOUNT_TRANSFERRED_IN",
            message = "Amount transferred in is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response when request body is missing amountTransferredOut" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"dateOfFirstSubscription":"2025-06-11","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_AMOUNT_TRANSFERRED_OUT",
            message = "Amount transferred out field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has invalid amountTransferredOut" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut":2500.0,"dateOfFirstSubscription":"2025-06-11","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_AMOUNT_TRANSFERRED_OUT",
            message = "Amount transferred out is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has a negative amountTransferredOut" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn":20.00,"amountTransferredOut": -2500.00,"dateOfFirstSubscription":"2025-06-11","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_AMOUNT_TRANSFERRED_OUT",
            message = "Amount transferred out is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response when request body is missing dateOfFirstSubscription" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_DATE_OF_FIRST_SUBSCRIPTION",
            message = "Date of first subscription field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has invalid totalCurrentYearSubscriptionsToDate" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":"Invalid","marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_TOTAL_CURRENT_YEAR_SUBSCRIPTIONS_TO_DATE",
            message = "Total current year subscriptions to date is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has a negative totalCurrentYearSubscriptionsToDate" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":"-20.00","marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_TOTAL_CURRENT_YEAR_SUBSCRIPTIONS_TO_DATE",
            message = "Total current year subscriptions to date is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response when request body is missing totalCurrentYearSubscriptionsToDate" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_TOTAL_CURRENT_YEAR_SUBSCRIPTIONS_TO_DATE",
            message = "Total current year subscriptions to date field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has invalid marketValueOfAccount" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.0,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_MARKET_VALUE_OF_ACCOUNT",
            message = "Market value of account is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has a negative marketValueOfAccount" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":-10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_MARKET_VALUE_OF_ACCOUNT",
            message = "Market value of account is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response when request body is missing marketValueOfAccount" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

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

    "return 400 with correct error response body when request body has invalid lisaQualifyingAddition" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":"money","lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_LISA_QUALIFYING_ADDITION",
            message = "Lisa qualifying addition is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response when request body is missing lisaQualifyingAddition" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaBonusClaim":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_LISA_QUALIFYING_ADDITION",
            message = "Lisa qualifying addition field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has invalid lisaBonusClaim" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":"money"}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_LISA_BONUS_CLAIM",
            message = "Lisa bonus claim is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response when request body is missing lisaBonusClaim" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaSubscription)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_LISA_BONUS_CLAIM",
            message = "Lisa bonus claim field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has invalid flexibleIsa" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidStandardIsaClosure =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"reasonForClosure":"CANCELLED","closureDate":"2025-06-01","flexibleIsa":123}""".stripMargin

      val result = submitMonthlyReturnRequest(invalidStandardIsaClosure)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_FLEXIBLE_ISA",
            message = "Flexible isa is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response when request body is missing flexibleIsa" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidStandardIsaClosure =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"reasonForClosure":"CANCELLED","closureDate":"2025-06-01"}"""

      val result = submitMonthlyReturnRequest(invalidStandardIsaClosure)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_FLEXIBLE_ISA",
            message = "Flexible isa field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has invalid closureDate" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidStandardIsaClosure =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"reasonForClosure":"CANCELLED","closureDate":"2025-06-1","flexibleIsa":false}"""

      val result = submitMonthlyReturnRequest(invalidStandardIsaClosure)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_CLOSURE_DATE",
            message = "Closure date is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response when request body is missing closureDate" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidStandardIsaClosure =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"reasonForClosure":"CANCELLED","flexibleIsa":false}"""

      val result = submitMonthlyReturnRequest(invalidStandardIsaClosure)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_CLOSURE_DATE",
            message = "Closure date field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has invalid reasonForClosure" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidStandardIsaClosure =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"reasonForClosure":"INVALID","closureDate":"2025-06-01","flexibleIsa":false}"""

      val result = submitMonthlyReturnRequest(invalidStandardIsaClosure)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_REASON_FOR_CLOSURE",
            message = "Reason for closure is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has invalid ALL_FUNDS_WITHDRAWN reasonForClosure for a standard ISA" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidStandardIsaClosure =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"reasonForClosure":"ALL_FUNDS_WITHDRAWN","closureDate":"2025-06-01","flexibleIsa":false}"""

      val result = submitMonthlyReturnRequest(invalidStandardIsaClosure)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_REASON_FOR_CLOSURE",
            message = "Reason for closure is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has invalid TRANSFERRED_IN_FULL reasonForClosure for a standard ISA" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidStandardIsaClosure =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"reasonForClosure":"TRANSFERRED_IN_FULL","closureDate":"2025-06-01","flexibleIsa":false}"""

      val result = submitMonthlyReturnRequest(invalidStandardIsaClosure)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_REASON_FOR_CLOSURE",
            message = "Reason for closure is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body when request body has invalid reasonForClosure for a Lifetime ISA" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidLifetimeIsaClosure =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfFirstSubscription":"2025-06-01","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"lisaQualifyingAddition":5000.00,"lisaBonusClaim":-5000.00,"reasonForClosure":"INVALID","closureDate":"2025-06-01"}"""

      val result = submitMonthlyReturnRequest(invalidLifetimeIsaClosure)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_REASON_FOR_CLOSURE",
            message = "Reason for closure is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response when request body is missing reasonForClosure" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidStandardIsaClosure =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"closureDate":"2025-06-01","flexibleIsa":false}"""

      val result = submitMonthlyReturnRequest(invalidStandardIsaClosure)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "MISSING_REASON_FOR_CLOSURE",
            message = "Reason for closure field is missing"
          )
        )
      )
    }

    "return 400 with correct error response when request payload is missing/invalid fields on each submitted ISA account" in {
      val invalidStandardIsaClosure1 =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":123,"middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"reasonForClosure":"CANCELLED","closureDate":"2025-06-01","flexibleIsa":false}"""
      val invalidStandardIsaClosure2 =
        """{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First1","middleName":null,"dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"reasonForClosure":"CANCELLED","closureDate":"2025-06-01","flexibleIsa":false}"""

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val result = submitMonthlyReturnRequest(invalidStandardIsaClosure1 + "\n" + invalidStandardIsaClosure2 + "\n")
      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_FIRST_NAME",
            message = "First name is not formatted correctly"
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

    "return 400 with correct error response when first NDJSON line has second-level field validation error & second NDJSON line has invalid nino (first level validation) error" in {
      val invalidStandardIsaClosure1 =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":123,"middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"reasonForClosure":"CANCELLED","closureDate":"2025-06-01","flexibleIsa":false}"""
      val invalidStandardIsaClosure2 =
        """{"accountNumber":"STD000002","nino":233,"firstName":"First1","middleName":null, "lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"reasonForClosure":"CANCELLED","closureDate":"2025-06-01","flexibleIsa":false}"""

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val result = submitMonthlyReturnRequest(invalidStandardIsaClosure1 + "\n" + invalidStandardIsaClosure2 + "\n")
      result.status                 shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe NinoOrAccountNumInvalidErr
    }

    "return 400 with correct error response when first NDJSON line has second-level field validation error & second NDJSON line has missing nino (first level validation) error" in {
      val invalidStandardIsaClosure1 =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":123,"middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"reasonForClosure":"CANCELLED","closureDate":"2025-06-01","flexibleIsa":false}"""
      val invalidStandardIsaClosure2 =
        """{"accountNumber":"STD000002","firstName":"First1","middleName":null, "lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"reasonForClosure":"CANCELLED","closureDate":"2025-06-01","flexibleIsa":false}"""

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val result = submitMonthlyReturnRequest(invalidStandardIsaClosure1 + "\n" + invalidStandardIsaClosure2 + "\n")
      result.status                 shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe NinoOrAccountNumMissingErr
    }

    "return 400 with correct error response when duplicate nino fields are provided in a single IsaAccount" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidStandardIsaClosure =
        """{"accountNumber":"STD000001","nino":"AB000001C","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"reasonForClosure":"CANCELLED","closureDate":"2025-06-01","flexibleIsa":false}"""

      val result = submitMonthlyReturnRequest(invalidStandardIsaClosure)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "DUPLICATE_NINO",
            message = "Duplicate Nino field detected in this record"
          )
        )
      )
    }

    "return 400 with correct error response body request body when json is malformed" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":MALFORMEDJSON,"nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"dateOfFirstSubscription":"2025-06-01","closureDate":"2025-06-01", "lisaQualifyingAddition":"10000.00", "lisaBonusClaim":"10000.00"}"""

      val result = submitMonthlyReturnRequest(invalidJson)

      result.status                 shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe MalformedJsonFailureErr
    }


    "return 400 with correct error response when payload NDJSON lines are not separated by a newline delimiter " in {

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val result = submitMonthlyReturnRequest(validStandardIsaClosure + validStandardIsaSubscription)
      result.status                 shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe MalformedJsonFailureErr
    }

    "return 400 with correct error response when payload NDJSON lines have non-whitespace trailing tokens" in {

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val result = submitMonthlyReturnRequest(validStandardIsaClosure + ";dlafj")
      result.status                 shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe MalformedJsonFailureErr
    }
    //TODO: This currently returns 500 InternalServerError, should return 200. This should pass after bug fixed in DFI-1365
//    "return 400 with correct error response when NDJSON payload does not end with a newline delimiter " in {
//      stubAuth()
//      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
//      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
//      val result = await(
//        ws.url(
//            s"http://localhost:$port/monthly/$testIsaManagerReference/$testTaxYear/JAN"
//          ).withFollowRedirects(follow = false)
//          .withHttpHeaders(
//            testHeaders: _*
//          )
//          .post(validLifetimeIsaClosure)
//      )
//      result.status                 shouldBe BAD_REQUEST
//      result.json.as[ErrorResponse] shouldBe MalformedJsonFailureErr
//    }

    "return 400 with correct error response body when NDJSON payload is empty" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)

      val result = submitMonthlyReturnRequest(requestBody = "")

      result.status                 shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe EmptyPayload
    }
  }

  "POST /monthly/:isaManagerRef/:taxYear/:month error handling" should {

    "return UNAUTHORISED if auth checks fail" in {
      val result = submitMonthlyReturnRequest(validStandardIsaClosure, withAuth = false)
      result.status                 shouldBe UNAUTHORIZED
      result.json.as[ErrorResponse] shouldBe UnauthorisedErr
    }

    "return FORBIDDEN if ETMP obligationAlreadyMet check returns true" in {

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> true), isaManagerRef = testIsaManagerReference)

      val result = submitMonthlyReturnRequest(validStandardIsaClosure)
      result.status                 shouldBe FORBIDDEN
      result.json.as[ErrorResponse] shouldBe ObligationClosed

    }

    "return FORBIDDEN if ETMP reportingWindowOpen check returns false" in {

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> false))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)

      val result = submitMonthlyReturnRequest(validStandardIsaClosure)
      result.status                 shouldBe FORBIDDEN
      result.json.as[ErrorResponse] shouldBe ReportingWindowClosed

    }

    "return FORBIDDEN if ETMP reportingWindowOpen check returns false & obligationAlreadyMet check returns true" in {

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> false))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> true), isaManagerRef = testIsaManagerReference)

      val result = submitMonthlyReturnRequest(validStandardIsaClosure)

      result.status                 shouldBe FORBIDDEN
      result.json.as[ErrorResponse] shouldBe MultipleErrorResponse(code = "FORBIDDEN", errors = Seq(ReportingWindowClosed, ObligationClosed))
    }

    "return 500 Internal Server Error when upstream 503 serviceUnavailable returned from ETMP" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubFor(
        get(urlEqualTo(s"/etmp/check-obligation-status/$testIsaManagerReference"))
          .willReturn(serverError)
      )
      val result = submitMonthlyReturnRequest(validStandardIsaClosure)

      result.status shouldBe INTERNAL_SERVER_ERROR
      result.json   shouldBe Json.toJson(InternalServerErr())
    }

    "return 500 Internal Server Error when upstream 503 serviceUnavailable returned from NPS" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      stubFor(
        post(urlEqualTo(s"/nps/submit/$testIsaManagerReference"))
          .willReturn(serverError)
      )
      val result = submitMonthlyReturnRequest(validStandardIsaClosure)

      result.status shouldBe INTERNAL_SERVER_ERROR
      result.json   shouldBe Json.toJson(InternalServerErr())
    }

    "return 500 Internal Server Error when upstream unexpected status returned from NPS" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      stubFor(
        post(urlEqualTo(s"/nps/submit/$testIsaManagerReference"))
          .willReturn(created)
      )
      val result = submitMonthlyReturnRequest(validStandardIsaClosure)

      result.status shouldBe INTERNAL_SERVER_ERROR
      result.json   shouldBe Json.toJson(InternalServerErr())
    }
  }

  def submitMonthlyReturnRequest(
    requestBody:         String,
    headers:             Seq[(String, String)] = testHeaders,
    isaManagerReference: String = testIsaManagerReference,
    taxYear:             String = testTaxYear,
    month:               String = Month.SEP.toString,
    withAuth:            Boolean = true
  ): WSResponse = {
    if (withAuth) stubAuth() else stubAuthFail()
    await(
      ws.url(
        s"http://localhost:$port/monthly/$isaManagerReference/$taxYear/$month"
      ).withFollowRedirects(follow = false)
        .withHttpHeaders(
          headers: _*
        )
        .post(requestBody + "\n")
    )
  }
}
