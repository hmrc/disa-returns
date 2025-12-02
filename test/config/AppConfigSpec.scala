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

import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.Configuration
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import utils.BaseUnitSpec

class AppConfigSpec extends BaseUnitSpec {

  private val configMap: Map[String, Any] = Map(
    "microservice.services.etmp.host" -> "etmp",
    "microservice.services.etmp.port" -> "1204",
    "urls.returnResultsLocation"      -> "/monthly/{isaManagerReference}/{taxYear}/{month}/results",
    "mongodb.timeToLive"              -> 30,
    "returnResultsRecordsPerPage"     -> 10
  )

  private val configuration  = Configuration.from(configMap)
  private val servicesConfig = new ServicesConfig(configuration)
  private val appConfig      = new AppConfig(servicesConfig)

  "AppConfig" should {

    "load the correct base URLs" in {
      appConfig.etmpBaseUrl mustBe "http://etmp:1204"
    }

    "read the mongodb timeToLive correctly" in {
      appConfig.timeToLive mustBe 30
    }

    "read the returnResultsRecordsPerPage correctly" in {
      appConfig.returnResultsRecordsPerPage mustBe 10
    }

    "calculate the number of pages for return results correctly" in {
      val pages1 = appConfig.getNoOfPagesForReturnResults(25)
      pages1 mustBe Some(3)

      val pages2 = appConfig.getNoOfPagesForReturnResults(0)
      pages2 mustBe Some(0)

      val pages3 = appConfig.getNoOfPagesForReturnResults(10)
      pages3 mustBe Some(1)

      val pages4 = appConfig.getNoOfPagesForReturnResults(1)
      pages4 mustBe Some(1)

      val pages5 = appConfig.getNoOfPagesForReturnResults(-10)
      pages5 mustBe None
    }
  }
}
