package models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.mvc.Http.Status
import uk.gov.hmrc.disareturns.models.errors.connector.responses._
import uk.gov.hmrc.http.UpstreamErrorResponse

class UpstreamErrorMapperSpec extends AnyWordSpec with Matchers {

  "UpstreamErrorMapper.mapToErrorResponse" should {

    "map 401 response to Unauthorised" in {
      val err = UpstreamErrorResponse("Unauthorised", Status.UNAUTHORIZED, Status.UNAUTHORIZED)
      val result = UpstreamErrorMapper.mapToErrorResponse(err)
      result shouldBe Unauthorised
    }

    "map 500 response to InternalServerErr" in {
      val err = UpstreamErrorResponse("Internal Server Error", Status.INTERNAL_SERVER_ERROR, Status.INTERNAL_SERVER_ERROR)
      val result = UpstreamErrorMapper.mapToErrorResponse(err)
      result shouldBe InternalServerErr
    }

    "map 502 response to InternalServerErr" in {
      val err = UpstreamErrorResponse("Bad Gateway", Status.BAD_GATEWAY, Status.BAD_GATEWAY)
      val result = UpstreamErrorMapper.mapToErrorResponse(err)
      result shouldBe InternalServerErr
    }

    "map 503 response to InternalServerErr" in {
      val err = UpstreamErrorResponse("Service Unavailable", Status.SERVICE_UNAVAILABLE, Status.SERVICE_UNAVAILABLE)
      val result = UpstreamErrorMapper.mapToErrorResponse(err)
      result shouldBe InternalServerErr
    }

    "map other 4xx errors to InternalServerErr" in {
      val err = UpstreamErrorResponse("Bad Request", Status.BAD_REQUEST, Status.BAD_REQUEST)
      val result = UpstreamErrorMapper.mapToErrorResponse(err)
      result shouldBe InternalServerErr
    }

    "map unknown status codes to InternalServerErr" in {
      val err = UpstreamErrorResponse("Some weird status", 207, 207) // e.g., Multi-Status (unusual)
      val result = UpstreamErrorMapper.mapToErrorResponse(err)
      result shouldBe InternalServerErr
    }
  }
}


