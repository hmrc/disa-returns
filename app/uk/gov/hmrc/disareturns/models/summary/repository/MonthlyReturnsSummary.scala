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

package uk.gov.hmrc.disareturns.models.summary.repository

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json._
import uk.gov.hmrc.disareturns.models.common.Month
import uk.gov.hmrc.disareturns.models.common.Month.Month
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

case class MonthlyReturnsSummary(
  zRef:         String,
  taxYear:      String,
  month:        Month,
  totalRecords: Int,
  createdAt:    Instant = Instant.now(),
  updatedAt:    Instant = Instant.now()
)

object MonthlyReturnsSummary {
  implicit val format: OFormat[MonthlyReturnsSummary] = Json.format[MonthlyReturnsSummary]

  val mongoFormat: OFormat[MonthlyReturnsSummary] = {
    implicit val instantFormat: Format[Instant] = Format(MongoJavatimeFormats.instantReads, MongoJavatimeFormats.instantWrites)

    val r: Reads[MonthlyReturnsSummary] = (
      (__ \ "zRef").read[String] and
        (__ \ "taxYear").read[String] and
        (__ \ "month").format[Month.Value] and
        (__ \ "totalRecords").read[Int] and
        (__ \ "createdAt").read[Instant] and
        (__ \ "updatedAt").read[Instant]
    )(MonthlyReturnsSummary.apply _)

    val w: OWrites[MonthlyReturnsSummary] = OWrites { m =>
      Json.obj(
        "zRef"         -> m.zRef,
        "taxYear"      -> m.taxYear,
        "month"        -> m.month.toString,
        "totalRecords" -> m.totalRecords,
        "createdAt"    -> m.createdAt,
        "updatedAt"    -> m.updatedAt
      )
    }

    OFormat(r, w)
  }

  //TODO: are the custom reads/writes required here?
}
