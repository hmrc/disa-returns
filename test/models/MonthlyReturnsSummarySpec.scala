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

import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.libs.json._
import uk.gov.hmrc.disareturns.models.common.Month
import uk.gov.hmrc.disareturns.models.summary.repository.MonthlyReturnsSummary
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import utils.BaseUnitSpec

import java.time.Instant

class MonthlyReturnsSummarySpec extends BaseUnitSpec {

  implicit val instantFormat: Format[Instant]                = Format(MongoJavatimeFormats.instantReads, MongoJavatimeFormats.instantWrites)
  implicit val fmt:           OFormat[MonthlyReturnsSummary] = MonthlyReturnsSummary.mongoFormat

  "MonthlyReturnsSummary.mongoFormat" should {

    "write a case class to JSON with month as a string" in {
      val now = Instant.parse("2025-09-30T12:00:00Z")
      val model = MonthlyReturnsSummary(
        zRef = "Z1234",
        taxYearEnd = 2026,
        month = Month.SEP,
        totalRecords = 3,
        createdAt = now,
        updatedAt = now
      )

      val js = Json.toJson(model)

      (js \ "zRef").as[String] mustBe "Z1234"
      (js \ "taxYearEnd").as[Int] mustBe 2026
      (js \ "month").as[String] mustBe "SEP"
      (js \ "totalRecords").as[Int] mustBe 3
      (js \ "createdAt").as[Instant] mustBe now
      (js \ "updatedAt").as[Instant] mustBe now
    }

    "read JSON with month string back into the case class" in {
      val now = Instant.parse("2025-09-30T12:00:00Z")

      val js = Json.obj(
        "zRef"         -> "Z5678",
        "taxYearEnd"   -> 2027,
        "month"        -> "JAN",
        "totalRecords" -> 7,
        "createdAt"    -> now,
        "updatedAt"    -> now
      )

      val model = js.as[MonthlyReturnsSummary]

      model.zRef mustBe "Z5678"
      model.taxYearEnd mustBe 2027
      model.month mustBe Month.JAN
      model.totalRecords mustBe 7
      model.createdAt mustBe now
      model.updatedAt mustBe now
    }

    "round-trip successfully" in {
      val now   = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
      val model = MonthlyReturnsSummary("Z9999", 2028, Month.DEC, 42, now, now)

      val js   = Json.toJson(model)
      val back = js.as[MonthlyReturnsSummary]

      back mustBe model
    }

    "fail reads when month string is invalid" in {
      val js = Json.obj(
        "zRef"         -> "Z1234",
        "taxYearEnd"   -> 2026,
        "month"        -> "NOPE",
        "totalRecords" -> 1,
        "createdAt"    -> Instant.now(),
        "updatedAt"    -> Instant.now()
      )

      val res = js.validate[MonthlyReturnsSummary]
      res.isError mustBe true
    }
  }
}
