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

package services

import uk.gov.hmrc.disareturns.models.common.InvalidCursorErr
import uk.gov.hmrc.disareturns.services.CursorCrypto
import utils.BaseUnitSpec

class CursorCryptoSpec extends BaseUnitSpec {
  private val cursorCrypto = new CursorCrypto(app.configuration)

  "CursorCrypto" should {
    "round-trip a cursor using a URL-safe token" in {
      val rawCursor = "nps+cursor/with=unsafe?characters&values"

      val encrypted = cursorCrypto.encrypt(rawCursor, validZReference, validTaxYear, validMonth.toString, 200)

      encrypted.matches("[A-Za-z0-9_-]+")                                                      shouldBe true
      encrypted                                                                                  should not include "+"
      encrypted                                                                                  should not include "/"
      encrypted                                                                                  should not include "="
      cursorCrypto.decrypt(encrypted, validZReference, validTaxYear, validMonth.toString, 200) shouldBe Right(rawCursor)
    }

    "reject a tampered token" in {
      val encrypted   = cursorCrypto.encrypt("raw-cursor", validZReference, validTaxYear, validMonth.toString, 200)
      val replacement = if (encrypted.head == 'A') 'B' else 'A'
      val tampered    = s"$replacement${encrypted.tail}"

      cursorCrypto.decrypt(tampered, validZReference, validTaxYear, validMonth.toString, 200) shouldBe Left(InvalidCursorErr)
    }

    "reject a token that is not URL-safe Base64" in {
      cursorCrypto.decrypt("not+a/cursor=", validZReference, validTaxYear, validMonth.toString, 200) shouldBe Left(InvalidCursorErr)
    }

    "reject a cursor used with different request context" in {
      val encrypted = cursorCrypto.encrypt("raw-cursor", validZReference, validTaxYear, validMonth.toString, 200)

      cursorCrypto.decrypt(encrypted, validZReference, validTaxYear, validMonth.toString, 100) shouldBe Left(InvalidCursorErr)
    }
  }
}
