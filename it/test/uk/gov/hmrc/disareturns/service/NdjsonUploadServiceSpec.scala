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

package uk.gov.hmrc.disareturns.service

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.disareturns.controllers.NdJsonController
import uk.gov.hmrc.disareturns.mongoRepositories.{InvalidIsaAccountRepository, ReportingRepository}

import scala.concurrent.ExecutionContext.Implicits.global

class NdjsonUploadServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with IntegrationPatience with BeforeAndAfterEach {

  lazy val app: Application = new GuiceApplicationBuilder().build()

  implicit val mat: Materializer = app.materializer

  lazy val reportingRepository:         ReportingRepository         = app.injector.instanceOf[ReportingRepository]
  lazy val invalidIsaAccountRepository: InvalidIsaAccountRepository = app.injector.instanceOf[InvalidIsaAccountRepository]
  lazy val service:                     NdjsonUploadService         = app.injector.instanceOf[NdjsonUploadService]
  lazy val controller: NdJsonController = app.injector.instanceOf[NdJsonController]


  override def beforeEach(): Unit = {
    await(reportingRepository.dropCollection())
    await(invalidIsaAccountRepository.dropCollection())
  }

  "NdjsonUploadService" should {

    "successfully process valid NDJSON" in {
      val ndjson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}
            |{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First2","middleName":"Middle2","lastName":"Last2","dateOfBirth":"1980-01-03","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":5000.00,"flexibleIsa":true}
            |{"accountNumber":"STD000003","nino":"AB000003C","firstName":"First3","middleName":null,"lastName":"Last3","dateOfBirth":"1980-01-04","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000003","amountTransferred":5000.00,"flexibleIsa":false}
            |{"accountNumber":"STD000004","nino":"AB000004C","firstName":"First4","middleName":"Middle4","lastName":"Last4","dateOfBirth":"1980-01-05","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000004","amountTransferred":5000.00,"flexibleIsa":true}
            |{"accountNumber":"STD000005","nino":"AB000005C","firstName":"First5","middleName":null,"lastName":"Last5","dateOfBirth":"1980-01-06","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000005","amountTransferred":5000.00,"flexibleIsa":false}
            |""".stripMargin

      val source = Source(ndjson.split("\n").map(line => ByteString(line + "\n")).toList)

      val result = await(service.processNdjsonUpload("Manager_1", "return_1", source))

      result shouldBe true
    }

    "store invalid entries with bad NINOs and return false" in {
      val ndjson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000002","nino":"AB000002C%%","firstName":"First2","middleName":"Middle2","lastName":"Last2","dateOfBirth":"1980-01-03","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000003","nino":"AB000003C","firstName":"First3","middleName":null,"lastName":"Last3","dateOfBirth":"1980-01-04","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000003","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000004","nino":"AB000004C","firstName":"First4","middleName":"Middle4","lastName":"Last4","dateOfBirth":"1980-01-05","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000004","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000005","nino":"AB000005C","firstName":"First5","middleName":null,"lastName":"Last5","dateOfBirth":"1980-01-06","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000005","amountTransferred":5000.00,"flexibleIsa":false}
          |""".stripMargin

      val source = Source(ndjson.split("\n").map(line => ByteString(line + "\n")).toList)

      val result = service.processNdjsonUpload("Manager_2", "return_2", source)



      result.futureValue shouldBe false

    }

  }
}
