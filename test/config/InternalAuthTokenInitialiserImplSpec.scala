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

import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{verify, when}
import play.api.http.Status.{CREATED, INTERNAL_SERVER_ERROR}
import play.api.libs.concurrent.Futures
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.disareturns.config.{AppConfig, InternalAuthTokenInitialiserImpl}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import utils.BaseUnitSpec

import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class InternalAuthTokenInitialiserImplSpec extends BaseUnitSpec {

  private val internalAuthToken = "valid-internal-auth-token-disa-returns"
  private val internalAuthUrl   = "http://localhost:8470"
  private val fullTokenUrl      = url"$internalAuthUrl/test-only/token"
  private val timeoutDuration   = 30.seconds

  class TestFutures extends Futures {
    var timeoutDuration: Option[FiniteDuration] = None

    override def timeout[A](duration: FiniteDuration)(future: => Future[A]): Future[A] = {
      timeoutDuration = Some(duration)
      future
    }

    override def delayed[A](duration: FiniteDuration)(future: => Future[A]): Future[A] =
      future

    override def delay(duration: FiniteDuration): Future[Done] =
      Future.successful(Done)
  }

  trait TestSetup {
    val mockInitialiserAppConfig:  AppConfig      = mock[AppConfig]
    val mockInitialiserHttpClient: HttpClientV2   = mock[HttpClientV2]
    val mockPostRequestBuilder:    RequestBuilder = mock[RequestBuilder]
    val futures:                   TestFutures    = new TestFutures

    lazy val initialiser =
      new InternalAuthTokenInitialiserImpl(
        mockInitialiserAppConfig,
        mockInitialiserHttpClient,
        futures
      )

    when(mockInitialiserAppConfig.internalAuthUrl).thenReturn(internalAuthUrl)
    when(mockInitialiserAppConfig.internalAuthToken).thenReturn(internalAuthToken)

    def createAuthTokenResponse(status: Int): Unit = {
      when(mockInitialiserHttpClient.post(eqTo(fullTokenUrl))(any[HeaderCarrier]))
        .thenReturn(mockPostRequestBuilder)
      when(mockPostRequestBuilder.withBody(any[JsObject]())(any(), any(), any()))
        .thenReturn(mockPostRequestBuilder)
      when(mockPostRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(HttpResponse(status)))
    }
  }

  "InternalAuthTokenInitialiserImpl.initialised" should {

    val expectedCreateTokenRequestBody: JsObject =
      Json.obj(
        "token"     -> internalAuthToken,
        "principal" -> "disa-returns",
        "permissions" -> Seq(
          Json.obj(
            "resourceType"     -> "disa-returns-submission",
            "resourceLocation" -> "*",
            "actions"          -> List("READ", "WRITE")
          )
        )
      )

    "create or update the auth token with submission READ and WRITE permissions" in new TestSetup {
      createAuthTokenResponse(CREATED)

      initialiser.initialised.futureValue shouldBe Done
      initialiser.initialised.futureValue shouldBe Done

      futures.timeoutDuration shouldBe Some(timeoutDuration)
      verify(mockPostRequestBuilder).withBody(eqTo(expectedCreateTokenRequestBody))(any(), any(), any())
      verify(mockPostRequestBuilder).execute[HttpResponse](any(), any())
    }

    "fail when the auth token cannot be created" in new TestSetup {
      createAuthTokenResponse(INTERNAL_SERVER_ERROR)

      val thrown = initialiser.initialised.failed.futureValue

      futures.timeoutDuration shouldBe Some(timeoutDuration)
      thrown                  shouldBe a[RuntimeException]
      thrown.getMessage       shouldBe "Unable to initialise internal-auth token"
    }
  }
}
