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

package uk.gov.hmrc.disareturns.utils

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.HeaderNames.{AUTHORIZATION, CONTENT_TYPE}
import play.api.http.MimeTypes.JSON
import play.api.http.Status
import play.api.http.Status.OK
import play.api.libs.json.Json
import uk.gov.hmrc.disareturns.config.Constants.{AgentEnrolmentKey, DelegatedAuthRule, EnrolmentKey, IdentifierKey}
import uk.gov.hmrc.disareturns.models.declaration.ReportingNilReturn

trait CommonStubs { self: TestData =>

  private val submissionIdPattern = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"

  private val enrolmentVerificationAuthRequest = s"""{
    "authorise":[{"$$or":[
      [{"affinityGroup":"Organisation"},{"enrolment":"$EnrolmentKey","identifiers":[],"state":"Activated"}],
      [{"affinityGroup":"Agent"},{"enrolment":"$AgentEnrolmentKey","identifiers":[],"state":"Activated"}]
    ]}],
    "retrieve":["authorisedEnrolments","optionalCredentials","credentialRole","affinityGroup","agentInformation","groupIdentifier"]
  }"""

  private val authenticatedAuthRequest = s"""{
    "authorise":[{"$$or":[
      {"affinityGroup":"Organisation"},
      [{"affinityGroup":"Agent"},{"enrolment":"$AgentEnrolmentKey","identifiers":[],"state":"Activated"}]
    ]}],
    "retrieve":["optionalCredentials","credentialRole","affinityGroup","agentInformation","groupIdentifier"]
  }"""

  def agentDelegatedAuthRequest(zRef: String = validZReference): String = Json.stringify(
    Json.obj(
      "authorise" -> Json.arr(
        Json.obj(
          "enrolment"         -> EnrolmentKey,
          "identifiers"       -> Json.arr(Json.obj("key" -> IdentifierKey, "value" -> zRef)),
          "state"             -> "Activated",
          "delegatedAuthRule" -> DelegatedAuthRule
        )
      ),
      "retrieve" -> Json.arr()
    )
  )

  def stubAuth(zRef: String = validZReference): Unit = {
    val validEnrolment = s"""{
      "authorisedEnrolments": [{
        "key": "$EnrolmentKey",
        "identifiers": [{ "key": "$IdentifierKey", "value": "$zRef" }],
        "state": "Activated"
      }],
      "optionalCredentials": { "providerId": "5053588723516092", "providerType": "GovernmentGateway" },
      "credentialRole": "User",
      "affinityGroup": "Organisation",
      "agentInformation": {},
      "groupIdentifier": "testGroupId"
      }"""

    stubFor {
      post("/auth/authorise")
        .withRequestBody(equalToJson(enrolmentVerificationAuthRequest))
        .willReturn {
          aResponse.withStatus(OK).withBody(validEnrolment)
        }
    }
  }

  def stubAgentAuth(hasDelegatedEnrolment: Boolean, enrolmentCheckEnabled: Boolean = true, zRef: String = validZReference): Unit = {
    val agentDetails = """{
      "authorisedEnrolments": [],
      "affinityGroup": "Agent",
      "agentInformation": { "agentId": "testAgentId", "agentFriendlyName": "testAgent" },
      "groupIdentifier": "testGroupId"
      }"""

    stubFor {
      post("/auth/authorise")
        .withRequestBody(equalToJson(if (enrolmentCheckEnabled) enrolmentVerificationAuthRequest else authenticatedAuthRequest))
        .willReturn(aResponse.withStatus(OK).withBody(agentDetails))
    }

    val delegatedEnrolmentResponse =
      if (hasDelegatedEnrolment) aResponse.withStatus(OK).withBody("{}")
      else aResponse.withStatus(Status.UNAUTHORIZED).withHeader("WWW-Authenticate", "MDTP detail=\"InsufficientEnrolments\"")

    stubFor {
      post("/auth/authorise")
        .withRequestBody(equalToJson(agentDelegatedAuthRequest(zRef)))
        .willReturn(delegatedEnrolmentResponse)
    }
  }

  def verifyAgentDelegatedAuthRequested(times: Int, zRef: String = validZReference): Unit =
    verify(times, postRequestedFor(urlEqualTo("/auth/authorise")).withRequestBody(equalToJson(agentDelegatedAuthRequest(zRef))))

  def stubAuthFail(): Unit =
    stubFor {
      post("/auth/authorise")
        .willReturn {
          aResponse()
            .withStatus(Status.UNAUTHORIZED)
            .withBody("{}")
        }
    }

  def stubMonthlyReturnReportingStatus(
    status:                Int,
    reportingWindowOpen:   Boolean = true,
    obligationClosed:      Boolean = false,
    monthlyReturnDeclared: Boolean = false,
    zReference:            String = validZReference
  ): Unit =
    stubFor(
      get(urlEqualTo(s"/disa-returns-submission/monthly/$zReference/reporting-status"))
        .withHeader(AUTHORIZATION, matching(".+"))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withBody(
              Json
                .obj(
                  "reportingWindowOpen"   -> reportingWindowOpen,
                  "reportingWindowStart"  -> "2026-06-06T00:00:00Z",
                  "reportingWindowEnd"    -> "2026-06-19T23:59:59Z",
                  "resolvedAt"            -> "2026-06-12T00:00:00Z",
                  "taxYear"               -> "2026-27",
                  "month"                 -> 5,
                  "obligationClosed"      -> obligationClosed,
                  "monthlyReturnDeclared" -> monthlyReturnDeclared,
                  "nilReturn"             -> false
                )
                .toString
            )
        )
    )

  def stubPPNSBoxId(boxResponseJson: String, clientId: String): Unit =
    stubFor(
      get(urlEqualTo(s"/box?clientId=$clientId&boxName=obligations%2Fdeclaration%2Fisa%2Freturn%23%231.0%23%23callbackUrl"))
        .willReturn(ok(boxResponseJson))
    )

  def stubSubmissionDeclaration(
    status:     ResponseDefinitionBuilder,
    zReference: String,
    taxYear:    String,
    monthInt:   Int,
    nilReturn:  Boolean = false
  ): Unit =
    stubFor(
      post(urlEqualTo(s"/disa-returns-submission/monthly/$zReference/$taxYear/$monthInt/declarations"))
        .withRequestBody(equalToJson(Json.toJson(ReportingNilReturn(nilReturn)).toString()))
        .willReturn(status)
    )

  def stubEnsureMonthlyReturnExists(status: Int, zReference: String, taxYear: String, monthInt: Int): Unit =
    stubFor(
      post(urlEqualTo(s"/disa-returns-submission/monthly/$zReference/$taxYear/$monthInt"))
        .willReturn(aResponse().withStatus(status))
    )

  def stubStoreMonthlyReturn(status: Int, zReference: String, taxYear: String, monthInt: Int): Unit =
    stubFor(
      put(urlPathMatching(s"/disa-returns-submission/monthly/$zReference/$taxYear/$monthInt/submissions/$submissionIdPattern"))
        .willReturn(aResponse().withStatus(status))
    )

  def stubStoreMonthlyReturnWithBodyAssert(
    status:             Int,
    zReference:         String,
    taxYear:            String,
    monthInt:           Int,
    expectedNdjsonLine: String
  ): Unit =
    stubFor(
      put(urlPathMatching(s"/disa-returns-submission/monthly/$zReference/$taxYear/$monthInt/submissions/$submissionIdPattern"))
        .withRequestBody(containing(expectedNdjsonLine))
        .willReturn(aResponse().withStatus(status))
    )

  val testClientId = "test-client-id"
  val testHeaders: Seq[(String, String)] = Seq(
    "X-Client-ID" -> testClientId,
    AUTHORIZATION -> "mock-bearer-token",
    CONTENT_TYPE  -> JSON
  )

}
