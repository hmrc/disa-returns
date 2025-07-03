package uk.gov.hmrc.disareturns.models.response.ppns

import play.api.libs.json.{Json, OFormat}

case class BoxCreator(clientId: String)

object BoxCreator {
  implicit val format: OFormat[BoxCreator] = Json.format[BoxCreator]
}
