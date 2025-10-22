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

package models

import play.api.libs.json._
import uk.gov.hmrc.disareturns.models.isaAccounts._
import utils.BaseUnitSpec

class IsaAccountSpec extends BaseUnitSpec {

  "IsaAccount Reads" should {

    "deserialize LifetimeIsaClosure when reportingATransfer=false and reasonForClosure is present" in {
      val result = lifetimeIsaClosureJson.validate[IsaAccount]
      result.isSuccess shouldBe true
      result.get       shouldBe a[LifetimeIsaClosure]
    }

    "deserialize LifetimeIsaSubscription when reportingATransfer=false and lisaBonusClaim is present" in {
      val result = LifetimeIsaSubscriptionJson.validate[IsaAccount]
      result.isSuccess shouldBe true
      result.get       shouldBe a[LifetimeIsaSubscription]
    }

    "deserialize LifetimeIsaSubscription when reportingATransfer=true and lisaBonusClaim is present and reasonForClosure is not present" in {
      val result = LifetimeIsaSubscriptionJson.validate[IsaAccount]
      result.isSuccess shouldBe true
      result.get       shouldBe a[LifetimeIsaSubscription]
    }

    "deserialize LifetimeIsaSubscription when reportingATransfer=true and reasonForClosure is present" in {
      val result = LifetimeIsaSubscriptionJson.validate[IsaAccount]
      result.isSuccess shouldBe true
      result.get       shouldBe a[LifetimeIsaSubscription]
    }

    "deserialize StandardIsaSubscription when reportingATransfer=false and flexibleIsa is present" in {
      val result = StandardIsaSubscriptionJson.validate[IsaAccount]
      result.isSuccess shouldBe true
      result.get       shouldBe a[StandardIsaSubscription]
    }

    "deserialize StandardIsaTransfer when reportingATransfer=true and flexibleIsa is present and reasonForClosure and lisaBonusClaim are absent" in {
      val result = standardIsaClosureJson.validate[IsaAccount]
      result.isSuccess shouldBe true
      result.get       shouldBe a[StandardIsaClosure]
    }

    "fail to deserialize when reportingATransfer=true and no subtype discriminator fields present" in {
      val invalidJson = Json.obj(
        "reportingATransfer" -> true,
        "accountNumber"      -> "ACC999"
      )
      val result = invalidJson.validate[IsaAccount]
      result.isError shouldBe true
    }

    "fail to deserialize when reportingATransfer=false and no subtype discriminator fields present" in {
      val invalidJson = Json.obj(
        "reportingATransfer" -> false,
        "accountNumber"      -> "ACC999"
      )
      val result = invalidJson.validate[IsaAccount]
      result.isError shouldBe true
    }
  }

  "IsaAccount Writes" should {

    "serialize LifetimeIsaClosure correctly" in {
      val account = lifetimeIsaClosureJson.as[IsaAccount]
      val json    = Json.toJson(account)
      json shouldBe lifetimeIsaClosureJson
    }

    "serialize LifetimeIsaSubscription correctly" in {
      val account = LifetimeIsaSubscriptionJson.as[IsaAccount]
      val json    = Json.toJson(account)
      json shouldBe LifetimeIsaSubscriptionJson
    }

    "serialize StandardIsaTransfer correctly" in {
      val account = standardIsaClosureJson.as[IsaAccount]
      val json    = Json.toJson(account)
      json shouldBe standardIsaClosureJson
    }

    "serialize LifetimeIsaSubscription correctly" in {
      val account = LifetimeIsaSubscriptionJson.as[IsaAccount]
      val json    = Json.toJson(account)
      json shouldBe LifetimeIsaSubscriptionJson
    }

    "serialize LifetimeIsaSubscription correctly" in {
      val account = LifetimeIsaSubscriptionJson.as[IsaAccount]
      val json    = Json.toJson(account)
      json shouldBe LifetimeIsaSubscriptionJson
    }

    "serialize StandardIsaSubscription correctly" in {
      val account = StandardIsaSubscriptionJson.as[IsaAccount]
      val json    = Json.toJson(account)
      json shouldBe StandardIsaSubscriptionJson
    }
  }
}
