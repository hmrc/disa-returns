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

import com.google.inject.{AbstractModule, Provides}
import com.typesafe.config.Config
import org.apache.pekko.actor.ActorSystem
import play.api.libs.concurrent.Futures
import uk.gov.hmrc.disareturns.AppInitialiser
import uk.gov.hmrc.disareturns.controllers.actionBuilders.{AuthAction, AuthenticatedAuthAction, EnrolmentVerificationAuthAction}
import uk.gov.hmrc.disareturns.services.{ReportingPeriodSource, SystemReportingPeriodSource}
import uk.gov.hmrc.disareturns.testOnly.services.TestOnlySubmissionReportingPeriodSource
import uk.gov.hmrc.disareturns.utils.{LooseZReferenceValidator, StrictZReferenceValidator, ZReferenceValidator}
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.{Provider, Singleton}
import java.time.Clock
import scala.concurrent.ExecutionContext

class Module extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[AppConfig]).asEagerSingleton()
    bind(classOf[AppInitialiser]).asEagerSingleton()
  }

  @Provides
  @Singleton
  def provideClock(): Clock = Clock.systemUTC()

  @Provides
  @Singleton
  def provideReportingPeriodSource(
    config:                   Config,
    systemSource:             Provider[SystemReportingPeriodSource],
    submissionOverrideSource: Provider[TestOnlySubmissionReportingPeriodSource]
  ): ReportingPeriodSource =
    if (config.hasPath("application.router") && config.getString("application.router") == "testOnlyDoNotUseInAppConf.Routes") {
      submissionOverrideSource.get()
    } else {
      systemSource.get()
    }

  @Provides
  @Singleton
  def provideAuthAction(
    config:                          Config,
    enrolmentVerificationAuthAction: EnrolmentVerificationAuthAction,
    authenticatedAuthAction:         AuthenticatedAuthAction
  ): AuthAction =
    if (config.getBoolean("features.enrolment-verification-enabled")) enrolmentVerificationAuthAction
    else authenticatedAuthAction

  @Provides
  @Singleton
  def provideZReferenceValidator(
    config:          Config,
    strictValidator: Provider[StrictZReferenceValidator],
    looseValidator:  Provider[LooseZReferenceValidator]
  ): ZReferenceValidator =
    if (config.getBoolean("features.strict-z-reference-validation-enabled")) strictValidator.get()
    else looseValidator.get()

  @Provides
  @Singleton
  def provideInternalAuthTokenInitialiser(
    actorSystem: ActorSystem,
    config:      Config,
    appConfig:   AppConfig,
    httpClient:  HttpClientV2,
    futures:     Futures,
    ec:          ExecutionContext
  ): InternalAuthTokenInitialiser =
    if (config.getBoolean("create-internal-auth-token-on-start")) {
      new InternalAuthTokenInitialiserImpl(actorSystem, appConfig, config, httpClient, futures)(ec)
    } else {
      new NoOpInternalAuthTokenInitialiser()
    }
}
