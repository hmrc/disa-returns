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

package uk.gov.hmrc.disareturns.models.initiate.mongo

import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{OFormat, __}
import uk.gov.hmrc.disareturns.models.initiate.inboundRequest.SubmissionRequest
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant
import java.util.UUID

case class ReturnMetadata(
  returnId:            String = UUID.randomUUID().toString,
  boxId:               String,
  submissionRequest:   SubmissionRequest,
  isaManagerReference: String,
  createdAt:           Instant = Instant.now()
)

object ReturnMetadata {

  private val reads = (
    (__ \ "returnId").read[String] and
      (__ \ "boxId").read[String] and
      (__ \ "submissionRequest").read[SubmissionRequest] and
      (__ \ "isaManagerReference").read[String] and
      (__ \ "createdAt").read[Instant](MongoJavatimeFormats.instantFormat)
  )(ReturnMetadata.apply _)

  private val writes = (
    (__ \ "returnId").write[String] and
      (__ \ "boxId").write[String] and
      (__ \ "submissionRequest").write[SubmissionRequest] and
      (__ \ "isaManagerReference").write[String] and
      (__ \ "createdAt").write[Instant](MongoJavatimeFormats.instantFormat)
  )(unlift(ReturnMetadata.unapply))

  implicit val format: OFormat[ReturnMetadata] = OFormat(reads, writes)

}
