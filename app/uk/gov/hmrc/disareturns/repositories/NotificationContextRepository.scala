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
import uk.gov.hmrc.disareturns.models.summary.repository.NotificationContext
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NotificationContextRepository @Inject() (mc: MongoComponent, appConfig: AppConfig)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[NotificationContext](
      mongoComponent = mc,
      collectionName = "notificationContext",
      domainFormat = NotificationContext.mongoFormat,
      indexes = Seq(
        IndexModel(
          keys = Indexes.ascending("zReference"),
          indexOptions = IndexOptions().unique(true).name("notificationContextIdx")
        ),
        IndexModel(
          keys = Indexes.ascending("updatedAt"),
          indexOptions = IndexOptions()
            .name("updatedAtTtlIdx")
            .expireAfter(appConfig.timeToLive, TimeUnit.DAYS)
        )
      ),
      replaceIndexes = true
    ) {

  def findNotificationContext(zReference: String): Future[Option[NotificationContext]] =
    collection.find(Filters.eq("zReference", zReference)).headOption()

  def insertNotificationContext(notificationContext: NotificationContext): Future[Unit] =
    collection
      .replaceOne(
        filter = Filters.eq("zReference", notificationContext.zReference),
        replacement = notificationContext,
        options = ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => ())
}
