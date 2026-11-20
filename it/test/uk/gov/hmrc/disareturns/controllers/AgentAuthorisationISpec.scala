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

package uk.gov.hmrc.disareturns.controllers

import com.github.tomakehurst.wiremock.client.WireMock.*
import play.api.http.HeaderNames.{AUTHORIZATION, CONTENT_TYPE}
import play.api.http.Status.*
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.DefaultBodyWritables.writeableOf_String
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec

trait AgentAuthorisationBehaviours { self: BaseIntegrationSpec =>

  private val taxYear = "2026-27"
  private val month   = 9

  private val standardIsaSubscription =
    """{"accountNumber":"STD000001","nino":"AB000001C","firstName":"First3","middleName":"Middle3","lastName":"Last3","dateOfBirth":"1980-01-02","isaType":"STOCKS_AND_SHARES","amountTransferredIn": 2500.00,"amountTransferredOut": 2500.00,"dateOfLastSubscription":"2025-06-01","totalCurrentYearSubscriptionsToDate":2500.00,"marketValueOfAccount":10000.00,"flexibleIsa":false}"""

  private val boxResponseJson =
    s"""{"boxId":"boxId1","boxName":"Test_Box","boxCreator":{"clientId":"$testClientId"},"applicationId":"applicationId"}"""

  protected val agentClientRelationshipNotFound: JsValue = Json.obj(
    "code"    -> "AGENT_CLIENT_RELATIONSHIP_NOT_FOUND",
    "message" -> s"We are unable to authenticate your access to $validZReference"
  )

  protected val unauthorised: JsValue = Json.obj("code" -> "UNAUTHORISED", "message" -> "Unauthorised")

  protected def submitRequest(): WSResponse = {
    stubMonthlyReturnReportingStatus(status = OK)
    stubEnsureMonthlyReturnExists(CREATED, validZReference, taxYear, month)
    stubStoreMonthlyReturn(OK, validZReference, taxYear, month)
    await(
      ws.url(s"http://localhost:$port/monthly/$validZReference")
        .withHttpHeaders(testHeaders.filterNot(_._1 == CONTENT_TYPE) :+ (CONTENT_TYPE -> "application/x-ndjson")*)
        .post(standardIsaSubscription + "\n")
    )
  }

  protected def declareRequest(): WSResponse = {
    stubMonthlyReturnReportingStatus(status = OK)
    stubSubmissionDeclaration(ok, validZReference, taxYear, month)
    stubPPNSBoxId(boxResponseJson, testClientId)
    await(
      ws.url(s"http://localhost:$port/monthly/$validZReference/declaration")
        .withHttpHeaders(testHeaders*)
        .post("""{"nilReturn":false}""")
    )
  }

  protected def reconciliationReportRequest(): WSResponse = {
    stubFor(
      get(urlEqualTo(s"/monthly/$validZReference/$taxYear/SEP/results?limit=200"))
        .willReturn(aResponse().withStatus(OK).withBody(Json.obj("returnResults" -> Json.arr()).toString))
    )
    await(
      ws.url(s"http://localhost:$port/monthly/$validZReference/results")
        .withHttpHeaders(AUTHORIZATION -> "mock-bearer-token")
        .get()
    )
  }

  protected val endpoints: Seq[(String, () => WSResponse, Int)] = Seq(
    ("submit", () => submitRequest(), NO_CONTENT),
    ("declare", () => declareRequest(), OK),
    ("get reconciliation report", () => reconciliationReportRequest(), OK)
  )
}

class AgentAuthorisationISpec extends BaseIntegrationSpec with AgentAuthorisationBehaviours {

  override def config: Map[String, Any] =
    super.config ++ Map(
      "features.enrolment-verification-enabled" -> true,
      "features.agent-authentication-enabled"   -> true
    )

  endpoints.foreach { case (name, request, successStatus) =>
    s"the $name endpoint" should {

      s"allow an agent with a delegated enrolment matching the requested Z-reference through" in {
        stubAgentAuth(hasDelegatedEnrolment = true)

        request().status shouldBe successStatus
      }

      s"return 403 AGENT_CLIENT_RELATIONSHIP_NOT_FOUND when the agent does not have a delegated enrolment matching the requested Z-reference" in {
        stubAgentAuth(hasDelegatedEnrolment = false)

        val result = request()

        result.status shouldBe FORBIDDEN
        result.json   shouldBe agentClientRelationshipNotFound
      }
    }
  }
}

class AgentAuthorisationEnrolmentCheckDisabledISpec extends BaseIntegrationSpec with AgentAuthorisationBehaviours {

  override def config: Map[String, Any] =
    super.config ++ Map(
      "features.enrolment-verification-enabled" -> false,
      "features.agent-authentication-enabled"   -> true
    )

  endpoints.foreach { case (name, request, successStatus) =>
    s"the $name endpoint" should {

      "allow an agent without a delegated enrolment matching the requested Z-reference through when the enrolment check is disabled" in {
        stubAgentAuth(hasDelegatedEnrolment = false)

        request().status shouldBe successStatus
      }
    }
  }
}

class AgentAuthorisationDisabledISpec extends BaseIntegrationSpec with AgentAuthorisationBehaviours {

  override def config: Map[String, Any] =
    super.config ++ Map(
      "features.enrolment-verification-enabled" -> true,
      "features.agent-authentication-enabled"   -> false
    )

  endpoints.foreach { case (name, request, _) =>
    s"the $name endpoint" should {

      "return 401 for an agent with a delegated enrolment when agent authentication is disabled" in {
        stubAgentAuth(hasDelegatedEnrolment = true)

        val result = request()

        result.status shouldBe UNAUTHORIZED
        result.json   shouldBe unauthorised
      }
    }
  }
}

class AgentAuthorisationDisabledWithEnrolmentCheckDisabledISpec extends BaseIntegrationSpec with AgentAuthorisationBehaviours {

  override def config: Map[String, Any] =
    super.config ++ Map(
      "features.enrolment-verification-enabled" -> false,
      "features.agent-authentication-enabled"   -> false
    )

  endpoints.foreach { case (name, request, _) =>
    s"the $name endpoint" should {

      "return 401 for an agent when both agent authentication and the enrolment check are disabled" in {
        stubAgentAuth(hasDelegatedEnrolment = true)

        request().status shouldBe UNAUTHORIZED
      }
    }
  }
}
