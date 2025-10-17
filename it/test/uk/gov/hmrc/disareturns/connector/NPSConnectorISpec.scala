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

import play.api.http.Status.{NOT_FOUND, NO_CONTENT, UNAUTHORIZED}
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.connectors.NPSConnector
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec
import uk.gov.hmrc.disareturns.utils.WiremockHelper._

class NPSConnectorISpec extends BaseIntegrationSpec {

  private val testIsaManagerReferenceNumber = "Z1234"
  private val submitUrl                     = s"/nps/submit/$testIsaManagerReferenceNumber"

  private val connector: NPSConnector = app.injector.instanceOf[NPSConnector]

  "NPSConnector.submit" should {

    "return Right(HttpResponse) when NPS returns 204 NO_CONTENT" in {
      stubPost(submitUrl, NO_CONTENT, "")

      val Right(response) =
        await(connector.submit(testIsaManagerReferenceNumber, Nil).value)

      response.status shouldBe NO_CONTENT
      response.body    shouldBe ""
    }

    "return Left(UpstreamErrorResponse) when NPS returns an error status (401)" in {
      stubPost(submitUrl, UNAUTHORIZED, """{"error":"Not authorised"}""")

      val Left(err) =
        await(connector.submit(testIsaManagerReferenceNumber, Nil).value)

      err.statusCode shouldBe UNAUTHORIZED
      err.message     should include("Not authorised")
    }

    "return Left(UpstreamErrorResponse) when the call fails with an unexpected exception" in {
      val Left(err) =
        await(connector.submit("non-existent", Nil).value)

      err.statusCode shouldBe NOT_FOUND
      err.message     should include("No response could be served as there are no stub mappings in this WireMock instance.")
    }
  }
}