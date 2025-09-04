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

package uk.gov.hmrc.disareturns.config

import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}

@Singleton
class AppConfig @Inject() (config: ServicesConfig) {

  lazy val etmpBaseUrl: String = config.baseUrl(serviceName = "etmp")
  lazy val ppnsBaseUrl: String = config.baseUrl(serviceName = "ppns")

  private val returnResultsSummaryLocation: String =
    config.getString("urls.returnResultsSummaryLocation")

  def getReturnResultsSummaryLocation(isaManagerReference: String, returnId: String): String =
    returnResultsSummaryLocation
      .replace("{isaManagerReference}", isaManagerReference)
      .replace("{returnId}", returnId)
}
