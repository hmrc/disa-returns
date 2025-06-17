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
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status}
import uk.gov.hmrc.disareturns.mongoRepositories.ReportingRepository

import scala.concurrent.Future

// MOVE TO UNIT test folder
class NdJsonControllerSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with GuiceOneServerPerSuite {

  private val controller = app.injector.instanceOf[NdJsonController]
  implicit val materializer: Materializer = app.materializer
  private val baseUrl  = s"http://localhost:$port"

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .build()

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

  private val mockRepo = mock[ReportingRepository]


  "NdJsonController#uploadNdjsonStreamWithMongo" should {

    "stream and parse valid NDJSON and insert into Mongo" in {
      val isaManagerId = "some-manager-id"

      val reports =
        """{"isaAmount": 1000, "id": 1, "isaManager": "ManagerA"}
          |{"isaAmount": 2000, "id": 2, "isaManager": "ManagerB"}
          |{"isaAmount": 3000, "id": 3, "isaManager": "ManagerC"}""".stripMargin

      val lines = reports.split("\n").toList.map(line => ByteString(line + "\n"))
      val body: Source[ByteString, _] = Source(lines)

      // Stub the insertBatch method
      when(mockRepo.insertBatch(
        org.mockito.ArgumentMatchers.eq(isaManagerId),
        org.mockito.ArgumentMatchers.any[Seq[ISAReport]]
      )).thenReturn(Future.successful(()))

      val request = FakeRequest()
        .withHeaders("Content-Type" -> "application/x-ndjson")
        .withBody(body)

      val result: Future[Result] = controller.uploadNdjsonStreamWithMongo(isaManagerId)(request)

      status(result) shouldBe OK
      contentAsString(result) should include("Inserted 3 reports into MongoDB")
    }

    "return BadRequest on malformed NDJSON" in {
      val isaManagerId = "bad-manager-id"

      val invalidJson =
        """{"isaAmount": 1000, "id": 1, "isaManager": "ManagerA"}
          |{invalid-json
          |{"isaAmount": 3000, "id": 3, "isaManager": "ManagerC"}""".stripMargin

      val lines = invalidJson.split("\n").toList.map(line => ByteString(line + "\n"))
      val body: Source[ByteString, _] = Source(lines)

      val request = FakeRequest()
        .withHeaders("Content-Type" -> "application/x-ndjson")
        .withBody(body)

      val result: Future[Result] = controller.uploadNdjsonStreamWithMongo(isaManagerId)(request)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("Error processing NDJSON")
    }
  }
}
