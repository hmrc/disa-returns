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

import cats.data.EitherT
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import play.api.http.Status.{NO_CONTENT, OK}
import play.api.libs.json.Json
import uk.gov.hmrc.disareturns.models.common.*
import uk.gov.hmrc.disareturns.models.returnResults.{IssueWithMessage, ReconciliationReport, ReconciliationReportResponse, ReturnResults}
import uk.gov.hmrc.disareturns.services.{CursorCrypto, NPSService}
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import utils.BaseUnitSpec

import scala.concurrent.Future

class NPSServiceSpec extends BaseUnitSpec {

  private val cursorCrypto       = mock[CursorCrypto]
  private val service            = new NPSService(mockNPSConnector, cursorCrypto)
  private val reportingNilReturn = false
  private val limit              = 200
  private val resultRecord       = ReturnResults("1", "a", IssueWithMessage("code", "message"))

  "NPSService.notification" should {
    "return the connector response" in {
      val httpResponse = HttpResponse(204, "")
      when(mockNPSConnector.sendNotification(validZReference, reportingNilReturn))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      service.notification(validZReference, reportingNilReturn).value.futureValue shouldBe Right(httpResponse)
    }

    "map connector errors" in {
      val error = UpstreamErrorResponse("Not authorised to access this service", 401, 401, Map.empty)
      when(mockNPSConnector.sendNotification(validZReference, reportingNilReturn))
        .thenReturn(EitherT.leftT[Future, HttpResponse](error))

      service.notification(validZReference, reportingNilReturn).value.futureValue shouldBe Left(UnauthorisedErr)
    }
  }

  "NPSService.retrieveReconciliationReport" should {
    "decrypt the public cursor and encrypt the downstream next cursor" in {
      when(cursorCrypto.decrypt("public-cursor", validZReference, validTaxYear, validMonth.toString, limit)).thenReturn(Right("raw-cursor"))
      when(cursorCrypto.encrypt("raw-next-cursor", validZReference, validTaxYear, validMonth.toString, limit)).thenReturn("public-next-cursor")
      stubReport(ReconciliationReportResponse(Seq(resultRecord), Some("raw-next-cursor")), Some("raw-cursor"))

      val result = service
        .retrieveReconciliationReport(validZReference, validTaxYear, validMonth, Some("public-cursor"), limit)
        .futureValue

      result shouldBe Right(ReconciliationReport(Seq(resultRecord), Some("public-next-cursor")))
    }

    "return final results without a cursor or page metadata" in {
      stubReport(ReconciliationReportResponse(Seq(resultRecord), None), None)

      val result = service.retrieveReconciliationReport(validZReference, validTaxYear, validMonth, None, limit).futureValue

      result                    shouldBe Right(ReconciliationReport(Seq(resultRecord), None))
      Json.toJson(result.value) shouldBe Json.obj("returnResults" -> Json.arr(Json.toJson(resultRecord)))
    }

    "allow an empty final result set" in {
      stubReport(ReconciliationReportResponse(Nil, None), None)

      service.retrieveReconciliationReport(validZReference, validTaxYear, validMonth, None, limit).futureValue shouldBe
        Right(ReconciliationReport(Nil, None))
    }

    "reject a cursor that cannot be decrypted without calling NPS" in {
      clearInvocations(mockNPSConnector)
      when(cursorCrypto.decrypt("tampered", validZReference, validTaxYear, validMonth.toString, limit)).thenReturn(Left(InvalidCursorErr))

      service.retrieveReconciliationReport(validZReference, validTaxYear, validMonth, Some("tampered"), limit).futureValue shouldBe
        Left(InvalidCursorErr)
      verify(mockNPSConnector, never()).retrieveReconciliationReport(any, any, any, any, any)(any)
    }

    "map downstream report and cursor errors" in {
      Seq("REPORT_NOT_FOUND" -> ReportNotFoundErr, "INVALID_CURSOR" -> InvalidCursorErr).foreach { case (message, expected) =>
        when(mockNPSConnector.retrieveReconciliationReport(any, any, any, any, any)(any))
          .thenReturn(EitherT.leftT[Future, HttpResponse](UpstreamErrorResponse(message, 404)))

        service.retrieveReconciliationReport(validZReference, validTaxYear, validMonth, None, limit).futureValue shouldBe Left(expected)
      }
    }

    "return an internal error for an unexpected status or invalid JSON" in {
      when(mockNPSConnector.retrieveReconciliationReport(any, any, any, any, any)(any))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(NO_CONTENT, "")))
      service.retrieveReconciliationReport(validZReference, validTaxYear, validMonth, None, limit).futureValue shouldBe Left(InternalServerErr())

      when(mockNPSConnector.retrieveReconciliationReport(any, any, any, any, any)(any))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(OK, "bad json")))
      service.retrieveReconciliationReport(validZReference, validTaxYear, validMonth, None, limit).futureValue shouldBe Left(InternalServerErr())
    }
  }

  private def stubReport(response: ReconciliationReportResponse, cursor: Option[String]): Unit = {
    val httpResponse = HttpResponse(OK, Json.toJson(response).toString())
    when(
      mockNPSConnector.retrieveReconciliationReport(
        eqTo(validZReference),
        eqTo(validTaxYear),
        eqTo(validMonth),
        eqTo(cursor),
        eqTo(limit)
      )(any)
    ).thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))
  }
}
