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

package repositories
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.summary.repository.NotificationMetaData
import uk.gov.hmrc.disareturns.repositories.NotificationMetaDataRepository
import uk.gov.hmrc.mongo.MongoComponent
import utils.BaseUnitSpec

class NotificationMetaDataRepositorySpec extends BaseUnitSpec {

  protected val databaseName:     String         = "notification-meta-data-test"
  protected val mongoUri:         String         = s"mongodb://127.0.0.1:27017/$databaseName"
  lazy val mongoComponentForTest: MongoComponent = MongoComponent(mongoUri)

  protected val repository: NotificationMetaDataRepository =
    new NotificationMetaDataRepository(mongoComponentForTest)

  val clientId = "client-123"
  val boxId:                Option[String]       = Some("box-456")
  val notificationMetaData: NotificationMetaData = NotificationMetaData(clientId, boxId, validZRef)

  override def beforeEach(): Unit =
    await(repository.collection.drop().toFuture())

  "findNotificationMetaData" should {
    "find notificationMetaData with zRef" in {

      await(repository.collection.insertOne(notificationMetaData).toFuture())

      val result = await(repository.findNotificationMetaData(validZRef))

      result shouldBe Some(notificationMetaData)
    }

    "return None when no matching notificationMetaData exists" in {
      val result = await(repository.findNotificationMetaData("Z0000"))
      result shouldBe None
    }
  }

  "insertNotificationMetaData" should {
    "insert new notificationMetaData successfully" in {
      await(repository.insertNotificationMetaData(notificationMetaData))

      val result = await(repository.findNotificationMetaData(validZRef))

      result shouldBe Some(notificationMetaData)
    }
  }

}
