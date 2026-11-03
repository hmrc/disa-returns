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

import uk.gov.hmrc.disareturns.models.common._
import uk.gov.hmrc.disareturns.models.submission.MonthlyReturnReportingStatus
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MonthlyReturnEligibilityService @Inject() (reportingStatusService: MonthlyReturnReportingStatusService) {

  def validateSubmissionEligibility(
    zReference:  String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[ErrorResponse, Unit]] =
    reportingStatusService
      .getReportingStatus(zReference)
      .value
      .map(_.flatMap(checkEligibility))

  private def checkEligibility(reportingStatus: MonthlyReturnReportingStatus): Either[ErrorResponse, Unit] = {
    val errors: Seq[ErrorResponse] = Seq(
      if (!reportingStatus.reportingWindowOpen) Some(ReportingWindowClosed) else None,
      if (reportingStatus.obligationClosed || reportingStatus.monthlyReturnDeclared) Some(ObligationClosed) else None
    ).flatten

    errors match {
      case Nil                => Right(())
      case singleError :: Nil => Left(singleError)
      case multipleErrors     => Left(MultipleErrorResponse(code = "FORBIDDEN", errors = multipleErrors))
    }
  }

}
