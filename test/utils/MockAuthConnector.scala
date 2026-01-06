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

package utils

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrieval

import scala.concurrent.Future

trait MockAuthConnector { self: TestData =>

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  def stubEnrolments(
    enrolmentKey:  String = "HMRC-DISA-ORG",
    identifierKey: Option[String] = Some("ZREF"),
    zRef:          String = validZReference,
    state:         String = "Activated"
  ): Unit = {

    val enrolment = Enrolments(
      Set(
        Enrolment(
          key = enrolmentKey,
          identifiers = identifierKey.fold(Seq.empty[EnrolmentIdentifier])(key => Seq(EnrolmentIdentifier(key, zRef))),
          state = state
        )
      )
    )

    when(mockAuthConnector.authorise(any(), any[Retrieval[Enrolments]])(any(), any()))
      .thenReturn(Future.successful(enrolment))
  }

  def authorizationForZRef(zRef: String = validZReference): Unit = stubEnrolments(zRef = zRef)

  def unauthorized(authException: Exception = InsufficientEnrolments("")): Unit =
    when(mockAuthConnector.authorise[Unit](any(), any())(any(), any()))
      .thenReturn(Future.failed(authException))
}
