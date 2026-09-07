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
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.models.callback.ReconciliationReportReady
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReconciliationReportReadyRepository @Inject() (mc: MongoComponent, appConfig: AppConfig)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[ReconciliationReportReady](
      mongoComponent = mc,
      collectionName = "monthlyReturnsSummaries",
      domainFormat = ReconciliationReportReady.mongoFormat,
      indexes = Seq(
        IndexModel(
          keys = Indexes.ascending("zRef"),
          indexOptions = IndexOptions().unique(true).name("zRefIdx")
        ),
        IndexModel(
          keys = Indexes.ascending("updatedAt"),
          indexOptions = IndexOptions()
            .name("updatedAtTtlIdx")
            .expireAfter(appConfig.timeToLive.toLong, TimeUnit.DAYS)
        )
      )
    ) {

  def findByZReference(zReference: String): Future[Option[ReconciliationReportReady]] =
    collection.find(Filters.eq("zRef", zReference)).headOption()

  def upsert(reportReady: ReconciliationReportReady): Future[Unit] = {
    val now    = Instant.now()
    val filter = Filters.eq("zRef", reportReady.zRef)

    val setOnInsert = Updates.combine(
      Updates.setOnInsert("zRef", reportReady.zRef),
      Updates.setOnInsert("createdAt", now)
    )

    val setters = Updates.set("updatedAt", now)

    collection
      .updateOne(filter, Updates.combine(setOnInsert, setters), UpdateOptions().upsert(true))
      .toFuture()
      .map(_ => ())
  }

  def deleteByZReferences(zReferences: Seq[String]): Future[Unit] =
    collection
      .deleteMany(Filters.in("zRef", zReferences: _*))
      .toFuture()
      .map(_ => ())
}
