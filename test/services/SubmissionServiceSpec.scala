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
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.http.Status._
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.services.{AuditService, SubmissionService}
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import utils.BaseUnitSpec

import java.nio.file.Files
import java.util.UUID
import scala.concurrent.Future

class SubmissionServiceSpec extends BaseUnitSpec {

  val mockAuditService: AuditService = mock[AuditService]
  val service           = new SubmissionService(mockSubmissionConnector, mockUuidGenerator, mockAuditService)
  val nilReturnReported = false
  val submissionId      = UUID.fromString("11111111-1111-4111-8111-111111111111")
  val internalReturnId  = "2f1611e2-0df4-4ddd-b666-b93554e6c85c"
  val createdResponse: HttpResponse = HttpResponse(CREATED, s"""{"submissionId":"$internalReturnId"}""")

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuditService)
    when(mockAuditService.auditApiUploadStarted(any(), any(), any(), any())(any())).thenReturn(Future.successful(()))
  }

  "SubmissionService.declare" should {

    "return Right(HttpResponse) when the connector returns a successful response" in {
      val httpResponse = HttpResponse(OK, "")
      when(mockSubmissionConnector.sendDeclaration(validZReference, validTaxYear, validMonth, nilReturnReported))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val result = service.declare(validZReference, validTaxYear, validMonth, nilReturnReported).value.futureValue

      result shouldBe Right(httpResponse)
    }

    "return Left(UnauthorisedErr) when the connector returns a 401 UpstreamErrorResponse" in {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = UNAUTHORIZED,
        reportAs = UNAUTHORIZED,
        headers = Map.empty
      )

      when(mockSubmissionConnector.sendDeclaration(validZReference, validTaxYear, validMonth, nilReturnReported))
        .thenReturn(EitherT.leftT[Future, HttpResponse](exception))

      val result = service.declare(validZReference, validTaxYear, validMonth, nilReturnReported).value.futureValue

      result shouldBe Left(UnauthorisedErr)
    }

    "return Left(InternalServerErr) when the connector returns a 500 UpstreamErrorResponse" in {
      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Internal server error",
        statusCode = INTERNAL_SERVER_ERROR,
        reportAs = INTERNAL_SERVER_ERROR,
        headers = Map.empty
      )

      when(mockSubmissionConnector.sendDeclaration(validZReference, validTaxYear, validMonth, nilReturnReported))
        .thenReturn(EitherT.leftT[Future, HttpResponse](exception))

      val result = service.declare(validZReference, validTaxYear, validMonth, nilReturnReported).value.futureValue

      result shouldBe Left(InternalServerErr())
    }

    "return Right(HttpResponse) when declaring with nilReturn true" in {
      val httpResponse = HttpResponse(200, "")
      when(mockSubmissionConnector.sendDeclaration(validZReference, validTaxYear, validMonth, true))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](httpResponse))

      val result = service.declare(validZReference, validTaxYear, validMonth, nilReturnReported = true).value.futureValue

      result shouldBe Right(httpResponse)
    }
  }

  "SubmissionService.submitMonthlyReturn" should {

    "return Right(()) when both connector calls return a successful response" in {
      val tempFile = Files.createTempFile("test-monthly-return", ".ndjson")
      Files.writeString(tempFile, """{"nino":"AB000001C","accountNumber":"STD000001"}""" + "\n")

      when(mockSubmissionConnector.createMonthlyReturn(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(createdResponse)))
      when(mockUuidGenerator.randomUuid()).thenReturn(submissionId)
      when(mockSubmissionConnector.sendSubmission(any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(())))

      val result = service.submitMonthlyReturn(validZReference, testIsaManagerDetails, validTaxYear, validMonth, tempFile).futureValue

      Files.deleteIfExists(tempFile)
      result shouldBe Right(())
      verify(mockUuidGenerator).randomUuid()
      verify(mockSubmissionConnector)
        .sendSubmission(eqTo(validZReference), eqTo(validTaxYear), eqTo(validMonth), eqTo(submissionId), any())(any())
      verify(mockAuditService)
        .auditApiUploadStarted(
          eqTo(validZReference),
          eqTo(testIsaManagerDetails),
          eqTo(ReportingPeriod(validTaxYear, validMonth)),
          eqTo(internalReturnId)
        )(any())
    }

    "return Right(()) when createMonthlyReturn returns 409 (monthly return already exists)" in {
      val tempFile = Files.createTempFile("test-monthly-return", ".ndjson")
      Files.writeString(tempFile, """{"nino":"AB000001C","accountNumber":"STD000001"}""" + "\n")

      val conflict = UpstreamErrorResponse(
        message = """{"submissionId":"existing-id"}""",
        statusCode = CONFLICT,
        reportAs = CONFLICT,
        headers = Map.empty
      )

      when(mockSubmissionConnector.createMonthlyReturn(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Left(conflict)))
      when(mockUuidGenerator.randomUuid()).thenReturn(submissionId)
      when(mockSubmissionConnector.sendSubmission(any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(())))

      val result = service.submitMonthlyReturn(validZReference, testIsaManagerDetails, validTaxYear, validMonth, tempFile).futureValue

      Files.deleteIfExists(tempFile)
      result shouldBe Right(())
      verify(mockUuidGenerator).randomUuid()
      verify(mockSubmissionConnector)
        .sendSubmission(eqTo(validZReference), eqTo(validTaxYear), eqTo(validMonth), eqTo(submissionId), any())(any())
      verify(mockAuditService, never()).auditApiUploadStarted(any(), any(), any(), any())(any())
    }

    "still submit when the create monthly return response has no submissionId, without auditing APIUploadStarted" in {
      val tempFile = Files.createTempFile("test-monthly-return", ".ndjson")
      Files.writeString(tempFile, """{"nino":"AB000001C","accountNumber":"STD000001"}""" + "\n")

      when(mockSubmissionConnector.createMonthlyReturn(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(HttpResponse(CREATED, ""))))
      when(mockUuidGenerator.randomUuid()).thenReturn(submissionId)
      when(mockSubmissionConnector.sendSubmission(any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(())))

      val result = service.submitMonthlyReturn(validZReference, testIsaManagerDetails, validTaxYear, validMonth, tempFile).futureValue

      Files.deleteIfExists(tempFile)
      result shouldBe Right(())
      verify(mockAuditService, never()).auditApiUploadStarted(any(), any(), any(), any())(any())
    }

    "still submit when auditing APIUploadStarted fails" in {
      val tempFile = Files.createTempFile("test-monthly-return", ".ndjson")
      Files.writeString(tempFile, """{"nino":"AB000001C","accountNumber":"STD000001"}""" + "\n")

      when(mockAuditService.auditApiUploadStarted(any(), any(), any(), any())(any()))
        .thenReturn(Future.failed(new RuntimeException("audit failed")))
      when(mockSubmissionConnector.createMonthlyReturn(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(createdResponse)))
      when(mockUuidGenerator.randomUuid()).thenReturn(submissionId)
      when(mockSubmissionConnector.sendSubmission(any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(())))

      val result = service.submitMonthlyReturn(validZReference, testIsaManagerDetails, validTaxYear, validMonth, tempFile).futureValue

      Files.deleteIfExists(tempFile)
      result shouldBe Right(())
    }

    "return Left(InternalServerErr) when createMonthlyReturn returns a 500 UpstreamErrorResponse" in {
      val tempFile = Files.createTempFile("test-monthly-return", ".ndjson")
      Files.writeString(tempFile, """{"nino":"AB000001C","accountNumber":"STD000001"}""" + "\n")

      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Internal server error",
        statusCode = INTERNAL_SERVER_ERROR,
        reportAs = INTERNAL_SERVER_ERROR,
        headers = Map.empty
      )

      when(mockSubmissionConnector.createMonthlyReturn(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Left(exception)))

      val result = service.submitMonthlyReturn(validZReference, testIsaManagerDetails, validTaxYear, validMonth, tempFile).futureValue

      Files.deleteIfExists(tempFile)
      result shouldBe Left(InternalServerErr())
      verify(mockUuidGenerator, never()).randomUuid()
      verify(mockSubmissionConnector, never()).sendSubmission(any(), any(), any(), any(), any())(any())
    }

    "return Left(UnauthorisedErr) when sendSubmission returns a 401 UpstreamErrorResponse" in {
      val tempFile = Files.createTempFile("test-monthly-return", ".ndjson")
      Files.writeString(tempFile, """{"nino":"AB000001C","accountNumber":"STD000001"}""" + "\n")

      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Not authorised to access this service",
        statusCode = UNAUTHORIZED,
        reportAs = UNAUTHORIZED,
        headers = Map.empty
      )

      when(mockSubmissionConnector.createMonthlyReturn(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(createdResponse)))
      when(mockUuidGenerator.randomUuid()).thenReturn(submissionId)
      when(mockSubmissionConnector.sendSubmission(any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Left(exception)))

      val result = service.submitMonthlyReturn(validZReference, testIsaManagerDetails, validTaxYear, validMonth, tempFile).futureValue

      Files.deleteIfExists(tempFile)
      result shouldBe Left(UnauthorisedErr)
    }

    "return Left(InternalServerErr) when sendSubmission returns a 500 UpstreamErrorResponse" in {
      val tempFile = Files.createTempFile("test-monthly-return", ".ndjson")
      Files.writeString(tempFile, """{"nino":"AB000001C","accountNumber":"STD000001"}""" + "\n")

      val exception: UpstreamErrorResponse = UpstreamErrorResponse(
        message = "Internal server error",
        statusCode = INTERNAL_SERVER_ERROR,
        reportAs = INTERNAL_SERVER_ERROR,
        headers = Map.empty
      )

      when(mockSubmissionConnector.createMonthlyReturn(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(createdResponse)))
      when(mockUuidGenerator.randomUuid()).thenReturn(submissionId)
      when(mockSubmissionConnector.sendSubmission(any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Left(exception)))

      val result = service.submitMonthlyReturn(validZReference, testIsaManagerDetails, validTaxYear, validMonth, tempFile).futureValue

      Files.deleteIfExists(tempFile)
      result shouldBe Left(InternalServerErr())
    }
  }
}
