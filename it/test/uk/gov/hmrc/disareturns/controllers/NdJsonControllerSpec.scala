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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, status}
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaAccount
import uk.gov.hmrc.disareturns.mongoRepositories.ReportingRepository

import scala.concurrent.Future

// MOVE TO UNIT test folder
class NdJsonControllerSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with GuiceOneServerPerSuite
      with BeforeAndAfterEach {

  private val controller = app.injector.instanceOf[NdJsonController]
  lazy val reportingRepository: ReportingRepository = app.injector.instanceOf[ReportingRepository]
  implicit val materializer: Materializer = app.materializer

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(reportingRepository.dropCollection())
  }


  "NdJsonController" should {
    "parse valid NDJSON and return count" in {

      val ndjson =
        """{"isaAmount": 1000, "id": 1, "isaManager": "ManagerA"}
          |{"transferAmount": 500, "id": 2, "isaManager": "ManagerB"}
          |{"isEligibleForBonus": true, "id": 3, "isaManager": "ManagerC"}
          |{"isaAmount": 1200, "id": 4, "isaManager": "ManagerD"}
          |{"transferAmount": 300, "id": 5, "isaManager": "ManagerE"}
          |{"isEligibleForBonus": false, "id": 6, "isaManager": "ManagerF"}
          |{"isaAmount": 1500, "id": 7, "isaManager": "ManagerG"}
          |{"transferAmount": 800, "id": 8, "isaManager": "ManagerH"}
          |{"isEligibleForBonus": true, "id": 9, "isaManager": "ManagerI"}
          |{"isaAmount": 1100, "id": 10, "isaManager": "ManagerJ"}
          |{"transferAmount": 200, "id": 11, "isaManager": "ManagerK"}
          |{"isEligibleForBonus": false, "id": 12, "isaManager": "ManagerL"}
          |{"isaAmount": 1300, "id": 13, "isaManager": "ManagerM"}
          |{"transferAmount": 400, "id": 14, "isaManager": "ManagerN"}
          |{"isEligibleForBonus": true, "id": 15, "isaManager": "ManagerO"}
          |{"isaAmount": 900, "id": 16, "isaManager": "ManagerP"}
          |{"transferAmount": 600, "id": 17, "isaManager": "ManagerQ"}
          |{"isEligibleForBonus": false, "id": 18, "isaManager": "ManagerR"}
          |{"isaAmount": 1400, "id": 19, "isaManager": "ManagerS"}
          |{"transferAmount": 550, "id": 20, "isaManager": "ManagerT"}""".stripMargin


      val request = FakeRequest("POST", "/ndjson").withBody(ndjson)
        .withHeaders("Content-Type" -> "application/x-ndjson")

      val result = controller.uploadNdjsonStream()(request)

      status(result) shouldBe OK
      contentAsString(result) should include("NDJSON streamed successfully")
    }
  }

  "NdJsonController#uploadNdjsonStreamWithMongo" should {

    "stream and parse valid NDJSON and insert into Mongo" in {
      lazy val isaManagerId = "some-manager-1"
      lazy val returnId = "some-return-1"

      val reports =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First2","middleName":"Middle2","lastName":"Last2","dateOfBirth":"1980-01-03","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000003","nino":"AB000003C","firstName":"First3","middleName":null,"lastName":"Last3","dateOfBirth":"1980-01-04","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000003","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000004","nino":"AB000004C","firstName":"First4","middleName":"Middle4","lastName":"Last4","dateOfBirth":"1980-01-05","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000004","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000005","nino":"AB000005C","firstName":"First5","middleName":null,"lastName":"Last5","dateOfBirth":"1980-01-06","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000005","amountTransferred":5000.00,"flexibleIsa":false}
          |""".stripMargin

      val lines = reports.split("\n").toList.map(line => ByteString(line + "\n"))
      val body: Source[ByteString, _] = Source(lines)

      val request = FakeRequest()
        .withHeaders("Content-Type" -> "application/x-ndjson")
        .withBody(body)

      val result: Future[Result] = controller.uploadNdjsonStreamWithMongo(isaManagerId, returnId)(request)

      status(result) shouldBe OK
      contentAsString(result) should include("Inserted 5 reports into MongoDB")
    }

    "return BadRequest on malformed NDJSON" in {
      val isaManagerId = "bad-manager-id"
      lazy val returnId = "bad-return-id"

      val invalidJson =
        """{"isaAmount": 1000, "id": 1, "isaManager": "ManagerA"}
          |{invalid-json
          |{"isaAmount": 3000, "id": 3, "isaManager": "ManagerC"}""".stripMargin

      val lines = invalidJson.split("\n").toList.map(line => ByteString(line + "\n"))
      val body: Source[ByteString, _] = Source(lines)

      val request = FakeRequest()
        .withHeaders("Content-Type" -> "application/x-ndjson")
        .withBody(body)

      val result: Future[Result] = controller.uploadNdjsonStreamWithMongo(isaManagerId, returnId)(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("Error processing NDJSON")
    }
  }

  "NdJsonController#uploadNdjsonToMongoWithStream" should {

    "insert 5 records into one mongo document" in {
      lazy val isaManagerId = "some-manager-2"
      lazy val returnId = "some-return-2"
      val reports =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First2","middleName":"Middle2","lastName":"Last2","dateOfBirth":"1980-01-03","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000003","nino":"AB000003C","firstName":"First3","middleName":null,"lastName":"Last3","dateOfBirth":"1980-01-04","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000003","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000004","nino":"AB000004C","firstName":"First4","middleName":"Middle4","lastName":"Last4","dateOfBirth":"1980-01-05","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000004","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000005","nino":"AB000005C","firstName":"First5","middleName":null,"lastName":"Last5","dateOfBirth":"1980-01-06","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000005","amountTransferred":5000.00,"flexibleIsa":false}
          |""".stripMargin

      val lines = reports.split("\n").toList.map(line => ByteString(line + "\n"))
      val body: Source[ByteString, _] = Source(lines)

      val request = FakeRequest()
        .withHeaders("Content-Type" -> "application/x-ndjson")
        .withBody(body)

      val result: Future[Result] = controller.uploadNdjsonWithStreamIntoMongo(isaManagerId, returnId)(request)

      status(result) shouldBe OK
      contentAsString(result) should include("NDJSON upload complete")

      val cachedMonthlyReport = await(reportingRepository.getMonthlyReport(returnId))
      cachedMonthlyReport.map { report =>
        report.isaReport.length shouldBe 5
      }
    }

    "insert 5 records in one request & then send same request again - should not add duplicates" in {
      lazy val isaManagerId = "some-manager-3"
      lazy val returnId = "some-return-3"
      val reports =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First2","middleName":"Middle2","lastName":"Last2","dateOfBirth":"1980-01-03","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000003","nino":"AB000003C","firstName":"First3","middleName":null,"lastName":"Last3","dateOfBirth":"1980-01-04","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000003","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000004","nino":"AB000004C","firstName":"First4","middleName":"Middle4","lastName":"Last4","dateOfBirth":"1980-01-05","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000004","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000005","nino":"AB000005C","firstName":"First5","middleName":null,"lastName":"Last5","dateOfBirth":"1980-01-06","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000005","amountTransferred":5000.00,"flexibleIsa":false}
          |""".stripMargin

      val lines = reports.split("\n").toList.map(line => ByteString(line + "\n"))
      val body: Source[ByteString, _] = Source(lines)

      val request = FakeRequest()
        .withHeaders("Content-Type" -> "application/x-ndjson")
        .withBody(body)

      val result: Future[Result] = controller.uploadNdjsonWithStreamIntoMongo(isaManagerId, returnId)(request)

      status(result) shouldBe OK
      contentAsString(result) should include("NDJSON upload complete")

      val cachedMonthlyReport = await(reportingRepository.getMonthlyReport(returnId))
      cachedMonthlyReport.map { report =>
        report.isaReport.length shouldBe 5
      }

      status(result) shouldBe OK
      contentAsString(result) should include("NDJSON upload complete")

      val cachedMonthlyReport2 = await(reportingRepository.getMonthlyReport(returnId))
      cachedMonthlyReport2.map { report =>
        report.isaReport.length shouldBe 5
      }
    }

    "insert 5 records in one request & then send more new reports - should add to existing document" in {
      lazy val isaManagerId = "some-manager-3"
      lazy val returnId = "some-return-3"
      val reports =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First2","middleName":"Middle2","lastName":"Last2","dateOfBirth":"1980-01-03","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000003","nino":"AB000003C","firstName":"First3","middleName":null,"lastName":"Last3","dateOfBirth":"1980-01-04","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000003","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000004","nino":"AB000004C","firstName":"First4","middleName":"Middle4","lastName":"Last4","dateOfBirth":"1980-01-05","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000004","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000005","nino":"AB000005C","firstName":"First5","middleName":null,"lastName":"Last5","dateOfBirth":"1980-01-06","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000005","amountTransferred":5000.00,"flexibleIsa":false}
          |""".stripMargin

      val lines = reports.split("\n").toList.map(line => ByteString(line + "\n"))
      val body: Source[ByteString, _] = Source(lines)

      val request = FakeRequest()
        .withHeaders("Content-Type" -> "application/x-ndjson")
        .withBody(body)

      val result: Future[Result] = controller.uploadNdjsonWithStreamIntoMongo(isaManagerId, returnId)(request)

      status(result) shouldBe OK
      contentAsString(result) should include("NDJSON upload complete")

      val cachedMonthlyReport = await(reportingRepository.getMonthlyReport(returnId))
      cachedMonthlyReport.map { report =>
        report.isaReport.length shouldBe 5
      }

      val reports2 =
        """{"accountNumber":"STD000006","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000007","nino":"AB000002C","firstName":"First2","middleName":"Middle2","lastName":"Last2","dateOfBirth":"1980-01-03","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000008","nino":"AB000003C","firstName":"First3","middleName":null,"lastName":"Last3","dateOfBirth":"1980-01-04","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000003","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000009","nino":"AB000004C","firstName":"First4","middleName":"Middle4","lastName":"Last4","dateOfBirth":"1980-01-05","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000004","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000010","nino":"AB000005C","firstName":"First5","middleName":null,"lastName":"Last5","dateOfBirth":"1980-01-06","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000005","amountTransferred":5000.00,"flexibleIsa":false}
          |""".stripMargin

      val lines2 = reports2.split("\n").toList.map(line => ByteString(line + "\n"))
      val body2: Source[ByteString, _] = Source(lines2)

      val request2 = FakeRequest()
        .withHeaders("Content-Type" -> "application/x-ndjson")
        .withBody(body2)

      val result2: Future[Result] = controller.uploadNdjsonWithStreamIntoMongo(isaManagerId, returnId)(request2)


      status(result2) shouldBe OK
      contentAsString(result2) should include("NDJSON upload complete")

      val cachedMonthlyReport2 = await(reportingRepository.getMonthlyReport(returnId))
      cachedMonthlyReport2.map { report =>
        report.isaReport.length shouldBe 10
      }
    }

    "insert 5 records in one request for one returnId & 5 for a second reportId - should add two documents" in {
      lazy val isaManagerId = "some-manager-3"
      lazy val returnId = "some-return-3"
      lazy val secondIsaManagerId = "some-manager-4"
      lazy val secondReturnId = "some-return-4"
      val reports =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First2","middleName":"Middle2","lastName":"Last2","dateOfBirth":"1980-01-03","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000003","nino":"AB000003C","firstName":"First3","middleName":null,"lastName":"Last3","dateOfBirth":"1980-01-04","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000003","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000004","nino":"AB000004C","firstName":"First4","middleName":"Middle4","lastName":"Last4","dateOfBirth":"1980-01-05","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000004","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000005","nino":"AB000005C","firstName":"First5","middleName":null,"lastName":"Last5","dateOfBirth":"1980-01-06","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000005","amountTransferred":5000.00,"flexibleIsa":false}
          |""".stripMargin

      val lines = reports.split("\n").toList.map(line => ByteString(line + "\n"))
      val body: Source[ByteString, _] = Source(lines)

      val request = FakeRequest()
        .withHeaders("Content-Type" -> "application/x-ndjson")
        .withBody(body)

      val result: Future[Result] = controller.uploadNdjsonWithStreamIntoMongo(isaManagerId, returnId)(request)

      status(result) shouldBe OK
      contentAsString(result) should include("NDJSON upload complete")

      val cachedMonthlyReport = await(reportingRepository.getMonthlyReport(returnId))
      cachedMonthlyReport.map { report =>
        report.isaReport.length shouldBe 5
      }

      val result2: Future[Result] = controller.uploadNdjsonWithStreamIntoMongo(secondIsaManagerId, secondReturnId)(request)

      status(result2) shouldBe OK
      contentAsString(result2) should include("NDJSON upload complete")

      val cachedMonthlyReport2 = await(reportingRepository.getMonthlyReport(secondReturnId))
      cachedMonthlyReport2.map { report =>
        report.isaReport.length shouldBe 5
      }
    }
  }
  "NdJsonController#uploadNdjsonWithStreamValidation" should {

    "insert 5 records into one mongo document" in {
      lazy val isaManagerId = "some-manager-2"
      lazy val returnId = "some-return-2"
      val reports =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First2","middleName":"Middle2","lastName":"Last2","dateOfBirth":"1980-01-03","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000003","nino":"AB000003C","firstName":"First3","middleName":null,"lastName":"Last3","dateOfBirth":"1980-01-04","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000003","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000004","nino":"AB000004C","firstName":"First4","middleName":"Middle4","lastName":"Last4","dateOfBirth":"1980-01-05","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000004","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000005","nino":"AB000005C","firstName":"First5","middleName":null,"lastName":"Last5","dateOfBirth":"1980-01-06","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000005","amountTransferred":5000.00,"flexibleIsa":false}
          |""".stripMargin

      val lines = reports.split("\n").toList.map(line => ByteString(line + "\n"))
      val body: Source[ByteString, _] = Source(lines)

      val request = FakeRequest()
        .withHeaders("Content-Type" -> "application/x-ndjson")
        .withBody(body)

      val result: Future[Result] = controller.uploadNdjsonWithStreamValidation(isaManagerId, returnId)(request)

      status(result) shouldBe OK
      contentAsString(result) should include("NDJSON upload complete")

      val cachedMonthlyReport = await(reportingRepository.getMonthlyReport(returnId))
      cachedMonthlyReport.map { report =>
        report.isaReport.length shouldBe 5
      }
    }

    "insert 5 records in one request & then send same request again - should not add duplicates" in {
      lazy val isaManagerId = "some-manager-3"
      lazy val returnId = "some-return-3"
      val reports =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First2","middleName":"Middle2","lastName":"Last2","dateOfBirth":"1980-01-03","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000003","nino":"AB000003C","firstName":"First3","middleName":null,"lastName":"Last3","dateOfBirth":"1980-01-04","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000003","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000004","nino":"AB000004C","firstName":"First4","middleName":"Middle4","lastName":"Last4","dateOfBirth":"1980-01-05","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000004","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000005","nino":"AB000005C","firstName":"First5","middleName":null,"lastName":"Last5","dateOfBirth":"1980-01-06","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000005","amountTransferred":5000.00,"flexibleIsa":false}
          |""".stripMargin

      val lines = reports.split("\n").toList.map(line => ByteString(line + "\n"))
      val body: Source[ByteString, _] = Source(lines)

      val request = FakeRequest()
        .withHeaders("Content-Type" -> "application/x-ndjson")
        .withBody(body)

      val result: Future[Result] = controller.uploadNdjsonWithStreamValidation(isaManagerId, returnId)(request)

      status(result) shouldBe OK
      contentAsString(result) should include("NDJSON upload complete")

      val cachedMonthlyReport = await(reportingRepository.getMonthlyReport(returnId))
      cachedMonthlyReport.map { report =>
        report.isaReport.length shouldBe 5
      }

      status(result) shouldBe OK
      contentAsString(result) should include("NDJSON upload complete")

      val cachedMonthlyReport2 = await(reportingRepository.getMonthlyReport(returnId))
      cachedMonthlyReport2.map { report =>
        report.isaReport.length shouldBe 5
      }
    }

    "insert 5 records in one request & then send more new reports - should add to existing document" in {
      lazy val isaManagerId = "some-manager-3"
      lazy val returnId = "some-return-3"
      val reports =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First2","middleName":"Middle2","lastName":"Last2","dateOfBirth":"1980-01-03","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000003","nino":"AB000003C","firstName":"First3","middleName":null,"lastName":"Last3","dateOfBirth":"1980-01-04","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000003","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000004","nino":"AB000004C","firstName":"First4","middleName":"Middle4","lastName":"Last4","dateOfBirth":"1980-01-05","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000004","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000005","nino":"AB000005C","firstName":"First5","middleName":null,"lastName":"Last5","dateOfBirth":"1980-01-06","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000005","amountTransferred":5000.00,"flexibleIsa":false}
          |""".stripMargin

      val lines = reports.split("\n").toList.map(line => ByteString(line + "\n"))
      val body: Source[ByteString, _] = Source(lines)

      val request = FakeRequest()
        .withHeaders("Content-Type" -> "application/x-ndjson")
        .withBody(body)

      val result: Future[Result] = controller.uploadNdjsonWithStreamValidation(isaManagerId, returnId)(request)

      status(result) shouldBe OK
      contentAsString(result) should include("NDJSON upload complete")

      val cachedMonthlyReport = await(reportingRepository.getMonthlyReport(returnId))
      cachedMonthlyReport.map { report =>
        report.isaReport.length shouldBe 5
      }

      val reports2 =
        """{"accountNumber":"STD000006","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000007","nino":"AB000002C","firstName":"First2","middleName":"Middle2","lastName":"Last2","dateOfBirth":"1980-01-03","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000008","nino":"AB000003C","firstName":"First3","middleName":null,"lastName":"Last3","dateOfBirth":"1980-01-04","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000003","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000009","nino":"AB000004C","firstName":"First4","middleName":"Middle4","lastName":"Last4","dateOfBirth":"1980-01-05","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000004","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000010","nino":"AB000005C","firstName":"First5","middleName":null,"lastName":"Last5","dateOfBirth":"1980-01-06","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000005","amountTransferred":5000.00,"flexibleIsa":false}
          |""".stripMargin

      val lines2 = reports2.split("\n").toList.map(line => ByteString(line + "\n"))
      val body2: Source[ByteString, _] = Source(lines2)

      val request2 = FakeRequest()
        .withHeaders("Content-Type" -> "application/x-ndjson")
        .withBody(body2)

      val result2: Future[Result] = controller.uploadNdjsonWithStreamValidation(isaManagerId, returnId)(request2)


      status(result2) shouldBe OK
      contentAsString(result2) should include("NDJSON upload complete")

      val cachedMonthlyReport2 = await(reportingRepository.getMonthlyReport(returnId))
      cachedMonthlyReport2.map { report =>
        report.isaReport.length shouldBe 10
      }
    }

    "insert 5 records in one request for one returnId & 5 for a second reportId - should add two documents" in {
      lazy val isaManagerId = "some-manager-3"
      lazy val returnId = "some-return-3"
      lazy val secondIsaManagerId = "some-manager-4"
      lazy val secondReturnId = "some-return-4"
      val reports =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First2","middleName":"Middle2","lastName":"Last2","dateOfBirth":"1980-01-03","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000003","nino":"AB000003C","firstName":"First3","middleName":null,"lastName":"Last3","dateOfBirth":"1980-01-04","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000003","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000004","nino":"AB000004C","firstName":"First4","middleName":"Middle4","lastName":"Last4","dateOfBirth":"1980-01-05","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000004","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000005","nino":"AB000005C","firstName":"First5","middleName":null,"lastName":"Last5","dateOfBirth":"1980-01-06","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000005","amountTransferred":5000.00,"flexibleIsa":false}
          |""".stripMargin

      val lines = reports.split("\n").toList.map(line => ByteString(line + "\n"))
      val body: Source[ByteString, _] = Source(lines)

      val request = FakeRequest()
        .withHeaders("Content-Type" -> "application/x-ndjson")
        .withBody(body)

      val result: Future[Result] = controller.uploadNdjsonWithStreamValidation(isaManagerId, returnId)(request)

      status(result) shouldBe OK
      contentAsString(result) should include("NDJSON upload complete")

      val cachedMonthlyReport = await(reportingRepository.getMonthlyReport(returnId))
      cachedMonthlyReport.map { report =>
        report.isaReport.length shouldBe 5
      }

      val result2: Future[Result] = controller.uploadNdjsonWithStreamIntoMongo(secondIsaManagerId, secondReturnId)(request)

      status(result2) shouldBe OK
      contentAsString(result2) should include("NDJSON upload complete")

      val cachedMonthlyReport2 = await(reportingRepository.getMonthlyReport(secondReturnId))
      cachedMonthlyReport2.map { report =>
        report.isaReport.length shouldBe 5
      }
    }
  }
}
