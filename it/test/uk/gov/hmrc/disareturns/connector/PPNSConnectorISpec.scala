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

import play.api.http.Status.{NOT_FOUND, OK, UNAUTHORIZED}
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.config.Constants
import uk.gov.hmrc.disareturns.connectors.PPNSConnector
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec
import uk.gov.hmrc.disareturns.utils.WiremockHelper._

class PPNSConnectorISpec extends BaseIntegrationSpec {

  lazy val connector: PPNSConnector = app.injector.instanceOf[PPNSConnector]

  override val testClientId = "test-client-id-12345"
  val url                   = "/box?clientId=test-client-id-12345&boxName=obligations%2Fdeclaration%2Fisa%2Freturn%23%231.0%23%23callbackUrl"

  "PPNSConnector.getBox" should {

    "return Right(Some(boxId)) when PPNS returns a 200 with valid Box JSON" in {
      val boxJson =
        s"""
           |{
           |  "boxId": "boxId1",
           |  "boxName": "${Constants.BoxName}",
           |  "boxCreator": {
           |    "clientId": "$testClientId"
           |  },
           |  "applicationId": "applicationId"
           |}
           |""".stripMargin

      stubGet(url = url, status = OK, body = boxJson)

      val result = await(connector.getBox(testClientId))

      result shouldBe Right(Some("boxId1"))
    }

    "return Right(None) when PPNS returns a 404" in {
      stubGet(url = url, status = NOT_FOUND, body = "")

      val result = await(connector.getBox(testClientId))

      result shouldBe Right(None)
    }

    "return Left(UpstreamErrorResponse) when PPNS returns a 401" in {
      stubGet(url = url, status = UNAUTHORIZED, body = "Unauthorized")

      val result = await(connector.getBox(testClientId))

      result match {
        case Left(error) =>
          error.statusCode shouldBe UNAUTHORIZED
          error.message      should include("Unexpected status from PPNS: 401")
        case _ => fail("Expected Left(UpstreamErrorResponse)")
      }
    }
  }

}
