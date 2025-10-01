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

package models.summary

import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.libs.json.Json
import uk.gov.hmrc.disareturns.models.summary.request.MonthlyReturnsSummaryReq
import utils.BaseUnitSpec

class MonthlyReturnsSummaryReqSpec extends BaseUnitSpec {

  "MonthlyReturnsSummaryReq JSON" should {

    "read minimal body with totalRecords only" in {
      val m  = MonthlyReturnsSummaryReq(totalRecords = 3)
      val js = Json.toJson(m)
      js.as[MonthlyReturnsSummaryReq] mustBe m
    }

    "fail if total record is less than 0" in {
      val m      = MonthlyReturnsSummaryReq(totalRecords = -1)
      val js     = Json.toJson(m)
      val result = js.validate[MonthlyReturnsSummaryReq]

      val errors = result.asEither.swap.getOrElse(fail("Expected a Left"))
      errors.head._2.head.message.toLowerCase mustBe "error.min"
    }
  }
}
