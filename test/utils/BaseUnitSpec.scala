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

import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.DefaultAwaitTimeout
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.connectors.{BaseConnector, ETMPConnector, PPNSConnector}
import uk.gov.hmrc.disareturns.repositories.{MonthlyReportDocumentRepository, ReturnMetadataRepository}
import uk.gov.hmrc.disareturns.services.{CompleteReturnService, ETMPService, PPNSService, ReturnMetadataService, StreamingParserService}
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
    with TestMocks {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val hc: HeaderCarrier    = HeaderCarrier()

  //MOCKS
  val mockHttpClient:                      HttpClientV2                    = mock[HttpClientV2]
  val mockAppConfig:                       AppConfig                       = mock[AppConfig]
  val mockRequestBuilder:                  RequestBuilder                  = mock[RequestBuilder]
  val mockPPNSService:                     PPNSService                     = mock[PPNSService]
  val mockPPNSConnector:                   PPNSConnector                   = mock[PPNSConnector]
  val mockETMPConnector:                   ETMPConnector                   = mock[ETMPConnector]
  val mockETMPService:                     ETMPService                     = mock[ETMPService]
  val mockBaseConnector:                   BaseConnector                   = mock[BaseConnector]
  val mockMonthlyReportDocumentService:    ReturnMetadataService           = mock[ReturnMetadataService]
  val mockMonthlyReportDocumentRepository: MonthlyReportDocumentRepository = mock[MonthlyReportDocumentRepository]
  val mockAuthConnector:                   AuthConnector                   = mock[AuthConnector]
  val mockStreamingParserService:          StreamingParserService          = mock[StreamingParserService]
  val mockCompleteReturnService:           CompleteReturnService           = mock[CompleteReturnService]
  val mockReturnMetadataRepository:        ReturnMetadataRepository        = mock[ReturnMetadataRepository]

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .overrides(
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[ETMPService].toInstance(mockETMPService),
      bind[PPNSService].toInstance(mockPPNSService),
      bind[ReturnMetadataService].toInstance(mockMonthlyReportDocumentService),
      bind[StreamingParserService].toInstance(mockStreamingParserService),
      bind[CompleteReturnService].toInstance(mockCompleteReturnService)
    )
    .build()
}
