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

package uk.gov.hmrc.disareturns.models.common

sealed trait UserDetails {
  def userType: String
  def groupId:  String
}

object UserDetails {

  val unknown = "unknown"

  case class IsaManager(
    groupId:        String,
    credId:         String,
    credentialRole: String
  ) extends UserDetails {
    override val userType: String = "IM"
  }

  case class Agent(
    groupId:   String,
    agentId:   String,
    agentName: String
  ) extends UserDetails {
    override val userType: String = "Agent"
  }
}
