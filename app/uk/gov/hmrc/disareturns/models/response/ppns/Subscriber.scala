package uk.gov.hmrc.disareturns.models.response.ppns

import play.api.libs.json.{Json, OFormat}

import java.time.Instant

case class Subscriber(
  subscribedDateTime: Instant,
  callBackUrl:        String,
  subscriptionType:   String
)

object Subscriber {
  implicit val format: OFormat[Subscriber] = Json.format[Subscriber]
}
