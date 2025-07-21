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

package models

import play.api.libs.json._
import uk.gov.hmrc.disareturns.models.initiate.inboundRequest.SubmissionRequest
import uk.gov.hmrc.disareturns.models.initiate.mongo.ReturnMetadata
import utils.BaseUnitSpec

import java.time.{Instant, LocalDateTime, ZoneOffset}

class InitiateSubmissionSpec extends BaseUnitSpec {

  "InitiateSubmission" should {
    val boxId               = "5"
    val isaManagerReference = "Manager1"
    val jsonString =
      """{
        |"totalRecords": 400,
        |"submissionPeriod": "JAN",
        |"taxYear": 2025
        |}""".stripMargin

    "serialize and deserialize correctly" in {
      val localDateTime = LocalDateTime.of(2025, 7, 15, 16, 0, 0)
      val instantAt16   = localDateTime.toInstant(ZoneOffset.UTC)
      val json          = Json.parse(jsonString)
      val request       = json.as[SubmissionRequest]

      val submissionRequestWithReturnId =
        ReturnMetadata(boxId = boxId, submissionRequest = request, isaManagerReference = isaManagerReference, createdAt = instantAt16)

      val serialized   = Json.toJson(submissionRequestWithReturnId)
      val deserialized = serialized.validate[ReturnMetadata]

      deserialized shouldBe JsSuccess(submissionRequestWithReturnId)
    }

    "create a new instance with a UUID returnId" in {
      val json    = Json.parse(jsonString)
      val request = json.as[SubmissionRequest]
      val result  = ReturnMetadata(boxId = boxId, submissionRequest = request, isaManagerReference = isaManagerReference)

      result.boxId               shouldBe "5"
      result.submissionRequest   shouldBe request
      result.isaManagerReference shouldBe isaManagerReference
      result.returnId              should not be empty
      result.createdAt           shouldBe a[Instant]
    }
  }
}
