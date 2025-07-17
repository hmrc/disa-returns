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

import uk.gov.hmrc.disareturns.models.initiate.inboundRequest.SubmissionRequest
import uk.gov.hmrc.disareturns.models.initiate.mongo.ReturnMetadata
import uk.gov.hmrc.disareturns.repositories.ReturnMetadataRepository

import javax.inject.Inject
import scala.concurrent.Future

class ReturnMetadataService @Inject() (repository: ReturnMetadataRepository) {

  def saveReturnMetadata(boxId: String, submissionRequest: SubmissionRequest, isaManagerReference: String): Future[String] = {
    val returnMetadata =
      ReturnMetadata.create(boxId = boxId, submissionRequest = submissionRequest, isaManagerReference = isaManagerReference)
    repository.insert(returnMetadata)
  }
}
