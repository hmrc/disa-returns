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

package uk.gov.hmrc.disareturns.services

import cats.data.EitherT
import uk.gov.hmrc.disareturns.connectors.ETMPConnector
import uk.gov.hmrc.disareturns.connectors.response.{EtmpObligations, EtmpReportingWindow}
import uk.gov.hmrc.disareturns.models.common.UpstreamErrorMapper.mapToErrorResponse
import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ETMPService @Inject() (connector: ETMPConnector)(implicit ec: ExecutionContext) {

  def getReportingWindowStatus()(implicit hc: HeaderCarrier): EitherT[Future, ErrorResponse, EtmpReportingWindow] =
    EitherT {
      connector.getReportingWindowStatus.value.map {
        case Left(upstreamError) => Left(mapToErrorResponse(upstreamError))
        case Right(response) =>
          response.json
            .validate[EtmpReportingWindow]
            .fold(
              _ => Left(InternalServerErr()),
              reportingWindow => Right(reportingWindow)
            )
      }
    }

  def getObligationStatus(isaManagerReferenceNumber: String)(implicit hc: HeaderCarrier): EitherT[Future, ErrorResponse, EtmpObligations] =
    EitherT {
      connector.getReturnsObligationStatus(isaManagerReferenceNumber).value.map {
        case Left(upstreamError) => Left(mapToErrorResponse(upstreamError))
        case Right(response) =>
          response.json
            .validate[EtmpObligations]
            .fold(
              _ => Left(InternalServerErr()),
              obligation => Right(obligation)
            )
      }
    }

  def validateEtmpSubmissionEligibility(
    isaManagerReferenceNumber: String
  )(implicit hc:               HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorResponse, (EtmpReportingWindow, EtmpObligations)]] =
    for {
      reportingWindowEither <- getReportingWindowStatus().value
      obligationsEither     <- getObligationStatus(isaManagerReferenceNumber).value
    } yield for {
      reportingWindow <- reportingWindowEither
      obligations     <- obligationsEither
      _ <- {
        val errors: Seq[ErrorResponse] = Seq(
          if (!reportingWindow.reportingWindowOpen) Some(ReportingWindowClosed) else None,
          if (obligations.obligationAlreadyMet) Some(ObligationClosed) else None
        ).flatten

        errors match {
          case Nil                => Right(())
          case singleError :: Nil => Left(singleError)
          case multipleErrors     => Left(MultipleErrorResponse(errors = multipleErrors))
        }
      }
    } yield (reportingWindow, obligations)

  def declaration(isaManagerReference: String)(implicit hc: HeaderCarrier): EitherT[Future, ErrorResponse, HttpResponse] =
    connector.sendDeclaration(isaManagerReference).leftMap(mapToErrorResponse)

}
