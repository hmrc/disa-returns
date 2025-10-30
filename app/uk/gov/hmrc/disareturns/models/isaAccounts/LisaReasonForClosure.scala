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

package uk.gov.hmrc.disareturns.models.isaAccounts

import play.api.libs.json.Format
import uk.gov.hmrc.disareturns.models.common.JsonUtils

object LisaReasonForClosure extends Enumeration {
  type LisaReasonForClosure = Value

  val CANCELLED:           Value = Value("CANCELLED")
  val CLOSED:              Value = Value("CLOSED")
  val VOID:                Value = Value("VOID")
  val TRANSFERRED_IN_FULL: Value = Value("TRANSFERRED_IN_FULL")
  val ALL_FUNDS_WITHDRAWN: Value = Value("ALL_FUNDS_WITHDRAWN")

  implicit val format: Format[LisaReasonForClosure.Value] = JsonUtils.enumFormat(LisaReasonForClosure)

}
