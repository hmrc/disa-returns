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
import uk.gov.hmrc.disareturns.models.summary.repository.NotificationMetaData
import uk.gov.hmrc.disareturns.services.NotificationMetaDataService
import utils.BaseUnitSpec

import scala.concurrent.Future

class NotificationMetaDataServiceSpec extends BaseUnitSpec {

  private val service = new NotificationMetaDataService(mockNotificationMetaDataRepository)

  private val clientId = "client-123"
  private val boxId    = Some("box-456")
  private val metaData = NotificationMetaData(clientId, boxId, validZRef)

  override def beforeEach(): Unit = reset(mockNotificationMetaDataRepository)

  "NotificationMetaDataService#saveMetaData" should {

    "return Right when metadata is stored successfully" in {
      when(mockNotificationMetaDataRepository.insertNotificationMetaData(any[NotificationMetaData]))
        .thenReturn(Future.successful(Right(())))

      val result = await(service.saveMetaData(clientId, boxId, validZRef))

      verify(mockNotificationMetaDataRepository).insertNotificationMetaData(metaData)
      result shouldBe Right(())
    }

    "return InternalServerErr if mongo repository throws an exception" in {
      when(mockNotificationMetaDataRepository.insertNotificationMetaData(any[NotificationMetaData]))
        .thenReturn(Future.failed(new Exception("fail")))
      val result = await(service.saveMetaData(clientId, boxId, validZRef))
      result shouldBe Left(InternalServerErr())
    }
  }

  "NotificationMetaDataService#retrieveMetaData" should {
    "return Some(metadata) when found" in {
      when(mockNotificationMetaDataRepository.findNotificationMetaData(validZRef)).thenReturn(Future.successful(Some(metaData)))

      val result = await(service.retrieveMetaData(validZRef))

      verify(mockNotificationMetaDataRepository).findNotificationMetaData(validZRef)
      result shouldBe Some(metaData)
    }

    "return None when not found" in {
      when(mockNotificationMetaDataRepository.findNotificationMetaData(validZRef)).thenReturn(Future.successful(None))

      val result = await(service.retrieveMetaData(validZRef))

      verify(mockNotificationMetaDataRepository).findNotificationMetaData(validZRef)
      result shouldBe None
    }
  }
}
