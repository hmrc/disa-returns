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
import uk.gov.hmrc.disareturns.models.common.Month.Month
import uk.gov.hmrc.disareturns.models.summary.repository.{MonthlyData, MonthlyReturnsSummary}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MonthlyDataRepository @Inject()(mc: MongoComponent, appConfig: AppConfig)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[MonthlyData](
      mongoComponent = mc,
      collectionName = "monthlyData",
      domainFormat = MonthlyData.mongoFormat,
      indexes = Seq.empty
    ) {

  def insert(monthlyData: MonthlyData): Future[Unit] =
    collection
      .insertOne(monthlyData)
      .toFuture()
      .map(_ => ())

}
