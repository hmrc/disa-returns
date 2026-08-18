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

import org.mongodb.scala.ObservableFuture
import play.api.libs.json.*
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.*
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.models.summary.repository.MonthlyReturnsSummary
import uk.gov.hmrc.disareturns.repositories.MonthlyReturnsSummaryRepository
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec

class ReturnsSummaryControllerISpec extends BaseIntegrationSpec {
  private lazy val repo      = app.injector.instanceOf[MonthlyReturnsSummaryRepository]
  private lazy val appConfig = app.injector.instanceOf[AppConfig]
  private val totalRecords   = 3
  private val invalidZRef    = "Z1111000000000"

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repo.collection.drop().toFuture())
  }

  "POST /callback/monthly/:zReference" should {
    "persist the current summary by Z-reference" in {
      val result = callback(validZReference, Json.obj("totalRecords" -> totalRecords))
      result.status shouldBe NO_CONTENT

      val stored = await(repo.collection.find().toFuture())
      stored should have size 1
      stored.head.zRef shouldBe validZReference
      stored.head.totalRecords shouldBe totalRecords
    }

    "replace the current summary while preserving its creation timestamp" in {
      callback(validZReference, Json.obj("totalRecords" -> 1)).status shouldBe NO_CONTENT
      val first = await(repo.collection.find().head())
      callback(validZReference, Json.obj("totalRecords" -> 4)).status shouldBe NO_CONTENT
      val second = await(repo.collection.find().head())

      second.totalRecords shouldBe 4
      second.createdAt shouldBe first.createdAt
      second.updatedAt should be >= first.updatedAt
    }

    "retain Z-reference and body validation" in {
      callback(invalidZRef, Json.obj("totalRecords" -> 1)).status shouldBe BAD_REQUEST
      callback(validZReference, Json.obj()).status shouldBe BAD_REQUEST
    }
  }

  "GET /monthly/:zReference/results/summary" should {
    "return the current summary with a periodless results location" in {
      await(repo.collection.insertOne(MonthlyReturnsSummary(validZReference, totalRecords)).toFuture())
      stubAuth()
      val result = getSummary(validZReference)

      result.status shouldBe OK
      (result.json \ "returnResultsLocation").as[String] shouldBe s"${appConfig.selfHost}/monthly/$validZReference/results?page=0"
      (result.json \ "totalRecords").as[Int] shouldBe totalRecords
    }

    "return not found or reject an invalid Z-reference" in {
      stubAuth()
      val notFound = getSummary(validZReference)
      notFound.status shouldBe NOT_FOUND
      (notFound.json \ "message").as[String] shouldBe s"No return found for $validZReference"
      getSummary(invalidZRef).status shouldBe BAD_REQUEST
    }
  }

  private def getSummary(zReference: String): WSResponse =
    await(ws.url(s"http://localhost:$port/monthly/$zReference/results/summary").withHttpHeaders(testHeaders: _*).get())

  private def callback(zReference: String, body: JsObject): WSResponse =
    await(ws.url(s"http://localhost:$port/callback/monthly/$zReference").post(body))
}
