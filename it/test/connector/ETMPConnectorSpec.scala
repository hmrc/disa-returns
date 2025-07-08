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

package connector

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.disareturns.config.AppConfig
import uk.gov.hmrc.disareturns.connectors.ETMPConnector
import uk.gov.hmrc.disareturns.connectors.response.EtmpObligations
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ETMPConnectorSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar with GuiceOneServerPerSuite {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "EtmpConnector.checkReturnsObligationStatus" should {

    "return Right(EtmpObligations) when the call is successful" in {
      val mockHttpClient = mock[HttpClientV2]
      val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
      val mockRequestBuilder = mock[RequestBuilder]
      val expectedResponse   = EtmpObligations(true)

      val testUrl = "http://localhost:1204"
      when(mockHttpClient.get(url"$testUrl/disa-returns-stubs/etmp/check-obligation-status/123456"))
        .thenReturn(mockRequestBuilder)
      when(mockRequestBuilder.execute[EtmpObligations](any(),any()))
        .thenReturn(Future.successful(expectedResponse))

      val connector = new ETMPConnector(mockHttpClient, appConfig)

      val result = connector.checkReturnsObligationStatus("123456")

      whenReady(result) { res =>
        res shouldBe Right(expectedResponse)
      }
    }
  }
}
