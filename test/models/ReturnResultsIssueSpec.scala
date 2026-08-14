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

import play.api.libs.json.Json
import uk.gov.hmrc.disareturns.models.returnResults.{IssueOverSubscribed, IssueWithMessage, ReturnResultsIssue}
import utils.BaseUnitSpec

class ReturnResultsIssueSpec extends BaseUnitSpec {

  "ReturnResultsIssue JSON format" should {

    "round-trip IssueWithMessage" in {
      val issue = IssueWithMessage("GENERIC_ERROR", "Something went wrong")

      val json = Json.toJson(issue)
      (json \ "code").as[String]    shouldBe "GENERIC_ERROR"
      (json \ "message").as[String] shouldBe "Something went wrong"

      val parsed = json.as[ReturnResultsIssue]
      parsed shouldBe issue
    }

    "round-trip IssueOverSubscribed" in {
      val issue = IssueOverSubscribed("OVER_SUBSCRIBED", BigDecimal(1823.76))

      val json = Json.toJson(issue)
      (json \ "code").as[String]                     shouldBe "OVER_SUBSCRIBED"
      (json \ "overSubscribedAmount").as[BigDecimal] shouldBe BigDecimal(1823.76)

      val parsed = json.as[ReturnResultsIssue]
      parsed shouldBe issue
    }

    "deserialise any other code JSON as IssueWithMessage" in {
      val json = Json.parse(
        """{
          | "code": "INVALID_ACCOUNT",
          | "message": "Account not found"
          |}""".stripMargin
      )

      val parsed = json.as[ReturnResultsIssue]
      parsed shouldBe IssueWithMessage("INVALID_ACCOUNT", "Account not found")
    }
  }
}
