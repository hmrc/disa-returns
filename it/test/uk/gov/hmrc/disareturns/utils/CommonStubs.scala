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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, post, stubFor, urlEqualTo}
import play.api.http.Status.OK
import play.api.libs.json.JsObject

trait CommonStubs {

  def stubAuth(): Unit =
    stubFor {
      post("/auth/authorise")
        .willReturn {
          aResponse.withStatus(OK).withBody("{}")
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

  val testClientId = "test-client-id"
  val testHeaders: Seq[(String, String)] = Seq(
    "X-Client-ID"   -> testClientId,
    "Authorization" -> "mock-bearer-token"
  )
}
