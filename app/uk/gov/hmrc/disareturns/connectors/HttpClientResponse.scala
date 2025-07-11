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

package uk.gov.hmrc.disareturns.connectors

import cats.data.EitherT
import com.google.inject.Inject
import play.api.Logging
import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.http.{HttpException, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class HttpClientResponse @Inject() ()(implicit ec: ExecutionContext) extends Logging {

  def read[A <: HttpResponse](response: Future[Either[UpstreamErrorResponse, A]]): EitherT[Future, UpstreamErrorResponse, A] = {
    val recoveredResponse: Future[Either[UpstreamErrorResponse, A]] = response.recover {
      case ex: HttpException =>
        logger.error(ex.message)
        Left(UpstreamErrorResponse(s"Unexpected error: ${ex.getMessage}", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR))
      case ex =>
        logger.error(s"Unexpected error: ${ex.getMessage}", ex)
        Left(UpstreamErrorResponse(s"Unexpected error: ${ex.getMessage}", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR))
    }

    EitherT(recoveredResponse.map {
      case Right(httpResponse) if httpResponse.status >= 400 =>
        val msg = s"Received error status ${httpResponse.status} with body: ${httpResponse.body}"
        logger.warn(msg)
        Left(UpstreamErrorResponse(msg, httpResponse.status, httpResponse.status))
      case Right(success) =>
        Right(success)
      case Left(error) =>
        logger.error(error.message)
        Left(error)
    })
  }
}