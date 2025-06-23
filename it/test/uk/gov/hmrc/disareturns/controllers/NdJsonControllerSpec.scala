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
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import play.api.test._
import play.api.{Application, inject}
import uk.gov.hmrc.disareturns.mongoRepositories.{InvalidIsaAccountRepository, ReportingRepository}
import uk.gov.hmrc.disareturns.service.NdjsonUploadService

import scala.concurrent.Future


class NdJsonControllerSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach {

  implicit lazy val materializer: Materializer     = app.materializer

  lazy val controller:          NdJsonController            = app.injector.instanceOf[NdJsonController]
  lazy val reportingRepository: ReportingRepository         = app.injector.instanceOf[ReportingRepository]
  lazy val invalidRepository:   InvalidIsaAccountRepository = app.injector.instanceOf[InvalidIsaAccountRepository]
  val service: NdjsonUploadService = mock[NdjsonUploadService]

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().overrides(inject.bind[NdjsonUploadService].toInstance(service))
      .build()

  override def beforeEach(): Unit = {
    await(reportingRepository.dropCollection())
    await(invalidRepository.dropCollection())
  }

  "NdJsonController#uploadNdjsonWithStreamValidation" should {
    val url = "/monthly-v3/manager1/submission/return1"

    "return 200 OK when all NDJSON entries are valid" in {
      val validNdjson =
        """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First2","middleName":"Middle2","lastName":"Last2","dateOfBirth":"1980-01-03","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000003","nino":"AB000003C","firstName":"First3","middleName":null,"lastName":"Last3","dateOfBirth":"1980-01-04","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000003","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000004","nino":"AB000004C","firstName":"First4","middleName":"Middle4","lastName":"Last4","dateOfBirth":"1980-01-05","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000004","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000005","nino":"AB000005C","firstName":"First5","middleName":null,"lastName":"Last5","dateOfBirth":"1980-01-06","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000005","amountTransferred":5000.00,"flexibleIsa":false}
          |""".stripMargin

      val request = FakeRequest(POST, url)
        .withBody(ByteString(validNdjson))
        .withHeaders("Content-Type" -> "application/x-ndjson")

      when(service.processNdjsonUpload(any(), any(),any())(any())).thenReturn(Future.successful(true))

      val result = controller.uploadNdjsonWithStreamValidation("manager1", "return1")(request)

      status(result)        shouldBe OK
      contentAsString(result) should include("NDJSON upload complete")
    }

    "return 400 BadRequest when some entries are invalid" in {
      val url = "/monthly-v3/manager2/submission/return2"
      val invalidNdjson =
        """{"accountNumber":"STD000001","nino":"AB000001%%%","firstName":"First1","middleName":null,"lastName":"Last1","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000001","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000002","nino":"AB000002C","firstName":"First2","middleName":"Middle2","lastName":"Last2","dateOfBirth":"1980-01-03","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000002","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000003","nino":"AB000003C","firstName":"First3","middleName":null,"lastName":"Last3","dateOfBirth":"1980-01-04","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000003","amountTransferred":5000.00,"flexibleIsa":false}
          |{"accountNumber":"STD000004","nino":"AB000004C","firstName":"First4","middleName":"Middle4","lastName":"Last4","dateOfBirth":"1980-01-05","isaType":"CASH","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000004","amountTransferred":5000.00,"flexibleIsa":true}
          |{"accountNumber":"STD000005","nino":"AB000005C","firstName":"First5","middleName":null,"lastName":"Last5","dateOfBirth":"1980-01-06","isaType":"STOCKS_AND_SHARES","reportingATransfer":true,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"accountNumberOfTransferringAccount":"OLD000005","amountTransferred":5000.00,"flexibleIsa":false}
          |""".stripMargin

      val request = FakeRequest(POST, url)
        .withBody(ByteString(invalidNdjson))
        .withHeaders("Content-Type" -> "application/x-ndjson")

      when(service.processNdjsonUpload(any(), any(),any())(any())).thenReturn(Future.successful(false))


      val result = controller.uploadNdjsonWithStreamValidation("manager2", "return2")(request)

      status(result)        shouldBe BAD_REQUEST
      contentAsString(result) should include("some entries were invalid")
    }
  }
}
