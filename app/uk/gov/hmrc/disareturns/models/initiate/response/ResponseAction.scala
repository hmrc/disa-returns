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

package uk.gov.hmrc.disareturns.models.initiate.response

import play.api.libs.json.Format
import uk.gov.hmrc.disareturns.models.common.JsonUtils

object ResponseAction extends Enumeration {
  type ResponseAction = Value

  val SUBMIT_RETURN_TO_PAGINATED_API:        Value = Value("SUBMIT_RETURN_TO_PAGINATED_API")
  val NIL_RETURN_ACCEPTED_NO_FURTHER_ACTION: Value = Value("NIL_RETURN_ACCEPTED_NO_FURTHER_ACTION")

  implicit val format: Format[ResponseAction.Value] = JsonUtils.enumFormat(ResponseAction)

}
