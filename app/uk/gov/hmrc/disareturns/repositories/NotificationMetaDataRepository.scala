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
import play.api.Logging
import uk.gov.hmrc.disareturns.models.common.ErrorResponse
import uk.gov.hmrc.disareturns.models.summary.repository.NotificationMetaData
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NotificationMetaDataRepository @Inject() (mc: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[NotificationMetaData](
      mongoComponent = mc,
      collectionName = "notificationMetaData",
      domainFormat = NotificationMetaData.mongoFormat,
      indexes = Seq(
        IndexModel(
          keys = Indexes.ascending("zRef"),
          indexOptions = IndexOptions().unique(true).name("notificationMetaDataIdx")
        ),
        IndexModel(
          keys = Indexes.ascending("updatedAt"),
          indexOptions = IndexOptions()
            .name("updatedAtTtlIdx")
            .expireAfter(30L, TimeUnit.DAYS)
        )
      )
    )
    with Logging {

  def findNotificationMetaData(zRef: String): Future[Option[NotificationMetaData]] =
    collection.find(Filters.eq("zRef", zRef)).headOption()

  def insertNotificationMetaData(metaData: NotificationMetaData): Future[Either[ErrorResponse, Unit]] =
    collection
      .replaceOne(
        filter = Filters.eq("zRef", metaData.zRef),
        replacement = metaData,
        options = ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => Right())

}
