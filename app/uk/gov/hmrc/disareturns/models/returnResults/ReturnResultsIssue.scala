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

package uk.gov.hmrc.disareturns.models.returnResults

import play.api.libs.json._

sealed trait ReturnResultsIssue {
  def code: String
}

case class IssueWithMessage(
                                   code: String,
                                   message: String
                                 ) extends ReturnResultsIssue

case class IssueOverSubscribed(
                                          code: String,
                                          overSubscribedAmount: BigDecimal
                                        ) extends ReturnResultsIssue

object ReturnResultsIssue {
  implicit val messageFormat: OFormat[IssueWithMessage] =
    Json.format[IssueWithMessage]

  implicit val overSubscribedFormat: OFormat[IssueOverSubscribed] =
    Json.format[IssueOverSubscribed]

  implicit val format: Format[ReturnResultsIssue] = new Format[ReturnResultsIssue] {
    override def reads(json: JsValue): JsResult[ReturnResultsIssue] =
      (json \ "code").validate[String].flatMap {
        case "OVER_SUBSCRIBED" => json.validate[IssueOverSubscribed]
        case _                 => json.validate[IssueWithMessage]
      }

    override def writes(issue: ReturnResultsIssue): JsValue = issue match {
      case overSubscribed: IssueOverSubscribed => overSubscribedFormat.writes(overSubscribed)
      case message: IssueWithMessage               => messageFormat.writes(message)
    }
  }
}
