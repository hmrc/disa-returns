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

package uk.gov.hmrc.disareturns.connector

import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, UNAUTHORIZED}
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.connectors.PPNSConnector
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec
import uk.gov.hmrc.disareturns.utils.WiremockHelper._

class PPNSConnectorISpec extends BaseIntegrationSpec {

  lazy val connector: PPNSConnector = app.injector.instanceOf[PPNSConnector]
  val testClientId = "test-client-id-12345"

  "PPNSConnector.getBoxId" should {

    "return Right(Box) when PPNS returns a 200 with valid Box JSON" in {
      val boxJson =
        s"""
           |{
           |  "boxId": "boxId1",
           |  "boxName": "Test_Box",
           |  "boxCreator": {
           |    "clientId": "$testClientId"
           |  },
           |  "applicationId": "applicationId"
           |}
           |""".stripMargin

      stubGet(url = s"/box?clientId=$testClientId", status = OK, body = boxJson)

      val Right(response) = await(connector.getBox(testClientId).value)

      response.status shouldBe OK
      (response.json \ "boxId").as[String] shouldBe "boxId1"
      (response.json \ "boxName").as[String] shouldBe "Test_Box"
      (response.json \ "boxCreator" \ "clientId").as[String] shouldBe testClientId
      (response.json \ "applicationId").as[String] shouldBe "applicationId"
    }

    "return Left(UpstreamErrorResponse) when PPNS returns a 401" in {

      stubGet(url = s"/box?clientId=$testClientId", status = UNAUTHORIZED, body = "Unauthorized")

      val Left(response) = await(connector.getBox(testClientId).value)
      response.statusCode shouldBe UNAUTHORIZED
      response.message should include("Unauthorized")
    }

    "return Left(UpstreamErrorResponse) when the call fails with unexpected exception" in {
      stopWiremock() // simulate connection failure

      val Left(response) = await(connector.getBox(testClientId).value)
      response.statusCode shouldBe INTERNAL_SERVER_ERROR
      response.message should include("Unexpected error:")
    }
  }
}
