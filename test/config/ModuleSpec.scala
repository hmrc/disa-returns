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
import uk.gov.hmrc.disareturns.controllers.actionBuilders.{AuthAction, AuthenticatedAuthAction, EnrolmentVerificationAuthAction}
import uk.gov.hmrc.disareturns.services.{ReportingPeriodSource, SystemReportingPeriodSource}
import uk.gov.hmrc.disareturns.testOnly.services.TestOnlySubmissionReportingPeriodSource
import uk.gov.hmrc.disareturns.utils.{LooseZReferenceValidator, StrictZReferenceValidator, ZReferenceValidator}

class ModuleSpec extends AnyWordSpec with Matchers {
  private val cursorEncryptionKey = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="

  "Module" should {
    "bind the submission reporting-period source only for the test-only router" in {
      val application = new GuiceApplicationBuilder()
        .configure(
          "application.router"                  -> "testOnlyDoNotUseInAppConf.Routes",
          "create-internal-auth-token-on-start" -> false,
          "cursor.encryption.key"               -> cursorEncryptionKey
        )
        .build()

      running(application) {
        application.injector.instanceOf[ReportingPeriodSource] shouldBe a[TestOnlySubmissionReportingPeriodSource]
      }
    }

    "bind the local system reporting-period source for the production router" in {
      val application = new GuiceApplicationBuilder()
        .configure(
          "create-internal-auth-token-on-start" -> false,
          "cursor.encryption.key"               -> cursorEncryptionKey
        )
        .build()

      running(application) {
        application.injector.instanceOf[ReportingPeriodSource] shouldBe a[SystemReportingPeriodSource]
      }
    }

    "bind the enrolment verification auth action when enrolment verification is enabled" in {
      val application = new GuiceApplicationBuilder()
        .configure(
          "create-internal-auth-token-on-start"     -> false,
          "features.enrolment-verification-enabled" -> true,
          "cursor.encryption.key"                   -> cursorEncryptionKey
        )
        .build()

      running(application) {
        application.injector.instanceOf[AuthAction] shouldBe a[EnrolmentVerificationAuthAction]
      }
    }

    "bind the authenticated auth action when enrolment verification is disabled" in {
      val application = new GuiceApplicationBuilder()
        .configure(
          "create-internal-auth-token-on-start"     -> false,
          "features.enrolment-verification-enabled" -> false,
          "cursor.encryption.key"                   -> cursorEncryptionKey
        )
        .build()

      running(application) {
        application.injector.instanceOf[AuthAction] shouldBe a[AuthenticatedAuthAction]
      }
    }

    "bind strict Z-reference validation by default" in {
      val application = new GuiceApplicationBuilder()
        .configure(
          "create-internal-auth-token-on-start" -> false,
          "cursor.encryption.key"               -> cursorEncryptionKey
        )
        .build()

      running(application) {
        application.injector.instanceOf[ZReferenceValidator] shouldBe a[StrictZReferenceValidator]
      }
    }

    "bind loose Z-reference validation when strict validation is disabled" in {
      val application = new GuiceApplicationBuilder()
        .configure(
          "create-internal-auth-token-on-start"            -> false,
          "features.strict-z-reference-validation-enabled" -> false,
          "cursor.encryption.key"                          -> cursorEncryptionKey
        )
        .build()

      running(application) {
        application.injector.instanceOf[ZReferenceValidator] shouldBe a[LooseZReferenceValidator]
      }
    }
  }
}
