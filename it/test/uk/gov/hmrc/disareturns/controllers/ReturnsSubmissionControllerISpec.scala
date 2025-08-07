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
import org.mongodb.scala.model.Filters
import play.api.http.Status._
import play.api.libs.json._
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.initiate.inboundRequest.{SubmissionRequest, TaxYear}
import uk.gov.hmrc.disareturns.models.initiate.mongo.ReturnMetadata
import uk.gov.hmrc.disareturns.models.submission.isaAccounts._
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec

import java.time.LocalDate

class ReturnsSubmissionControllerISpec extends BaseIntegrationSpec {

  val testIsaManagerReference = "Z123456"
  val testReturnId            = "return-789"

  def checkMongoRepository(): Option[MonthlyReportDocument] =
    await(
      reportingMetadataRepository.collection
        .find(
          Filters.and(
            Filters.equal("isaManagerReferenceNumber", testIsaManagerReference),
            Filters.equal("returnId", testReturnId)
          )
        )
        .headOption()
    )

  val validNdJson =
    """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

  "POST /monthly/:isaManagerRef/:returnId" should {

    "return 204 for successful submission - LifetimeIsaNewSubscription" in {
      val validLifetimeIsaNewSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME_CASH","reportingATransfer":false,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"dateOfFirstSubscription":"2025-06-01","lisaQualifyingAddition":5000.00,"lisaBonusClaim":5000.00}"""

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val result = initiateRequest(validLifetimeIsaNewSubscription)
      result.status shouldBe NO_CONTENT

      checkMongoRepository().map(_.isaReport) shouldBe Some(
        Seq(
          LifetimeIsaNewSubscription(
            accountNumber = "STD000001",
            nino = "AB000001C",
            firstName = "First1",
            middleName = None,
            lastName = "Last1",
            dateOfBirth = LocalDate.of(1980, 1, 2),
            isaType = IsaType.LIFETIME_CASH,
            reportingATransfer = false,
            dateOfFirstSubscription = LocalDate.of(2025, 6, 1),
            dateOfLastSubscription = LocalDate.of(2025, 6, 1),
            totalCurrentYearSubscriptionsToDate = 2500.00,
            marketValueOfAccount = 10000.00,
            lisaQualifyingAddition = 5000.00,
            lisaBonusClaim = 5000.00
          )
        )
      )
    }

    "return 204 for successful submission - LifetimeIsaClosure" in {
      val validLifetimeIsaClosure =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME_CASH","reportingATransfer":false,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"dateOfFirstSubscription":"2025-06-01","closureDate":"2025-06-01","reasonForClosure":"CANCELLED","lisaQualifyingAddition":10000.00,"lisaBonusClaim":10000.00}"""

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val result = initiateRequest(validLifetimeIsaClosure)
      result.status shouldBe NO_CONTENT

      checkMongoRepository().map(_.isaReport) shouldBe Some(
        Seq(
          LifetimeIsaClosure(
            accountNumber = "STD000001",
            nino = "AB000001C",
            firstName = "First1",
            middleName = None,
            lastName = "Last1",
            dateOfBirth = LocalDate.parse("1980-01-02"),
            isaType = IsaType.LIFETIME_CASH,
            reportingATransfer = false,
            dateOfLastSubscription = LocalDate.parse("2025-06-01"),
            totalCurrentYearSubscriptionsToDate = BigDecimal(2500.00),
            marketValueOfAccount = BigDecimal(10000.00),
            dateOfFirstSubscription = LocalDate.parse("2025-06-01"),
            closureDate = LocalDate.parse("2025-06-01"),
            reasonForClosure = ReasonForClosure.CANCELLED,
            lisaQualifyingAddition = BigDecimal(10000.00),
            lisaBonusClaim = BigDecimal(10000.00)
          )
        )
      )
    }

    "return 204 for successful submission - LifetimeIsaTransfer" in {
      val validLifetimeIsaTransfer =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME_CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00, "accountNumberOfTransferringAccount": "123456","dateOfFirstSubscription":"2025-06-01","amountTransferred":"10001.00", "lisaQualifyingAddition":"10000.00", "lisaBonusClaim":"10000.00"}"""

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val result = initiateRequest(validLifetimeIsaTransfer)
      result.status shouldBe NO_CONTENT

      checkMongoRepository().map(_.isaReport) shouldBe Some(
        Seq(
          LifetimeIsaTransfer(
            accountNumber = "STD000001",
            nino = "AB000001C",
            firstName = "First1",
            middleName = None,
            lastName = "Last1",
            dateOfBirth = LocalDate.parse("1980-01-02"),
            isaType = IsaType.LIFETIME_CASH,
            reportingATransfer = true,
            dateOfFirstSubscription = LocalDate.parse("2025-06-01"),
            dateOfLastSubscription = LocalDate.parse("2025-06-01"),
            totalCurrentYearSubscriptionsToDate = BigDecimal(2500.00),
            marketValueOfAccount = BigDecimal(10000.00),
            accountNumberOfTransferringAccount = "123456",
            amountTransferred = BigDecimal(10001.00),
            lisaQualifyingAddition = BigDecimal(10000.00),
            lisaBonusClaim = BigDecimal(10000.00)
          )
        )
      )
    }

    "return 204 for successful submission - LifetimeIsaTransferAndClosure" in {
      val validLifetimeIsaTransferAndClosure =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME_CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00, "accountNumberOfTransferringAccount": "123456","dateOfFirstSubscription":"2025-06-01","amountTransferred":"10001.00", "lisaQualifyingAddition":"10000.00", "lisaBonusClaim":"10000.00", "closureDate":"2025-06-01", "reasonForClosure":"CANCELLED"}"""
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val result = initiateRequest(validLifetimeIsaTransferAndClosure)
      result.status shouldBe NO_CONTENT

      checkMongoRepository().map(_.isaReport) shouldBe Some(
        Seq(
          LifetimeIsaTransferAndClosure(
            accountNumber = "STD000001",
            nino = "AB000001C",
            firstName = "First1",
            middleName = None,
            lastName = "Last1",
            dateOfBirth = LocalDate.parse("1980-01-02"),
            isaType = IsaType.LIFETIME_CASH,
            reportingATransfer = true,
            dateOfFirstSubscription = LocalDate.parse("2025-06-01"),
            dateOfLastSubscription = LocalDate.parse("2025-06-01"),
            closureDate = LocalDate.parse("2025-06-01"),
            totalCurrentYearSubscriptionsToDate = BigDecimal(2500.00),
            marketValueOfAccount = BigDecimal(10000.00),
            accountNumberOfTransferringAccount = "123456",
            amountTransferred = BigDecimal(10001.00),
            reasonForClosure = ReasonForClosure.CANCELLED,
            lisaQualifyingAddition = BigDecimal(10000.00),
            lisaBonusClaim = BigDecimal(10000.00)
          )
        )
      )
    }

    "return 204 for successful submission - StandardIsaNewSubscription" in {
      val validStandardIsaNewSubscription =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":false,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00, "flexibleIsa":true}"""

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val result = initiateRequest(validStandardIsaNewSubscription)
      result.status shouldBe NO_CONTENT

      checkMongoRepository().map(_.isaReport) shouldBe Some(
        Seq(
          StandardIsaNewSubscription(
            accountNumber = "STD000001",
            nino = "AB000001C",
            firstName = "First1",
            middleName = None,
            lastName = "Last1",
            dateOfBirth = LocalDate.parse("1980-01-02"),
            isaType = IsaType.STOCKS_AND_SHARES,
            reportingATransfer = false,
            dateOfLastSubscription = LocalDate.parse("2025-06-01"),
            totalCurrentYearSubscriptionsToDate = BigDecimal(2500.00),
            marketValueOfAccount = BigDecimal(10000.00),
            flexibleIsa = true
          )
        )
      )
    }

    "return 204 for successful submission - StandardIsaTransfer" in {
      val validStandardIsaTransfer =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00, "accountNumberOfTransferringAccount": "12345", "amountTransferred": 10000.00, "flexibleIsa":true}"""

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val result = initiateRequest(validStandardIsaTransfer)
      result.status shouldBe NO_CONTENT

      checkMongoRepository().map(_.isaReport) shouldBe Some(
        Seq(
          StandardIsaTransfer(
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
            accountNumberOfTransferringAccount = "12345",
            amountTransferred = BigDecimal(10000.00),
            flexibleIsa = true
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing accountNumber" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"nino":"AB000001C","firstName":"First1","lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)
      result.json.as[ErrorResponse] shouldBe NinoOrAccountNumMissingErr

    }

    "return 400 with correct error response body request body with invalid accountNumber" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber": 1.0, "nino": "AB000001C", "firstName":"First1","lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)
      result.json.as[ErrorResponse] shouldBe NinoOrAccountNumInvalidErr

    }

    "return 400 with correct error response body request body with missing nino" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","firstName":"First1","lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)
      result.json.as[ErrorResponse] shouldBe NinoOrAccountNumMissingErr

    }

    "return 400 with correct error response body request body with invalid nino" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino": 123, "firstName":"First1","lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)
      result.json.as[ErrorResponse] shouldBe NinoOrAccountNumInvalidErr

    }

    "return 400 with correct error response body request body with invalid first name" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
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
            message = "First name is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing first name" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
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
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
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
            message = "Middle name is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body request body with invalid last name" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
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
            message = "Last name is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing last name" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
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
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","lastName":"Last1","dateOfBirth":"1980-0-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

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

    "return 400 with correct error response body request body with missing DOB" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","lastName":"Last1","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

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

    "return 400 with correct error response body request body with invalid ISA type" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
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
            message = "Isa type is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing ISA type" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
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
            message = "Isa type field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body request body with invalid reportingATransfer" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer": 123,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_REPORTING_A_TRANSFER",
            message = "Reporting a transfer is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing reportingATransfer" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
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
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","dateOfLastSubscription":"wrong","reportingATransfer":true,"totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

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

    "return 400 with correct error response body request body with missing dateOfLastSubscription" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
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

    "return 400 with correct error response body request body with invalid dateOfFirstSubscription" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME_CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00, "accountNumberOfTransferringAccount": "123456","dateOfFirstSubscription":"12-06-01","amountTransferred":"10001.00", "lisaQualifyingAddition":"10000.00", "lisaBonusClaim":"10000.00", "closureDate":"2025-06-01", "reasonForClosure":"CANCELLED"}"""

      val result = initiateRequest(invalidJson)

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

    "return 400 with correct error response body request body with missing dateOfFirstSubscription" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME_CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00, "accountNumberOfTransferringAccount": "123456","amountTransferred":"10001.00", "lisaQualifyingAddition":"10000.00", "lisaBonusClaim":"10000.00", "closureDate":"2025-06-01", "reasonForClosure":"CANCELLED"}"""

      val result = initiateRequest(invalidJson)

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

    "return 400 with correct error response body request body with invalid totalCurrentYearSubscriptionsToDate" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.0,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_TOTAL_CURRENT_YEAR_SUBSCRIPTIONS_TO_DATE",
            message = "Total current year subscriptions to date must have 2 decimal places (e.g. 123.45)"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing totalCurrentYearSubscriptionsToDate" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

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

    "return 400 with correct error response body request body with invalid marketValueOfAccount" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
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
            message = "Market value of account must have 2 decimal places (e.g. 123.45)"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing marketValueOfAccount" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
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
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
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
            message = "Account number of transferring account is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing accountNumberOfTransferringAccount" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
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

    "return 400 with correct error response body request body with incorrect .00 decimal place amountTransferred" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
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
            message = "Amount transferred must have 2 decimal places (e.g. 123.45)"
          )
        )
      )
    }

    "return 400 with correct error response body request body with invalid amountTransferred" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":"fail","flexibleIsa":false}"""

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000001C",
            accountNumber = "STD000001",
            code = "INVALID_AMOUNT_TRANSFERRED",
            message = "Amount transferred is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing amountTransferred" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
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
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First2","middleName":"Lee","lastName":"Last2","dateOfBirth":"1975-05-20","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-07-15","totalCurrentYearSubscriptionsToDate":1500.00,"marketValueOfAccount":7500.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":3000.00,"flexibleIsa":"123"}""".stripMargin

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000002C",
            accountNumber = "STD000002",
            code = "INVALID_FLEXIBLE_ISA",
            message = "Flexible isa is not formatted correctly"
          )
        )
      )
    }

    "return 400 with correct error response body request body with missing flexibleIsa" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First2","middleName":"Lee","lastName":"Last2","dateOfBirth":"1975-05-20","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-07-15","totalCurrentYearSubscriptionsToDate":1500.00,"marketValueOfAccount":7500.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":3000.00}""".stripMargin

      val result = initiateRequest(invalidJson)

      result.status shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe SecondLevelValidationResponse(
        errors = Seq(
          SecondLevelValidationError(
            nino = "AB000002C",
            accountNumber = "STD000002",
            code = "MISSING_FLEXIBLE_ISA",
            message = "Flexible isa field is missing"
          )
        )
      )
    }

    "return 400 with correct error response body request body with invalid lisaQualifyingAddition" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME_CASH","reportingATransfer":false,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"dateOfFirstSubscription":"2025-06-01","lisaQualifyingAddition":"money", "lisaBonusClaim":5000.00}"""

      val result = initiateRequest(invalidJson)

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

    "return 400 with correct error response body request body with missing lisaQualifyingAddition" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME_CASH","reportingATransfer":false,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"dateOfFirstSubscription":"2025-06-01","lisaBonusClaim":5000.00}"""

      val result = initiateRequest(invalidJson)

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

    "return 400 with correct error response body request body with invalid lisaBonusClaim" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME_CASH","reportingATransfer":false,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"dateOfFirstSubscription":"2025-06-01","lisaQualifyingAddition":"5000.00", "lisaBonusClaim":"money"}"""

      val result = initiateRequest(invalidJson)

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

    "return 400 with correct error response body request body with missing lisaBonusClaim" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME_CASH","reportingATransfer":false,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"dateOfFirstSubscription":"2025-06-01","lisaQualifyingAddition":5000.00}"""

      val result = initiateRequest(invalidJson)

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

    "return 400 with correct error response body request body with invalid closureDate" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson1 =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME_CASH","reportingATransfer":false,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"dateOfFirstSubscription":"2025-06-01","closureDate":"11-06-01","reasonForClosure":"CANCELLED", "lisaQualifyingAddition":"10000.00", "lisaBonusClaim":"10000.00"}"""

      val result = initiateRequest(invalidJson1)

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

    "return 400 with correct error response body request body with missing closureDate" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME_CASH","reportingATransfer":false,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"dateOfFirstSubscription":"2025-06-01","reasonForClosure":"CANCELLED", "lisaQualifyingAddition":"10000.00", "lisaBonusClaim":"10000.00"}"""

      val result = initiateRequest(invalidJson)

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

    "return 400 with correct error response body request body with invalid reasonForClosure" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME_CASH","reportingATransfer":false,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"dateOfFirstSubscription":"2025-06-01","closureDate":"2025-06-01","reasonForClosure":123, "lisaQualifyingAddition":"10000.00", "lisaBonusClaim":"10000.00"}"""

      val result = initiateRequest(invalidJson)

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

    "return 400 with correct error response body request body with missing reasonForClosure" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"LIFETIME_CASH","reportingATransfer":false,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"dateOfFirstSubscription":"2025-06-01","closureDate":"2025-06-01", "lisaQualifyingAddition":"10000.00", "lisaBonusClaim":"10000.00"}"""

      val result = initiateRequest(invalidJson)

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

    "return 400 with correct error response body request body when json is malformed" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
      val invalidJson =
        """{"accountNumber":WRONGJSON,"nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":false,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"dateOfFirstSubscription":"2025-06-01","closureDate":"2025-06-01", "lisaQualifyingAddition":"10000.00", "lisaBonusClaim":"10000.00"}"""

      val result = initiateRequest(invalidJson)

      result.status                 shouldBe BAD_REQUEST
      result.json.as[ErrorResponse] shouldBe MalformedJsonFailureErr
    }

    "return 400 with correct error response body request body when missing errors displayed across multiple records submitted" in {
      val invalidNdJsonLine1 =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""
      val invalidNdJsonLine2 =
        """{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First2","middleName":null,"dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}"""

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)
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

    "return NOT_FOUND with correct error message if not ReturnMetaData exists" in {
      val result = initiateRequest(requestBody = validNdJson, withReturnMetaData = false)
      result.status                 shouldBe NOT_FOUND
      result.json.as[ErrorResponse] shouldBe ReturnIdNotMatchedErr
    }

    "return NOT_FOUND with correct error message if returnId matches in mongo but isaManagerRef does not" in {
      val result = initiateRequest(requestBody = validNdJson, isaManagerReference = "incorrect-isa-ref")
      result.status                 shouldBe NOT_FOUND
      result.json.as[ErrorResponse] shouldBe ReturnIdNotMatchedErr

    }

    "return NOT_FOUND with correct error message if isaManagerRef matches in mongo but returnId does not" in {
      val result = initiateRequest(requestBody = validNdJson, returnId = "incorrect-return-id")
      result.status                 shouldBe NOT_FOUND
      result.json.as[ErrorResponse] shouldBe ReturnIdNotMatchedErr
    }

    "return NOT_FOUND with correct error message if neither isaManagerRef or returned matches in mongo" in {
      val result = initiateRequest(requestBody = validNdJson, isaManagerReference = "incorrect-isa-ref", returnId = "incorrect-return-id")
      result.status                 shouldBe NOT_FOUND
      result.json.as[ErrorResponse] shouldBe ReturnIdNotMatchedErr
    }

    "return FORBIDDEN if ETMP obligationAlreadyMet check returns true" in {

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> true), isaManagerRef = testIsaManagerReference)

      val result = initiateRequest(validNdJson)
      result.status                 shouldBe FORBIDDEN
      result.json.as[ErrorResponse] shouldBe ObligationClosed

    }

    "return FORBIDDEN if ETMP reportingWindowOpen check returns false" in {

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> false))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false), isaManagerRef = testIsaManagerReference)

      val result = initiateRequest(validNdJson)
      result.status                 shouldBe FORBIDDEN
      result.json.as[ErrorResponse] shouldBe ReportingWindowClosed

    }

    "return FORBIDDEN if ETMP reportingWindowOpen check returns false & obligationAlreadyMet check returns true" in {

      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> false))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> true), isaManagerRef = testIsaManagerReference)

      val result = initiateRequest(validNdJson)
      result.status                 shouldBe FORBIDDEN
      result.json.as[ErrorResponse] shouldBe MultipleErrorResponse(errors = Seq(ReportingWindowClosed, ObligationClosed))

    }

    "return 500 Internal Server Error when upstream 503 serviceUnavailable returned from ETMP" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubFor(
        get(urlEqualTo(s"/etmp/check-obligation-status/$testIsaManagerReference"))
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
    isaManagerReference: String = testIsaManagerReference,
    returnId:            String = testReturnId,
    withReturnMetaData:  Boolean = true
  ): WSResponse = {
    stubAuth()
    await(returnMetadataRepository.dropCollection())
    await(reportingMetadataRepository.dropCollection())
    if (withReturnMetaData) setupReturnMetadataRepository()
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

  def setupReturnMetadataRepository(): String =
    await(
      returnMetadataRepository.insert(
        ReturnMetadata(
          returnId = testReturnId,
          boxId = "box-id",
          submissionRequest = SubmissionRequest(totalRecords = 1000, submissionPeriod = Month.JAN, taxYear = TaxYear(LocalDate.now.getYear)),
          isaManagerReference = testIsaManagerReference
        )
      )
    )
}
