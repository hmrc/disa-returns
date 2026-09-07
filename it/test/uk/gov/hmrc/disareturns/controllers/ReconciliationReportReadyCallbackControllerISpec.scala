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
import play.api.libs.ws.WSBodyWritables.writeableOf_String
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.*
import uk.gov.hmrc.disareturns.repositories.ReconciliationReportReadyRepository
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec

class ReconciliationReportReadyCallbackControllerISpec extends BaseIntegrationSpec {
  private lazy val repo   = app.injector.instanceOf[ReconciliationReportReadyRepository]
  private val invalidZRef = "Z1111000000000"

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repo.collection.drop().toFuture())
  }

  "POST /callback/monthly/:zReference" should {
    "persist report readiness by Z-reference" in {
      val result = callback(validZReference)
      result.status shouldBe NO_CONTENT

      val stored = await(repo.collection.find().toFuture())
      stored                should have size 1
      stored.head.zRef    shouldBe validZReference
      stored.head.createdAt should be <= stored.head.updatedAt
    }

    "replace report readiness while preserving its creation timestamp" in {
      callback(validZReference).status shouldBe NO_CONTENT
      val first = await(repo.collection.find().head())
      callback(validZReference).status shouldBe NO_CONTENT
      val second = await(repo.collection.find().head())

      second.zRef      shouldBe validZReference
      second.createdAt shouldBe first.createdAt
      second.updatedAt   should be >= first.updatedAt
    }

    "retain Z-reference validation" in {
      callback(invalidZRef).status shouldBe BAD_REQUEST
    }
  }

  private def callback(zReference: String): WSResponse =
    await(ws.url(s"http://localhost:$port/callback/monthly/$zReference").post(""))
}
