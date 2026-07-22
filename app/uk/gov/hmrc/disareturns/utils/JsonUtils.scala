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

package uk.gov.hmrc.disareturns.utils

import play.api.libs.json.{Format, Reads, Writes}

object JsonUtils {

  inline def enumFormat[E <: Enumeration & Singleton](using
    e: ValueOf[E]
  ): Format[e.value.Value] =
    Format(
      Reads.enumNameReads(e.value),
      Writes.enumNameWrites
    )

}
