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
import uk.gov.hmrc.disareturns.models.isaAccounts.{IsaAccount, LifetimeIsaClosure, LifetimeIsaNewSubscription, LifetimeIsaTransfer, LifetimeIsaTransferAndClosure, StandardIsaNewSubscription, StandardIsaTransfer}
import utils.BaseUnitSpec

class IsaAccountSpec extends BaseUnitSpec {

  "IsaAccount Reads" should {

    "deserialize LifetimeIsaClosure when reportingATransfer=false and reasonForClosure is present" in {
      val result = lifetimeIsaClosureJson.validate[IsaAccount]
      result.isSuccess shouldBe true
      result.get       shouldBe a[LifetimeIsaClosure]
    }

    "deserialize LifetimeIsaNewSubscription when reportingATransfer=false and lisaBonusClaim is present" in {
      val result = lifetimeIsaNewSubscriptionJson.validate[IsaAccount]
      result.isSuccess shouldBe true
      result.get       shouldBe a[LifetimeIsaNewSubscription]
    }

    "deserialize LifetimeIsaTransfer when reportingATransfer=true and lisaBonusClaim is present and reasonForClosure is not present" in {
      val result = lifetimeIsaTransferJson.validate[IsaAccount]
      result.isSuccess shouldBe true
      result.get       shouldBe a[LifetimeIsaTransfer]
    }

    "deserialize LifetimeIsaTransferAndClosure when reportingATransfer=true and reasonForClosure is present" in {
      val result = lifetimeIsaTransferAndClosureJson.validate[IsaAccount]
      result.isSuccess shouldBe true
      result.get       shouldBe a[LifetimeIsaTransferAndClosure]
    }

    "deserialize StandardIsaNewSubscription when reportingATransfer=false and flexibleIsa is present" in {
      val result = standardIsaNewSubscriptionJson.validate[IsaAccount]
      result.isSuccess shouldBe true
      result.get       shouldBe a[StandardIsaNewSubscription]
    }

    "deserialize StandardIsaTransfer when reportingATransfer=true and flexibleIsa is present and reasonForClosure and lisaBonusClaim are absent" in {
      val result = standardIsaTransferJson.validate[IsaAccount]
      result.isSuccess shouldBe true
      result.get       shouldBe a[StandardIsaTransfer]
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

    "serialize LifetimeIsaNewSubscription correctly" in {
      val account = lifetimeIsaNewSubscriptionJson.as[IsaAccount]
      val json    = Json.toJson(account)
      json shouldBe lifetimeIsaNewSubscriptionJson
    }

    "serialize StandardIsaTransfer correctly" in {
      val account = standardIsaTransferJson.as[IsaAccount]
      val json    = Json.toJson(account)
      json shouldBe standardIsaTransferJson
    }

    "serialize LifetimeIsaTransfer correctly" in {
      val account = lifetimeIsaTransferJson.as[IsaAccount]
      val json    = Json.toJson(account)
      json shouldBe lifetimeIsaTransferJson
    }

    "serialize LifetimeIsaTransferAndClosure correctly" in {
      val account = lifetimeIsaTransferAndClosureJson.as[IsaAccount]
      val json    = Json.toJson(account)
      json shouldBe lifetimeIsaTransferAndClosureJson
    }

    "serialize StandardIsaNewSubscription correctly" in {
      val account = standardIsaNewSubscriptionJson.as[IsaAccount]
      val json    = Json.toJson(account)
      json shouldBe standardIsaNewSubscriptionJson
    }
  }
}
