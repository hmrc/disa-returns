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

package uk.gov.hmrc.disareturns.mongoRepositories

import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Updates.{addEachToSet, combine, setOnInsert}
import org.mongodb.scala.model.{FindOneAndUpdateOptions, IndexModel, IndexOptions, Indexes}
import play.api.libs.json.Json
import uk.gov.hmrc.disareturns.models.MonthlyReportDocument
import uk.gov.hmrc.disareturns.models.isaAccounts.IsaAccount
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ReportingRepository @Inject() (mc: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[MonthlyReportDocument](
      mongoComponent = mc,
      collectionName = "reportingRepository",
      domainFormat = MonthlyReportDocument.format,
      indexes = Seq(IndexModel(
        keys = Indexes.ascending("returnId"),
        indexOptions = IndexOptions().unique(true)
      ))
    ) {



  def insertOrUpdate(isaManagerId: String, returnId: String, reports: Seq[IsaAccount]): Future[Unit] = {
    val documents: Seq[Document] = reports.map { isaAccount =>
      Document(Json.stringify(Json.toJson(isaAccount)))
    }

    val update = combine(
      addEachToSet("isaReport", documents: _*),
      setOnInsert("returnId", returnId),
      setOnInsert("isaManagerReferenceNumber", isaManagerId)
    )

    collection
      .findOneAndUpdate(
        filter = equal("returnId", returnId),
        update = update,
        options = FindOneAndUpdateOptions().upsert(true)
      )
      .toFuture()
      .map(_ => ())
  }


  def dropCollection(): Future[Unit] = {
    collection.drop().toFuture().map(_ => ())
  }
}
