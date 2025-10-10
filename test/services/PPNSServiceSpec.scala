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
import uk.gov.hmrc.disareturns.services.PPNSService
import uk.gov.hmrc.http.UpstreamErrorResponse
import utils.BaseUnitSpec

import scala.concurrent.Future

class PPNSServiceSpec extends BaseUnitSpec {

  val testClientId = "123456"

  val service = new PPNSService(mockPPNSConnector)

  "PPNSService.getBoxId" should {

    "return Right(Some(boxId)) when connector returns a box successfully" in {
      val boxId = "box_1"

      when(mockPPNSConnector.getBox(testClientId))
        .thenReturn(Future.successful(Right(Some(boxId))))

      val result = service.getBoxId(testClientId).futureValue

      result shouldBe Right(Some(boxId))
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

}
