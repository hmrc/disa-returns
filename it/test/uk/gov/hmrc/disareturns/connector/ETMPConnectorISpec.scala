/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND, OK, UNAUTHORIZED}
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.connectors.ETMPConnector
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec
import uk.gov.hmrc.disareturns.utils.WiremockHelper._

class ETMPConnectorISpec extends BaseIntegrationSpec {

  val obligationsUrl = s"/etmp/check-obligation-status/$validZReference"

  val connector: ETMPConnector = app.injector.instanceOf[ETMPConnector]

  "ETMPConnector.checkReturnsObligationStatus" should {
    "return Right(HttpResponse) when ETMP returns a successful response" in {
      val responseBody = """{ "obligationAlreadyMet": true }"""

      stubGet(obligationsUrl, OK, responseBody)

      val response = await(connector.getReturnsObligationStatus(validZReference).value).value

      response.status                                      shouldBe OK
      (response.json \ "obligationAlreadyMet").as[Boolean] shouldBe true
    }

    "return Left(UpstreamErrorResponse) when ETMP returns an error status" in {
      stubGet(obligationsUrl, UNAUTHORIZED, """{"error": "Not authorised"}""")

      val response = await(connector.getReturnsObligationStatus(validZReference).value).left.value

      response.statusCode shouldBe UNAUTHORIZED
      response.message      should include("Not authorised")
      verifyGet(obligationsUrl, count = 1)
    }

    "retry a persistent server error four times" in {
      stubGet(obligationsUrl, INTERNAL_SERVER_ERROR, "failed")

      await(connector.getReturnsObligationStatus(validZReference).value).left.value.statusCode shouldBe INTERNAL_SERVER_ERROR
      verifyGet(obligationsUrl, count = 4)
    }

    "return Left(UpstreamErrorResponse) when ETMP fails with unexpected exception - No stub simulate 404" in {
      val response = await(connector.getReturnsObligationStatus("non-existent").value).left.value

      response.statusCode shouldBe NOT_FOUND
      response.message      should include("No response could be served as there are no stub mappings in this WireMock instance.")
    }
  }
}
