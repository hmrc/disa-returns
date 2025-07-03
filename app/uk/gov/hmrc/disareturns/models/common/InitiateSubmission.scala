package uk.gov.hmrc.disareturns.models.common

import play.api.libs.json.{Json, OFormat}

import java.util.UUID

case class InitiateSubmission(returnId: String, boxId: String, submissionRequest: SubmissionRequest)

object InitiateSubmission {
  implicit val format: OFormat[InitiateSubmission] = Json.format[InitiateSubmission]

  def create(boxId: String, submissionRequest: SubmissionRequest): InitiateSubmission =
    InitiateSubmission(UUID.randomUUID().toString, boxId, submissionRequest)
}
