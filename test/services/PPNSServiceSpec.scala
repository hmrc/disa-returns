/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mockito.Mockito._
import uk.gov.hmrc.disareturns.models.common.InternalServerErr
import uk.gov.hmrc.disareturns.models.summary.repository.NotificationMetaData
import uk.gov.hmrc.disareturns.services.PPNSService
import uk.gov.hmrc.http.UpstreamErrorResponse
import utils.BaseUnitSpec

import scala.concurrent.Future

class PPNSServiceSpec extends BaseUnitSpec {

  val testClientId = "123456"
  val testBoxId    = "Box1"

  val service = new PPNSService(mockPPNSConnector, mockNotificationMetaDataService)
  val notificationMetaData: NotificationMetaData = NotificationMetaData(clientId = testClientId, boxId = None, zRef = validZRef)

  "PPNSService.getBoxId" should {

    "return Right(Some(boxId)) when connector returns a box successfully" in {

      when(mockPPNSConnector.getBox(testClientId))
        .thenReturn(Future.successful(Right(Some(testBoxId))))

      val result = service.getBoxId(testClientId).futureValue

      result shouldBe Right(Some(testBoxId))
    }

    "return Right(None) when connector returns no box" in {
      when(mockPPNSConnector.getBox(testClientId))
        .thenReturn(Future.successful(Right(None)))

      val result = service.getBoxId(testClientId).futureValue

      result shouldBe Right(None)
    }

    "return Left(InternalServerErr) when connector returns an error" in {
      val error = UpstreamErrorResponse("Internal Server Error", 500)

      when(mockPPNSConnector.getBox(testClientId))
        .thenReturn(Future.successful(Left(error)))

      val result = service.getBoxId(testClientId).futureValue

      result shouldBe Left(InternalServerErr())
    }
  }

  "PPNService.sendNotification" should {

    "successfully send a notification" in {
      when(mockNotificationMetaDataService.retrieveMetaData(validZRef))
        .thenReturn(Future.successful(Some(notificationMetaData)))
      when(mockPPNSConnector.sendNotification(testBoxId, returnSummaryResults))
        .thenReturn(Future.successful(()))
      service.sendNotification(validZRef, returnSummaryResults).futureValue shouldBe ()

    }

    "successfully send a notification after retrieving the boxId from from ppns" in {
      when(mockNotificationMetaDataService.retrieveMetaData(validZRef))
        .thenReturn(Future.successful(Some(notificationMetaData)))
      when(mockPPNSConnector.getBox(notificationMetaData.clientId))
        .thenReturn(Future.successful(Right(Some(testBoxId))))
      when(mockPPNSConnector.sendNotification(testBoxId, returnSummaryResults))
        .thenReturn(Future.successful(()))
      service.sendNotification(validZRef, returnSummaryResults).futureValue shouldBe ()

    }

    "not send a notification when no notification meta data exists" in {
      when(mockNotificationMetaDataService.retrieveMetaData(validZRef))
        .thenReturn(Future.successful(None))
      service.sendNotification(validZRef, returnSummaryResults).futureValue shouldBe ()
    }

    "not send a notification after failing to retrieving a box is from ppns" in {
      when(mockNotificationMetaDataService.retrieveMetaData(validZRef))
        .thenReturn(Future.successful(Some(notificationMetaData)))
      when(mockPPNSConnector.getBox(notificationMetaData.clientId))
        .thenReturn(Future.successful(Right(None)))
      service.sendNotification(validZRef, returnSummaryResults).futureValue shouldBe ()

    }

    "not send a notification if ppns returns an upstream error response when attempting to retrieve a box" in {
      when(mockNotificationMetaDataService.retrieveMetaData(validZRef))
        .thenReturn(Future.successful(Some(notificationMetaData)))
      val error = UpstreamErrorResponse("Internal Server Error", 500)
      when(mockPPNSConnector.getBox(notificationMetaData.clientId))
        .thenReturn(Future.successful(Left(error)))
      service.sendNotification(validZRef, returnSummaryResults).futureValue shouldBe ()

    }
  }

  "PPNService.retrieveBoxId" should {
    "return None is no notification meta data exist" in {
      when(mockNotificationMetaDataService.retrieveMetaData(validZRef))
        .thenReturn(Future.successful(None))
      service.retrieveBoxId(validZRef).futureValue shouldBe None
    }

  }

}
