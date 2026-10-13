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
import uk.gov.hmrc.auth.core.retrieve.{AgentInformation, Credentials, ~}

import scala.concurrent.Future

trait MockAuthConnector { self: TestData =>

  type UserDetailsRetrievals = Option[Credentials] ~ Option[CredentialRole] ~ Option[AffinityGroup] ~ AgentInformation ~ Option[String]

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  def isaManagerRetrievals(
    credentials:    Option[Credentials] = Some(Credentials(testCredId, "GovernmentGateway")),
    credentialRole: Option[CredentialRole] = Some(User),
    groupId:        Option[String] = Some(testGroupId)
  ): UserDetailsRetrievals =
    new ~(new ~(new ~(new ~(credentials, credentialRole), Some(AffinityGroup.Organisation)), AgentInformation(None, None, None)), groupId)

  def agentRetrievals(groupId: Option[String] = Some(testGroupId)): UserDetailsRetrievals =
    new ~(
      new ~(new ~(new ~(None, None), Some(AffinityGroup.Agent)), AgentInformation(Some(testAgentId), None, Some(testAgentName))),
      groupId
    )

  def stubEnrolments(
    enrolmentKey:  String = "HMRC-DISA-ORG",
    identifierKey: Option[String] = Some("ZREF"),
    zRef:          String = validZReference,
    state:         String = "Activated",
    userDetails:   UserDetailsRetrievals = isaManagerRetrievals()
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

    when(mockAuthConnector.authorise[Enrolments ~ UserDetailsRetrievals](any(), any())(any(), any()))
      .thenReturn(Future.successful(new ~(enrolment, userDetails)))
  }

  def authorizationForZRef(zRef: String = validZReference): Unit =
    stubEnrolments(zRef = zRef)

  def unauthorized(authException: Exception = InsufficientEnrolments("")): Unit =
    when(mockAuthConnector.authorise[Unit](any(), any())(any(), any()))
      .thenReturn(Future.failed(authException))
}
