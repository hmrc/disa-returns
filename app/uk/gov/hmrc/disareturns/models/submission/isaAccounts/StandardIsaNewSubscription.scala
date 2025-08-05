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

package uk.gov.hmrc.disareturns.models.submission.isaAccounts

import uk.gov.hmrc.disareturns.models.submission.isaAccounts.IsaType.IsaType
import play.api.libs.json._
import play.api.libs.functional.syntax._

import java.time.LocalDate

case class StandardIsaNewSubscription(
  accountNumber:                       String,
  nino:                                String,
  firstName:                           String,
  middleName:                          Option[String],
  lastName:                            String,
  dateOfBirth:                         LocalDate,
  isaType:                             IsaType,
  reportingATransfer:                  Boolean,
  dateOfLastSubscription:              LocalDate,
  totalCurrentYearSubscriptionsToDate: BigDecimal,
  marketValueOfAccount:                BigDecimal,
  flexibleIsa:                         Boolean
) extends IsaAccount

object StandardIsaNewSubscription {

  implicit val reads: Reads[StandardIsaNewSubscription] = Reads { json =>
    if (!(json \ "flexibleIsa").asOpt[Boolean].contains(true))
      JsError("flexibleIsa must be true")
    else {
      (
        (__ \ "accountNumber").read[String] and
          (__ \ "nino").read[String] and
          (__ \ "firstName").read[String] and
          (__ \ "middleName").readNullable[String] and
          (__ \ "lastName").read[String] and
          (__ \ "dateOfBirth").read[LocalDate] and
          (__ \ "isaType").read[IsaType] and
          (__ \ "reportingATransfer")
            .read[Boolean]
            .filter(JsonValidationError("reportingATransfer must be false"))(_ == false) and
          (__ \ "dateOfLastSubscription").read[LocalDate] and
          (__ \ "totalCurrentYearSubscriptionsToDate").read[BigDecimal] and
          (__ \ "marketValueOfAccount").read[BigDecimal] and
          (__ \ "flexibleIsa").read[Boolean]
        )(StandardIsaNewSubscription.apply _).reads(json)
    }
  }

  implicit val format: OFormat[StandardIsaNewSubscription] =
    OFormat(reads, Json.writes[StandardIsaNewSubscription])
}

