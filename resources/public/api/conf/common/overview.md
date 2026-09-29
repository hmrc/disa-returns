The ISA Returns API is intended for ISA managers and authorised third-party software providers who need to submit
monthly ISA subscription returns and retrieve reconciliation results digitally. Use this API to submit monthly reports 
containing current-year ISA subscription data. It supports HMRC's digital ISA reporting service by enabling secure, 
standardised and frequent digital reporting. This helps HMRC detect errors quickly and improve oversight throughout the 
tax year.

You can use the API to:

- submit monthly reports containing cumulative current-year subscription data, including transfers and withdrawals
- submit a declaration that data for the current reporting window have been submitted
- retrieve reconciliation results in batches

Each monthly report must be generated to cover all ISA subscription activity from the 6th of one month through the 5th 
of the following month. The report must be submitted between the 6th and the 19th (by 23:59) of the month in which the 
reporting period ends.

This API does not currently support annual ISA end-of-year returns and does not replace the Lifetime ISA API, which 
remains active.

### Alpha and Beta access

The API is being released in phases.

### Alpha phase

- the API schema is only available for evaluation and planning
- the sandbox environment and executable endpoints are not yet available
- access is limited to organisations listed on the ISA manager register and third-party organisations with an existing 
relationship with a listed ISA manager

### Beta phase

The following are applicable during the beta phase:

- the sandbox environment and executable endpoints will be available
- ISA managers must also be enrolled for digital ISA reporting - information about how to enrol will be provided by 
early 2027
- access granted to organisations during the Alpha phase continues to remain valid during the Beta phase
- third-party organisations will continue to be eligible, subject to confirmation of their relationship with an 
organisation listed on the ISA manager register

## Integration stages

To use the ISA Returns API, you must register an application in the 
[HMRC Developer Hub](https://developer.service.hmrc.gov.uk/api-documentation), request access to the API, and configure 
authentication. You must also first integrate and test your software in the sandbox before requesting production access.

Follow the below steps to get started:

- [Create a Developer Hub account](#create-a-developer-hub-account).
- [Register your application for sandbox testing](#register-your-application-for-sandbox-testing).
- [Request access to API](#request-access-to-api).
- [Subscribe your application to the API](#subscribe-your-application-to-the-api).
- [Download required fields for submission](#download-required-fields-for-submission).

### Create a Developer Hub account

You need a Developer Hub account to register and manage your applications. If you do not already have one, register for 
an account before continuing.


### Register your application for sandbox testing

Register an application in the Developer Hub for sandbox testing. This generates a client ID and secret for use in the 
sandbox environment.

You will use this application to:

- subscribe to the sandbox version of the API
- test integration and validate your request and response handling
- simulate submission flows using test users

Use the sandbox base URL in your application: 
[https://test-api.service.hmrc.gov.uk](https://test-api.service.hmrc.gov.uk)

You must also create one or more test Government Gateway user IDs to authenticate with the user-restricted endpoints. 
These can be created in the Developer Hub.

For more information, see [Testing in the sandbox](https://developer.service.hmrc.gov.uk/api-documentation/docs/testing).

### Request access to API

The ISA Returns API is a controlled access API. Its endpoints are visible only to authorised and subscribed applications. 
Because it is a controlled access API, you must request access before you can subscribe your application.

To request access, you must have a Developer Hub account with a registered software application and satisfy either of 
the following:

- be an employee of an organisation listed on the 
[ISA manager register (GOV.UK)](https://www.gov.uk/government/publications/list-of-individual-savings-account-isa-managers-approved-by-hmrc/registered-individual-savings-account-isa-managers)
- be part of a third-party organisation with an existing relationship with a listed ISA manager

If you are an ISA manager and your organisation is not listed on the register, please check 
[how to apply for ISA manager status (GOV.UK)](https://www.gov.uk/guidance/apply-to-be-an-isa-manager).


If you are a third-party organisation, HMRC may ask you to provide evidence of your organisation’s relationship with the 
ISA manager to confirm eligibility and request authorisation through the Agent services. For more information, see 
[Authorise an agent for taxes that use the digital handshake](https://www.gov.uk/guidance/authorise-an-agent-to-deal-with-certain-tax-services-for-you).

If you do not have a Developer Hub account, you can 
[register for an account on GOV.UK](https://developer.service.hmrc.gov.uk/developer/registration). The account must use 
a work email address.

Once your Developer Hub account and software application are set up:

1. [Sign in to Developer Hub](https://developer.service.hmrc.gov.uk/developer/login).
2. Go to the ISA Returns API landing page.
3. Go to the Endpoints section and select ‘Request access’.
4. Fill in the request form with:
    - your organisation name
    - the API name: ISA Returns
    - the application ID linked to your Developer Hub software application

HMRC may contact you to discuss your request and confirm eligibility.

If you are not signed in, or access has not yet been granted, the Endpoints section will not display a link. You may 
see ‘Not applicable’ or ‘Request access’ instead.

### Subscribe your application to the API

If your access is approved, you will receive a confirmation email, and your software application will be subscribed to 
the API.

If you are not familiar with subscriptions or API visibility, see the 
[Reference guide](https://developer.service.hmrc.gov.uk/api-documentation/docs/reference-guide#api-access).

### Download required fields for submission

You can download a 
[list of the required fields for the POST submission endpoint](https://github.com/hmrc/disa-returns/raw/refs/heads/main/resources/public/api/conf/1.0/ISA_Returns_Monthly_Reporting_Required_Data_Items.ods). 
This lets you review the submission structure without needing access to the full API schema.

The file is in .ODS (OpenDocument Spreadsheet) format, which you can open using Google Sheets, LibreOffice, or Microsoft 
Excel.

These fields reflect the Alpha version of the API and may change before the Beta phase.
