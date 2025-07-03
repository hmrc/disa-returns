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

package uk.gov.hmrc.disareturns.connectors.response

import play.api.http.Status.{OK, UNAUTHORIZED}
import play.api.libs.json._
import uk.gov.hmrc.http.{HttpReads, HttpResponse}

object EtmpObligationsReads {

  implicit val etmpObligationsReads: Reads[EtmpObligations] = Json.reads[EtmpObligations]

  // HttpReads to convert HttpResponse to EtmpObligations, supporting 200 and 401
  implicit val httpReadsEtmpObligations: HttpReads[EtmpObligations] = new HttpReads[EtmpObligations] {
    override def read(method: String, url: String, response: HttpResponse): EtmpObligations =
      response.status match {
        case OK | UNAUTHORIZED =>
          Json.parse(response.body).as[EtmpObligations]
        case status =>
          throw new RuntimeException(s"Unexpected response status: $status, body: ${response.body}")
      }
  }
}
