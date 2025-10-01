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

package models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.mvc.Http.Status
import uk.gov.hmrc.disareturns.models.common.{InternalServerErr, UnauthorisedErr, UpstreamErrorMapper}
import uk.gov.hmrc.http.UpstreamErrorResponse

class UpstreamErrorMapperSpec extends AnyWordSpec with Matchers {

  "UpstreamErrorMapper.mapToErrorResponse" should {

    "map 401 response to Unauthorised" in {
      val err    = UpstreamErrorResponse("Unauthorised", Status.UNAUTHORIZED, Status.UNAUTHORIZED)
      val result = UpstreamErrorMapper.mapToErrorResponse(err)
      result shouldBe UnauthorisedErr
    }

    "map 500 response to InternalServerErr" in {
      val err    = UpstreamErrorResponse("Internal Server Error", Status.INTERNAL_SERVER_ERROR, Status.INTERNAL_SERVER_ERROR)
      val result = UpstreamErrorMapper.mapToErrorResponse(err)
      result shouldBe InternalServerErr()
    }

    "map 502 response to InternalServerErr" in {
      val err    = UpstreamErrorResponse("Bad Gateway", Status.BAD_GATEWAY, Status.BAD_GATEWAY)
      val result = UpstreamErrorMapper.mapToErrorResponse(err)
      result shouldBe InternalServerErr()
    }

    "map 503 response to InternalServerErr" in {
      val err    = UpstreamErrorResponse("Service Unavailable", Status.SERVICE_UNAVAILABLE, Status.SERVICE_UNAVAILABLE)
      val result = UpstreamErrorMapper.mapToErrorResponse(err)
      result shouldBe InternalServerErr()
    }

    "map other 4xx errors to InternalServerErr" in {
      val err    = UpstreamErrorResponse("Bad Request", Status.BAD_REQUEST, Status.BAD_REQUEST)
      val result = UpstreamErrorMapper.mapToErrorResponse(err)
      result shouldBe InternalServerErr()
    }

    "map unknown status codes to InternalServerErr" in {
      val err    = UpstreamErrorResponse("Some weird status", 207, 207)
      val result = UpstreamErrorMapper.mapToErrorResponse(err)
      result shouldBe InternalServerErr()
    }
  }
}
