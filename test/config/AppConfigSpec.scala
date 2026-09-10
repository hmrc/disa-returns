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

package config

import org.scalatest.matchers.must.Matchers.mustBe
import play.api.Configuration
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import utils.BaseUnitSpec

class AppConfigSpec extends BaseUnitSpec {

  private val configMap: Map[String, Any] = Map(
    "microservice.services.etmp.host"          -> "etmp",
    "microservice.services.etmp.port"          -> "1204",
    "microservice.services.internal-auth.host" -> "internal-auth",
    "microservice.services.internal-auth.port" -> "8470",
    "internal-auth.token"                      -> "valid-internal-auth-token-disa-returns",
    "urls.returnResultsLocation"               -> "/monthly/{zReference}/results",
    "mongodb.timeToLive"                       -> 30,
    "returnResults.defaultLimit"               -> 200,
    "returnResults.maxLimit"                   -> 1000
  )

  private val configuration  = Configuration.from(configMap)
  private val servicesConfig = new ServicesConfig(configuration)
  private val appConfig      = new AppConfig(configuration, servicesConfig)

  "AppConfig" should {

    "load the correct base URLs" in {
      appConfig.etmpBaseUrl mustBe "http://etmp:1204"
    }

    "read the mongodb timeToLive correctly" in {
      appConfig.timeToLive mustBe 30
    }

    "read the internal auth config correctly" in {
      appConfig.internalAuthUrl mustBe "http://internal-auth:8470"
      appConfig.internalAuthToken mustBe "valid-internal-auth-token-disa-returns"
    }

    "read the return results limits correctly" in {
      appConfig.returnResultsDefaultLimit mustBe 200
      appConfig.returnResultsMaxLimit mustBe 1000
    }
  }
}
