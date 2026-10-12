/*
 * Copyright 2026 HM Revenue & Customs
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

import play.api.http.Status._
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.connectors.DisaAccountConnector
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec
import uk.gov.hmrc.disareturns.utils.WiremockHelper._
import uk.gov.hmrc.http.UpstreamErrorResponse

class DisaAccountConnectorISpec extends BaseIntegrationSpec {

  private val connector        = app.injector.instanceOf[DisaAccountConnector]
  private val registrationPath = s"/disa-account/registration/$validZReference"

  "DisaAccountConnector.getCompanyName" should {

    "return the company name from the registration details" in {
      stubGet(
        registrationPath,
        OK,
        """{"groupId":"testGroupId","businessVerification":{"ctUtr":"1234567890","companyName":"testIM","companyNumber":"12345678"}}"""
      )

      await(connector.getCompanyName(validZReference)) shouldBe Some("testIM")
      verifyGet(registrationPath)
    }

    "return None when the registration details have no company name" in {
      stubGet(registrationPath, OK, """{"groupId":"testGroupId"}""")

      await(connector.getCompanyName(validZReference)) shouldBe None
    }

    "return None when no registration details are found" in {
      stubGet(registrationPath, NOT_FOUND, "")

      await(connector.getCompanyName(validZReference)) shouldBe None
    }

    "fail with an UpstreamErrorResponse for any other status" in {
      stubGet(registrationPath, INTERNAL_SERVER_ERROR, "")

      val error = intercept[UpstreamErrorResponse](await(connector.getCompanyName(validZReference)))

      error.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }
}
