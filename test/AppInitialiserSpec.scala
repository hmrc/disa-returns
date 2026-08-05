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

package uk.gov.hmrc.disareturns

import org.apache.pekko.Done
import uk.gov.hmrc.disareturns.config.InternalAuthTokenInitialiser
import _root_.utils.BaseUnitSpec

import scala.concurrent.Future

class AppInitialiserSpec extends BaseUnitSpec {

  "AppInitialiser" should {
    "complete construction when internal-auth initialisation succeeds" in {
      val initialiser = internalAuthTokenInitialiser(Future.successful(Done))

      val appInitialiser = new AppInitialiser(initialiser)

      appInitialiser.initialised.futureValue shouldBe Done
    }

    "fail construction when internal-auth initialisation fails" in {
      val exception   = new RuntimeException("Internal-auth initialisation failed")
      val initialiser = internalAuthTokenInitialiser(Future.failed(exception))

      val thrown = intercept[RuntimeException] {
        new AppInitialiser(initialiser)
      }

      thrown shouldBe exception
    }
  }

  private def internalAuthTokenInitialiser(result: Future[Done]): InternalAuthTokenInitialiser =
    new InternalAuthTokenInitialiser {
      override protected def initialise(): Future[Done] = result
    }
}
