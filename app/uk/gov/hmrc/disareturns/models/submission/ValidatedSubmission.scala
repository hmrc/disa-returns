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

package uk.gov.hmrc.disareturns.models.submission

import play.api.libs.Files.TemporaryFile
import uk.gov.hmrc.disareturns.models.common.ValidationError

import java.time.Duration

final case class ValidationMetrics(numberOfEntries: Long, requestSize: Long, requestValidationTime: Duration)

final case class ErrorVolume(errorType: String, volume: Int)

final case class ValidatedSubmission(file: TemporaryFile, metrics: ValidationMetrics)

final case class InvalidSubmission(error: ValidationError, errorVolumes: Seq[ErrorVolume], metrics: ValidationMetrics)
