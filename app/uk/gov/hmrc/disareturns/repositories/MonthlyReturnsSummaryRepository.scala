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

package uk.gov.hmrc.disareturns.repositories

import org.mongodb.scala.model._
import uk.gov.hmrc.disareturns.models.common.Month.Month
import uk.gov.hmrc.disareturns.models.summary.repository.MonthlyReturnsSummary
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MonthlyReturnsSummaryRepository @Inject() (mc: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[MonthlyReturnsSummary](
      mongoComponent = mc,
      collectionName = "monthlyReturnsSummaries",
      domainFormat = MonthlyReturnsSummary.format,
      indexes = Seq(
        IndexModel(
          keys = Indexes.ascending("zRef", "year", "month"),
          indexOptions = IndexOptions().unique(true).name("zRefYearMonthIdx")
        ),
        IndexModel(
          keys = Indexes.ascending("updatedAt"),
          indexOptions = IndexOptions()
            .name("updatedAtTtlIdx")
            .expireAfter(30, TimeUnit.DAYS)
        )
      )
    ) {

  def upsert(zRef: String, year: Int, month: Month, totalRecords: Int): Future[Unit] = {
    val now = Instant.now()
    val filter = Filters.and(
      Filters.eq("zRef", zRef),
      Filters.eq("year", year),
      Filters.eq("month", month.toString)
    )

    val setOnInsert = Updates.combine(
      Updates.setOnInsert("zRef", zRef),
      Updates.setOnInsert("year", year),
      Updates.setOnInsert("month", month.toString),
      Updates.setOnInsert("createdAt", now)
    )

    val setters = Updates.combine(
      Updates.set("totalRecords", totalRecords),
      Updates.set("updatedAt", now)
    )

    collection
      .updateOne(filter, Updates.combine(setOnInsert, setters), UpdateOptions().upsert(true))
      .toFuture()
      .map(_ => ())
  }
}
