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

package uk.gov.hmrc.disareturns.services

import play.api.Configuration
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.crypto.{Crypted, PlainText, SymmetricCryptoFactory}
import uk.gov.hmrc.disareturns.models.common.{ErrorResponse, InvalidCursorErr}

import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.util.Try

@Singleton
class CursorCrypto @Inject() (configuration: Configuration) {
  private val crypto = SymmetricCryptoFactory.aesGcmCryptoFromConfig("cursor.encryption", configuration.underlying)

  def encrypt(cursor: String, zReference: String, taxYear: String, month: String, limit: Int): String = {
    val payload        = Json.stringify(Json.toJson(CursorCrypto.CursorPayload(cursor, zReference, taxYear, month, limit)))
    val encryptedBytes = Base64.getDecoder.decode(crypto.encrypt(PlainText(payload)).value)
    Base64.getUrlEncoder.withoutPadding().encodeToString(encryptedBytes)
  }

  def decrypt(cursor: String, zReference: String, taxYear: String, month: String, limit: Int): Either[ErrorResponse, String] =
    Try {
      val encrypted = Base64.getEncoder.encodeToString(Base64.getUrlDecoder.decode(cursor))
      Json.parse(crypto.decrypt(Crypted(encrypted)).value).as[CursorCrypto.CursorPayload]
    }.toEither
      .filterOrElse(
        payload => payload.zReference == zReference && payload.taxYear == taxYear && payload.month == month && payload.limit == limit,
        new SecurityException("Cursor context does not match request")
      )
      .map(_.cursor)
      .left
      .map(_ => InvalidCursorErr)
}

object CursorCrypto {
  private case class CursorPayload(cursor: String, zReference: String, taxYear: String, month: String, limit: Int)
  private implicit val format: OFormat[CursorPayload] = Json.format[CursorPayload]
}
