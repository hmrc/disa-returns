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

import com.google.inject.Inject
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
import uk.gov.hmrc.disareturns.models.submission.isaAccounts.{IsaAccount, MonthlyReportDocument}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MonthlyReportDocumentRepository @Inject() (mc: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[MonthlyReportDocument](
      mongoComponent = mc,
      collectionName = "reportingRepository",
      domainFormat = MonthlyReportDocument.format,
      indexes = Seq(
        IndexModel(
          keys = Indexes.ascending("createdAt"),
          indexOptions = IndexOptions()
            .name("createdAtTtlIdx")
            .expireAfter(30, TimeUnit.DAYS)
        )
      )
    ) {

  def dropCollection(): Future[Unit] =
    collection.drop().toFuture().map(_ => ())

  def insertBatch(isaManagerId: String, returnId: String, reports: Seq[IsaAccount]): Future[Unit] = {
    val wrapperJson = MonthlyReportDocument(returnId = returnId, isaManagerReferenceNumber = isaManagerId, isaReport = reports)
    collection.insertOne(wrapperJson).toFuture().map(_ => ())
  }

  def countByIsaManagerReferenceAndReturnId(
    isaManagerReference: String,
    returnId:            String
  ): Future[Long] = {
    val filter = Filters.and(
      Filters.equal("isaManagerReferenceNumber", isaManagerReference),
      Filters.equal("returnId", returnId)
    )
    collection.find(filter).toFuture().map(_.map(_.isaReport.size).sum)
  }

}
