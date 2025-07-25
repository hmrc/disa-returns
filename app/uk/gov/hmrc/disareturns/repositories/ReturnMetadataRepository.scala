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

import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import uk.gov.hmrc.disareturns.models.initiate.mongo.ReturnMetadata
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReturnMetadataRepository @Inject() (mc: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[ReturnMetadata](
      mongoComponent = mc,
      collectionName = "initiateSubmission",
      domainFormat = ReturnMetadata.format,
      indexes = Seq(
        IndexModel(
          keys = Indexes.ascending("returnId"),
          indexOptions = IndexOptions().unique(true).name("returnIdUniqueIdx")
        ),
        IndexModel(
          keys = Indexes.ascending("createdAt"),
          indexOptions = IndexOptions()
            .name("createdAtTtlIdx")
            .expireAfter(30, TimeUnit.DAYS)
        )
      )
    ) {

  def insert(initiateSubmission: ReturnMetadata): Future[String] =
    collection.insertOne(initiateSubmission).toFuture().map(_ => initiateSubmission.returnId)

  def findByIsaManagerReference(isaManagerReference: String): Future[Option[ReturnMetadata]] =
    collection.find(equal("isaManagerReference", isaManagerReference)).headOption()

  def dropCollection(): Future[Unit] =
    collection.drop().toFuture().map(_ => ())
}
