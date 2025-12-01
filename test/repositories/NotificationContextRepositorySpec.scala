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
import uk.gov.hmrc.disareturns.models.summary.repository.NotificationContext
import uk.gov.hmrc.disareturns.repositories.NotificationContextRepository
import uk.gov.hmrc.mongo.MongoComponent
import utils.BaseUnitSpec

class NotificationContextRepositorySpec extends BaseUnitSpec {

  protected val databaseName:     String         = "notification-meta-data-test"
  protected val mongoUri:         String         = s"mongodb://127.0.0.1:27017/$databaseName"
  lazy val mongoComponentForTest: MongoComponent = MongoComponent(mongoUri)

  protected val repository: NotificationContextRepository =
    new NotificationContextRepository(mongoComponentForTest, mockAppConfig)

  val clientId = "client-123"
  val boxId:               Option[String]      = Some("box-456")
  val notificationContext: NotificationContext = NotificationContext(clientId, boxId, validZRef)

  override def beforeEach(): Unit =
    await(repository.collection.drop().toFuture())

  "findNotificationContext" should {
    "successfully return Some(NotificationContext)" in {

      await(repository.collection.insertOne(notificationContext).toFuture())
      await(repository.findNotificationContext(validZRef)) shouldBe Some(notificationContext)

    }

    "return None when no matching notificationContext exists" in {
      await(repository.findNotificationContext("Z0000")) shouldBe None
    }
  }

  "insertNotificationContext" should {
    "insert new notificationContext successfully" in {

      await(repository.insertNotificationContext(notificationContext))
      await(repository.findNotificationContext(validZRef)) shouldBe Some(notificationContext)

    }
  }

}
