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

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status.OK
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.initiate.inboundRequest.InitiateSubmission
import uk.gov.hmrc.disareturns.repositories.InitiateSubmissionRepository
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec

class InitiateSubmissionControllerISpec extends BaseIntegrationSpec {

  implicit val mongo: InitiateSubmissionRepository = app.injector.instanceOf[InitiateSubmissionRepository]
  val isaManagerRef = "Z123456"
  val initiateUrl = s"/monthly/$isaManagerRef/init"
  val clientId = "test-client-id"

  val validRequestJson: JsObject = Json.obj(
    "totalRecords" -> 100,
    "submissionPeriod" -> "APR",
    "taxYear" -> 2025
  )

  def stubEtmpReportingWindow(status: Int, body: JsObject): Unit = {
    stubFor(get(urlEqualTo("/disa-returns-stubs/etmp/check-reporting-window"))
      .willReturn(aResponse().withStatus(status).withBody(body.toString)))
  }

  def stubEtmpObligation(status: Int, body: JsObject): Unit = {
    stubFor(get(urlEqualTo(s"/disa-returns-stubs/etmp/check-obligation-status/$isaManagerRef"))
      .willReturn(aResponse().withStatus(status).withBody(body.toString)))
  }

  val boxResponseJson =
    s"""
       |{
       |  "boxId": "boxId1",
       |  "boxName": "Test_Box",
       |  "boxCreator": {
       |    "clientId": "$clientId"
       |  },
       |  "applicationId": "applicationId"
       |}
       |""".stripMargin

  def stubPPNSBoxId(): Unit = {
    stubFor(get(urlEqualTo(s"/box?clientId=$clientId"))
      .willReturn(ok(boxResponseJson)))
  }

  def stubMongoSave(): Unit = {
    stubFor(post(urlEqualTo(s"/mongo/submissions"))
      .willReturn(ok(boxResponseJson)))
  }

  "POST /monthly/:isaManagerRef/init" should {

    "return 200 OK when the submission is valid and all services respond successfully" in {
      stubEtmpReportingWindow(status = OK, body = Json.obj("reportingWindowOpen" -> true))
      stubEtmpObligation(status = OK, body = Json.obj("obligationAlreadyMet" -> false))
      stubPPNSBoxId()

      val result = initiateRequest(validRequestJson)

      result.status shouldBe OK

      val mongoRecord: Option[InitiateSubmission] = await(mongo.findByIsaManagerReference(isaManagerRef))
      val returnId = mongoRecord.map(_.returnId).get

      (result.json \ "returnId").as[String] shouldBe returnId
      (result.json \ "action").as[String] shouldBe "SUBMIT_RETURN_TO_PAGINATED_API"
      (result.json \ "boxId").as[String] shouldBe "boxId1"
    }
  }

  def initiateRequest(requestBody: JsObject): WSResponse = {
    stubAuth()
    mongo.dropCollection()
    await(
      ws.url(
          s"http://localhost:$port/monthly/$isaManagerRef/init"
        ).withFollowRedirects(follow = false)
        .withHttpHeaders(
          ("X-Client-ID" -> clientId),
          ("Authorization" -> "mock-bearer-token")
        )
        .post(requestBody)
    )
  }
}
