/*
 * Copyright 2023 HM Revenue & Customs
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

package utils

import org.mockito.Mockito.reset
import org.apache.pekko.actor.ActorSystem
import org.scalatest.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import com.typesafe.config.Config
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.test.DefaultAwaitTimeout
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.connectors.*
import uk.gov.hmrc.disareturns.repositories.{MonthlyReturnsSummaryRepository, NotificationContextRepository}
import uk.gov.hmrc.disareturns.services.*
import uk.gov.hmrc.disareturns.utils.UuidGenerator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}

import scala.concurrent.ExecutionContext

abstract class BaseUnitSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with EitherValues
    with ScalaFutures
    with MockitoSugar
    with DefaultAwaitTimeout
    with GuiceOneAppPerSuite
    with TestMocks
    with MockAuthConnector
    with utils.TestData {

  implicit val ec:      ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val hc:      HeaderCarrier    = HeaderCarrier()
  lazy val retryConfig: Config           = app.configuration.underlying
  lazy val actorSystem: ActorSystem      = app.actorSystem

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset[AnyRef](
      mockAuthConnector,
      mockETMPService,
      mockNPSService,
      mockPPNSService,
      mockSubmissionConnector,
      mockSubmissionService,
      mockUuidGenerator,
      mockNotificationContextService,
      mockReturnsSummaryService,
      mockReportingWindowService
    )
  }

  //MOCKS
  val mockHttpClient:                    HttpClientV2                    = mock[HttpClientV2]
  val mockAppConfig:                     AppConfig                       = mock[AppConfig]
  val mockRequestBuilder:                RequestBuilder                  = mock[RequestBuilder]
  val mockPPNSService:                   PPNSService                     = mock[PPNSService]
  val mockPPNSConnector:                 PPNSConnector                   = mock[PPNSConnector]
  val mockETMPConnector:                 ETMPConnector                   = mock[ETMPConnector]
  val mockETMPService:                   ETMPService                     = mock[ETMPService]
  val mockBaseConnector:                 BaseConnector                   = mock[BaseConnector]
  val mockStreamingParserService:        StreamingParserService          = mock[StreamingParserService]
  val mockReturnsSummaryService:         ReturnsSummaryService           = mock[ReturnsSummaryService]
  val mockReturnsSummaryRepository:      MonthlyReturnsSummaryRepository = mock[MonthlyReturnsSummaryRepository]
  val mockNPSConnector:                  NPSConnector                    = mock[NPSConnector]
  val mockNPSService:                    NPSService                      = mock[NPSService]
  val mockSubmissionConnector:           SubmissionConnector             = mock[SubmissionConnector]
  val mockSubmissionService:             SubmissionService               = mock[SubmissionService]
  val mockUuidGenerator:                 UuidGenerator                   = mock[UuidGenerator]
  val mockNotificationContextRepository: NotificationContextRepository   = mock[NotificationContextRepository]
  val mockNotificationContextService:    NotificationContextService      = mock[NotificationContextService]
  val mockReportingWindowService:        ReportingWindowService          = mock[ReportingWindowService]

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .configure(
      "create-internal-auth-token-on-start" -> false,
      "http-verbs.retries.intervals"        -> List("1ms", "1ms", "1ms")
    )
    .overrides(
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[ETMPService].toInstance(mockETMPService),
      bind[PPNSService].toInstance(mockPPNSService),
      bind[StreamingParserService].toInstance(mockStreamingParserService),
      bind[AppConfig].toInstance(mockAppConfig),
      bind[ReturnsSummaryService].toInstance(mockReturnsSummaryService),
      bind[MonthlyReturnsSummaryRepository].toInstance(mockReturnsSummaryRepository),
      bind[NPSService].toInstance(mockNPSService),
      bind[SubmissionService].toInstance(mockSubmissionService),
      bind[NotificationContextService].toInstance(mockNotificationContextService)
    )
    .build()

  def app(overrides: GuiceableModule*): Application = GuiceApplicationBuilder().overrides(overrides: _*).build()
}
