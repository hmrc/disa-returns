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

package services

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.common.InternalServerErr
import uk.gov.hmrc.disareturns.models.summary.repository.NotificationContext
import uk.gov.hmrc.disareturns.services.NotificationContextService
import utils.BaseUnitSpec

import scala.concurrent.Future

class NotificationContextServiceSpec extends BaseUnitSpec {

  private val service = new NotificationContextService(notificationContextRepository)

  private val clientId = "client-123"
  private val boxId    = Some("box-456")
  private val metaData = NotificationContext(clientId, boxId, validZRef)

  override def beforeEach(): Unit = reset(notificationContextRepository)

  "NotificationContextService#saveContext" should {

    "return Right when notificationContext is stored successfully" in {
      when(notificationContextRepository.insertNotificationContext(any[NotificationContext]))
        .thenReturn(Future.successful(Right(())))

      val result = await(service.saveContext(clientId, boxId, validZRef))

      verify(notificationContextRepository).insertNotificationContext(metaData)
      result shouldBe Right(())
    }

    "return InternalServerErr if mongo repository throws an exception" in {
      when(notificationContextRepository.insertNotificationContext(any[NotificationContext]))
        .thenReturn(Future.failed(new Exception("fail")))
      await(service.saveContext(clientId, boxId, validZRef)) shouldBe Left(InternalServerErr())

    }
  }

  "NotificationContextService#retrieveContext" should {
    "return Some(notificationContext) when found" in {
      when(notificationContextRepository.findNotificationContext(validZRef)).thenReturn(Future.successful(Some(metaData)))

      val result = await(service.retrieveContext(validZRef))

      verify(notificationContextRepository).findNotificationContext(validZRef)
      result shouldBe Some(metaData)
    }

    "return None when not found" in {
      when(notificationContextRepository.findNotificationContext(validZRef)).thenReturn(Future.successful(None))

      val result = await(service.retrieveContext(validZRef))

      verify(notificationContextRepository).findNotificationContext(validZRef)
      result shouldBe None
    }
  }
}
