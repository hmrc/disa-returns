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

package models.callback

import play.api.libs.json.Json
import uk.gov.hmrc.disareturns.models.callback.ReconciliationReportReady
import utils.BaseUnitSpec

import java.time.Instant
import java.time.temporal.ChronoUnit

class ReconciliationReportReadySpec extends BaseUnitSpec {

  "ReconciliationReportReady.mongoFormat" should {
    "round-trip callback data and its timestamps" in {
      val now   = Instant.now().truncatedTo(ChronoUnit.MILLIS)
      val model = ReconciliationReportReady(validZReference, 42, now, now)

      Json.toJson(model).as[ReconciliationReportReady] shouldBe model
    }
  }
}
