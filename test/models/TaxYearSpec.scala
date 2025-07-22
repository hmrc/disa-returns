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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import uk.gov.hmrc.disareturns.models.initiate.inboundRequest.TaxYear

import java.time.{Clock, Instant, LocalDate, ZoneId}

class TaxYearSpec extends AnyWordSpec with Matchers {

  "TaxYear" should {

    "successfully deserialize a valid tax year (current year based on date logic)" in {
      val now             = LocalDate.now()
      val cutoff          = LocalDate.of(now.getYear, 4, 6)
      val expectedTaxYear = if (now.isBefore(cutoff)) now.getYear - 1 else now.getYear

      val json   = JsNumber(expectedTaxYear)
      val result = Json.fromJson[TaxYear](json)

      result shouldBe JsSuccess(TaxYear(expectedTaxYear))
    }

    "fail to deserialize an invalid tax year (past taxYear)" in {
      val now         = LocalDate.now()
      val cutoff      = LocalDate.of(now.getYear, 4, 6)
      val invalidYear = if (now.isBefore(cutoff)) now.getYear else now.getYear - 1

      val json   = JsNumber(invalidYear)
      val result = Json.fromJson[TaxYear](json)

      result                                      shouldBe a[JsError]
      result.asEither.left.get.head._2.head.message should include("error.taxYear.in.past")
    }
    "fail to deserialize an invalid tax year (future taxYear)" in {
      val now         = LocalDate.now().plusYears(3)
      val cutoff      = LocalDate.of(now.getYear, 4, 6)
      val invalidYear = if (now.isBefore(cutoff)) now.getYear else now.getYear - 1

      val json   = JsNumber(invalidYear)
      val result = Json.fromJson[TaxYear](json)

      result                                      shouldBe a[JsError]
      result.asEither.left.get.head._2.head.message should include("error.taxYear.not.current")
    }

    "fail to deserialize a non-numeric input" in {
      val json   = JsString("not-a-number")
      val result = Json.fromJson[TaxYear](json)

      result                                      shouldBe a[JsError]
      result.asEither.left.get.head._2.head.message should include("error.taxYear.not.integer")
    }
    "fail to deserialize a non whole number input" in {
      val json   = JsNumber(2025.1)
      val result = Json.fromJson[TaxYear](json)

      result                                      shouldBe a[JsError]
      result.asEither.left.get.head._2.head.message should include("error.taxYear.not.whole.integer")
    }

    "successfully serialize a TaxYear to JSON number" in {
      val taxYear = TaxYear(2024)
      val json    = Json.toJson(taxYear)

      json shouldBe JsNumber(2024)
    }

    "correctly calculate currentTaxYear as now.getYear - 1 when before April 6" in {
      // Set a fixed date before April 6, e.g. March 15, 2025
      val fixedInstant = Instant.parse("2025-03-15T00:00:00Z")
      val fixedClock   = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

      val taxYear = TaxYear.currentTaxYear(fixedClock)

      taxYear shouldBe 2024
      val json   = JsNumber(2024)
      val result = Json.fromJson[TaxYear](json)
      result shouldBe JsError(List((JsPath, Seq(JsonValidationError("error.taxYear.in.past")))))
    }
  }
}
