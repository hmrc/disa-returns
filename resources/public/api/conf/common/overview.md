Use this API to submit monthly reports of current-year ISA subscription data.

During the Alpha phase:

- you can access the API schema only for evaluation and planning purposes.
- the sandbox environment and executable endpoints are not available.
- your organisation must be approved by HMRC as an ISA manager to access the API.

From the Beta phase onwards:

- your organisation must also be enrolled for digital ISA reporting.

The API supports digital ISA reporting by enabling secure, standardised and frequent digital reporting. This helps HMRC detect errors quickly and improve oversight during the tax year.

You can use the API to:

- submit monthly reports with a cumulative total made up of current-year subscription data, including transfers and withdrawals.
- check the status of submitted reports to confirm processing.
- retrieve reconciliation results in batches using pagination.

Each report must cover ISA subscription activity from the 6th of one month to the 5th of the next, and be submitted between the 6th and 23:59 on the 19th.

The API is designed for integration into internal systems or third-party software. It enables organisations to reduce manual data handling and retrieve results programmatically.

Annual ISA end-of-year returns are not currently supported by the API.

The API does not replace or affect the Lifetime ISA API, which remains live and in use.

### Request access to the API ###
This is a restricted-access API. Its endpoints are visible only to authorised and subscribed software applications. Because it is a restricted-access API, you must request access before you can subscribe your software application to the API.

### Who can request access ###
To request access, you must:

- be an employee of an organisation listed on the [ISA manager register (GOV.UK)](https://www.gov.uk/government/publications/list-of-individual-savings-account-isa-managers-approved-by-hmrc/registered-individual-savings-account-isa-managers).
- have an HMRC Developer Hub account with a registered software application.

If your organisation is not listed, please check [how to apply for ISA manager status on GOV.UK](https://www.gov.uk/guidance/apply-to-be-an-isa-manager).

If you do not have a Developer Hub account, you can [register for an account on GOV.UK](https://developer.service.hmrc.gov.uk/developer/registration). The email address linked to your Developer Hub account must be a work email address.

### How to request access ###
Once your Developer Hub account and software application are set up:

1. [Sign in to Developer Hub](https://developer.service.hmrc.gov.uk/developer/login).

2. Return to this API landing page.

3. Go to the Endpoints section and select 'Request access'.

4. Fill in the request form with:
    - your organisation name (as listed on the ISA manager register)
    - the API name: ISA Returns
    - the ID linked to your Developer Hub software application

HMRC may contact you to discuss your request and confirm eligibility.

If your access is approved, you will get a confirmation email and your software application will be subscribed to the API.

If you are not signed in, or access has not yet been granted, the Endpoints section will not display a link. You may see 'Not applicable' or 'Request access' instead.

Access granted during Alpha will remain valid at Beta. You will not need to reapply.