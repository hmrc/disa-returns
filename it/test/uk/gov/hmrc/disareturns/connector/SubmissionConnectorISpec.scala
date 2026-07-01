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

package uk.gov.hmrc.disareturns.connector

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.http.Status._
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.connectors.SubmissionConnector
import uk.gov.hmrc.disareturns.models.common.Month
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec
import uk.gov.hmrc.disareturns.utils.WiremockHelper._

class SubmissionConnectorISpec extends BaseIntegrationSpec {

  private val taxYear  = "2026-27"
  private val month    = Month.SEP
  private val monthInt = month.id + 1

  private val declarationsUrl = s"/disa-returns-submission/monthly/$validZReference/$taxYear/$monthInt/declarations"
  private val createUrl       = s"/disa-returns-submission/monthly/$validZReference/$taxYear/$monthInt"
  private val submissionsUrl  = s"/disa-returns-submission/monthly/$validZReference/$taxYear/$monthInt/submissions"

  private val connector: SubmissionConnector = app.injector.instanceOf[SubmissionConnector]

  "SubmissionConnector.sendDeclaration" should {

    "return Right(HttpResponse) when disa-returns-submission returns 200 OK" in {
      stubPost(declarationsUrl, OK, "")

      val Right(response) = await(connector.sendDeclaration(validZReference, taxYear, month, nilReturnReported = false).value)

      response.status shouldBe OK
    }

    "return Left(UpstreamErrorResponse) preserving 422 status when disa-returns-submission returns 422" in {
      val body = """{"code":"NO_SUBMISSION_DATA","error":"Cannot declare with nilReturn as false when no monthly return data has been submitted"}"""
      stubPost(declarationsUrl, UNPROCESSABLE_ENTITY, body)

      val Left(err) = await(connector.sendDeclaration(validZReference, taxYear, month, nilReturnReported = false).value)

      err.statusCode shouldBe UNPROCESSABLE_ENTITY
      err.message    should include(body)
    }

    "return Left(UpstreamErrorResponse) when disa-returns-submission returns 500" in {
      stubPost(declarationsUrl, INTERNAL_SERVER_ERROR, """{"error":"Internal server error"}""")

      val Left(err) = await(connector.sendDeclaration(validZReference, taxYear, month, nilReturnReported = false).value)

      err.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "SubmissionConnector.createMonthlyReturn" should {

    "return Right(()) when disa-returns-submission returns 201 Created" in {
      stubPost(createUrl, CREATED, """{"submissionId":"abc-123"}""")

      val Right(()) = await(connector.createMonthlyReturn(validZReference, taxYear, month, nilReturn = false))
    }

    "return Left(UpstreamErrorResponse) preserving 409 status when disa-returns-submission returns 409 Conflict" in {
      stubPost(createUrl, CONFLICT, """{"submissionId":"existing-id"}""")

      val Left(err) = await(connector.createMonthlyReturn(validZReference, taxYear, month, nilReturn = false))

      err.statusCode shouldBe CONFLICT
    }

    "return Left(UpstreamErrorResponse) when disa-returns-submission returns 503" in {
      stubPost(createUrl, SERVICE_UNAVAILABLE, "")

      val Left(err) = await(connector.createMonthlyReturn(validZReference, taxYear, month, nilReturn = false))

      err.statusCode shouldBe SERVICE_UNAVAILABLE
    }
  }

  "SubmissionConnector.sendMonthlyReturn" should {

    val ndjsonSource: Source[ByteString, _] = Source.single(ByteString("""{"nino":"AB000001C","accountNumber":"STD000001"}""" + "\n"))

    "return Right(()) when disa-returns-submission returns 200 OK" in {
      stubPost(submissionsUrl, OK, "")

      val Right(()) = await(connector.sendMonthlyReturn(validZReference, taxYear, month, ndjsonSource))
    }

    "return Left(UpstreamErrorResponse) when disa-returns-submission returns 503" in {
      stubPost(submissionsUrl, SERVICE_UNAVAILABLE, "")

      val Left(err) = await(connector.sendMonthlyReturn(validZReference, taxYear, month, ndjsonSource))

      err.statusCode shouldBe SERVICE_UNAVAILABLE
    }
  }
}
