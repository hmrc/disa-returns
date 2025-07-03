package uk.gov.hmrc.disareturns.models.response.ppns

import play.api.libs.json.{Json, OFormat}

case class Box(boxId: String, boxName: String, boxCreator: BoxCreator, applicationId: Option[String] = None, subscriber: Option[Subscriber] = None)

object Box {
  implicit val format: OFormat[Box] = Json.format[Box]
}
