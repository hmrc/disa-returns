package uk.gov.hmrc.disareturns.controllers

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlEqualTo}
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND, OK, UNAUTHORIZED}
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.await
import uk.gov.hmrc.disareturns.models.common.Month
import uk.gov.hmrc.disareturns.models.returnResults.{IssueOverSubscribed, IssueWithMessage, ReconciliationReportPage, ReturnResults}
import uk.gov.hmrc.disareturns.utils.BaseIntegrationSpec

class ReconciliationResultControllerISpec extends BaseIntegrationSpec {

    private val isaManagerRef = "Z1234"
    private val taxYear = "2025-26"
    private val monthEnum = Month.SEP
    private val monthToken = monthEnum.toString
    private val page = 0

    private val npsReportJson = """
                              |{
                              | "totalRecords": 3,
                              | "returnResults": [
                              |   {
                              |     "accountNumber": "123",
                              |     "nino": "ABC123",
                              |     "issueIdentified": {
                              |       "code": "OVER_SUBSCRIBED",
                              |       "overSubscribedAmount": 1823.76
                              |     }
                              |   },
                              |   {
                              |     "accountNumber": "123",
                              |     "nino": "ABC123",
                              |     "issueIdentified": {
                              |       "code": "FAILED_ELIGIBILITY",
                              |       "message": "Failed Eligibility"
                              |     }
                              |   },
                              |   {
                              |     "accountNumber": "123",
                              |     "nino": "ABC123",
                              |     "issueIdentified": {
                              |       "code": "UNABLE_TO_IDENTIFY_INVESTOR",
                              |       "message": "Unable to Identify Investor"
                              |     }
                              |   }
                              | ]
                              |}
        """.stripMargin

    "GET /monthly/:zRef/:year/:month/results/summary" should {

      "return 200 and the first page of the reconciliation report" in {
        stubAuth()
        stubNPSReportRetrieval(200, npsReportJson)

        val res: WSResponse = retrieveReconciliationReportPageRequest(isaManagerRef, taxYear, monthToken, page)

        res.status mustBe OK
        res.json.as[ReconciliationReportPage] mustBe ReconciliationReportPage(
          page,
          2,
          3,
          2,
          Seq(
            ReturnResults(
              "123",
              "ABC123",
              IssueOverSubscribed(
                "OVER_SUBSCRIBED",
                1823.76
              )
            ),
            ReturnResults(
              "123",
              "ABC123",
              IssueWithMessage(
                "FAILED_ELIGIBILITY",
                "Failed Eligibility"
              )
            )
          )
        )
      }

      "return 200 and the final page of the reconciliation report" in {
        stubAuth()
        stubNPSReportRetrieval(200, npsReportJson)

        val res: WSResponse = retrieveReconciliationReportPageRequest(isaManagerRef, taxYear, monthToken, page + 1)

        res.status mustBe OK
        res.json.as[ReconciliationReportPage] mustBe ReconciliationReportPage(
          page + 1,
          1,
          3,
          2,
          Seq(
            ReturnResults(
              "123",
              "ABC123",
              IssueWithMessage(
                "UNABLE_TO_IDENTIFY_INVESTOR",
                "Unable to Identify Investor"
              )
            )
          )
        )
      }

      "return 400 and an error message" in {
        stubAuth()
        stubNPSReportRetrieval(200, npsReportJson)

        val res: WSResponse = retrieveReconciliationReportPageRequest(isaManagerRef, taxYear, monthToken, -1)

        res.status mustBe BAD_REQUEST
        (res.json \ "message" ).as[String] mustBe "Issue(s) with your request"
      }

      "return 401 and an error message" in {
        stubAuthFail()

        val res: WSResponse = retrieveReconciliationReportPageRequest(isaManagerRef, taxYear, monthToken, page)

        res.status mustBe UNAUTHORIZED
        (res.json \ "message" ).as[String] mustBe "Unauthorised"
      }

      "return 404 when a page is not found" in {
        stubAuth()
        stubNPSReportRetrieval(200, npsReportJson)

        val res: WSResponse = retrieveReconciliationReportPageRequest(isaManagerRef, taxYear, monthToken, page + 2)

        res.status mustBe NOT_FOUND
        (res.json \ "message" ).as[String] mustBe s"No page ${page + 2} found"
      }

      "return 404 when a report is not found" in {
        stubAuth()
        stubNPSReportRetrieval(404, "")

        val res: WSResponse = retrieveReconciliationReportPageRequest(isaManagerRef, taxYear, monthToken, page + 2)

        res.status mustBe NOT_FOUND
        (res.json \ "message" ).as[String] mustBe s"Return not found"
      }

      "return 500 when NPS sends invalid JSON" in {
        stubAuth()
        stubNPSReportRetrieval(200, "not good json")

        val res: WSResponse = retrieveReconciliationReportPageRequest(isaManagerRef, taxYear, monthToken, page + 2)

        res.status mustBe INTERNAL_SERVER_ERROR
      }

      "return 500 when NPS sends an unexpected status" in {
        stubAuth()
        stubNPSReportRetrieval(204, npsReportJson)

        val res: WSResponse = retrieveReconciliationReportPageRequest(isaManagerRef, taxYear, monthToken, page + 2)

        res.status mustBe INTERNAL_SERVER_ERROR
      }
    }

    def retrieveReconciliationReportPageRequest(
                                       isaManagerReference: String,
                                       taxYear: String,
                                       month: String,
                                       pageIndex: Int,
                                       headers: Seq[(String, String)] = Seq("Authorization" -> "mock-bearer-token")
                                     ): WSResponse = {
      await(
        ws.url(s"http://localhost:$port/monthly/$isaManagerReference/$taxYear/$month/results?page=$pageIndex")
          .withFollowRedirects(follow = false)
          .withHttpHeaders(headers: _*)
          .get()
      )
    }

    def stubNPSReportRetrieval(status: Int, body: String): Unit =
      stubFor(
        get(urlEqualTo(s"/monthly/$isaManagerRef/$taxYear/$monthToken/results"))
          .willReturn(aResponse().withStatus(status).withBody(body))
      )
}
