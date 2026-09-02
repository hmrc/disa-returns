/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.running
import uk.gov.hmrc.disareturns.services.{ReportingPeriodSource, SystemReportingPeriodSource}
import uk.gov.hmrc.disareturns.testOnly.services.TestOnlySubmissionReportingPeriodSource

class ModuleSpec extends AnyWordSpec with Matchers {

  "Module" should {
    "bind the submission reporting-period source only for the test-only router" in {
      val application = new GuiceApplicationBuilder()
        .configure(
          "application.router"                  -> "testOnlyDoNotUseInAppConf.Routes",
          "create-internal-auth-token-on-start" -> false
        )
        .build()

      running(application) {
        application.injector.instanceOf[ReportingPeriodSource] shouldBe a[TestOnlySubmissionReportingPeriodSource]
      }
    }

    "bind the local system reporting-period source for the production router" in {
      val application = new GuiceApplicationBuilder()
        .configure("create-internal-auth-token-on-start" -> false)
        .build()

      running(application) {
        application.injector.instanceOf[ReportingPeriodSource] shouldBe a[SystemReportingPeriodSource]
      }
    }
  }
}
