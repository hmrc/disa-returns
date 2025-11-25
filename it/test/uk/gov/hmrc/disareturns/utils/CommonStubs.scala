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
import play.api.http.Status
import play.api.http.Status.OK
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.disareturns.models.declaration.ReportingNilReturn

trait CommonStubs {

  def stubAuth(): Unit =
    stubFor {
      post("/auth/authorise")
        .willReturn {
          aResponse.withStatus(OK).withBody("{}")
        }
    }

  def stubAuthFail(): Unit =
    stubFor {
      post("/auth/authorise")
        .willReturn {
          aResponse()
            .withStatus(Status.UNAUTHORIZED)
            .withBody("{}")
        }
    }

  def stubEtmpReportingWindow(status: Int, body: JsObject): Unit =
    stubFor(
      get(urlEqualTo("/etmp/check-reporting-window"))
        .willReturn(aResponse().withStatus(status).withBody(body.toString))
    )

  def stubEtmpObligation(status: Int, body: JsObject, isaManagerRef: String): Unit =
    stubFor(
      get(urlEqualTo(s"/etmp/check-obligation-status/$isaManagerRef"))
        .willReturn(aResponse().withStatus(status).withBody(body.toString))
    )
  def stubCloseEtmpObligation(status: Int, body: JsObject, isaManagerRef: String): Unit =
    stubFor(
      post(urlEqualTo(s"/etmp/close-obligation-status/$isaManagerRef"))
        .willReturn(aResponse().withStatus(status).withBody(body.toString))
    )

  def stubPPNSBoxId(boxResponseJson: String, clientId: String): Unit =
    stubFor(
      get(urlEqualTo(s"/box?clientId=$clientId&boxName=obligations%2Fdeclaration%2Fisa%2Freturn%23%231.0%23%23callbackUrl"))
        .willReturn(ok(boxResponseJson))
    )

  def stubETMPDeclaration(status: ResponseDefinitionBuilder, isaManagerRef: String): Unit =
    stubFor(
      post(urlEqualTo(s"/etmp/declaration/$isaManagerRef"))
        .willReturn(status)
    )

  def stubNPSNotification(
    status:        ResponseDefinitionBuilder,
    isaManagerRef: String,
    nilReturn:     Boolean = false
  ): Unit =
    stubFor(
      post(urlEqualTo(s"/nps/declaration/$isaManagerRef"))
        .withRequestBody(equalToJson(Json.toJson(ReportingNilReturn(nilReturn)).toString()))
        .willReturn(status)
    )

  def stubNpsSubmission(status: Int, isaManagerRef: String): Unit =
    stubFor(
      post(urlEqualTo(s"/nps/submit/$isaManagerRef"))
        .willReturn(aResponse().withStatus(status))
    )

  def stubNpsSubmissionWithBodyAssert(
    status:             Int,
    isaManagerRef:      String,
    expectedJsonObject: String
  ): Unit = {
    val jsObj             = Json.parse(expectedJsonObject)
    val jsArray           = Json.arr(jsObj)
    val expectedArrayJson = Json.stringify(jsArray)
    stubFor(
      post(urlEqualTo(s"/nps/submit/$isaManagerRef"))
        .withRequestBody(equalToJson(expectedArrayJson, true, false))
        .willReturn(aResponse().withStatus(status))
    )
  }

  val testClientId = "test-client-id"
  val testHeaders: Seq[(String, String)] = Seq(
    "X-Client-ID"   -> testClientId,
    "Authorization" -> "mock-bearer-token",
    "Content-Type"  -> "application/json"
  )

}
