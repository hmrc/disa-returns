# ISA Returns API Changelog

All notable changes to this API will be documented in this file. Learn about API features, fixes, deprecations and documentation changes.

## 19 November 2026

### Updates

- Added agent support. Agents can now submit and declare monthly reports and retrieve reconciliation results for an ISA manager they are authorised to act for.
- The `read:isa-returns` and `write:isa-returns` scopes can now be granted to agents.
- Added new error response `AGENT_CLIENT_RELATIONSHIP_NOT_FOUND` (403). It is returned when an agent is not authorised to act for the ISA manager with the requested Z-reference.

### What impact does this have?

- Agents must have an authorised relationship with the ISA manager for the requested Z-reference before calling the submit, declare or get reconciliation results endpoints.
- Consumers should handle the new `AGENT_CLIENT_RELATIONSHIP_NOT_FOUND` error code in their error handling logic.

## 3 September 2026

### Updates

- Removed the `GET /monthly/{zReference}/results/summary` endpoint.
- Removed `returnResultsSummaryLocation` from successful declaration responses.

### What impact does this have?

- Consumers may use the Push-Pull Notifications Service to receive notifications when new reconciliation results are available.

## 14 August 2026

### Updates

- Removed tax year and month from the paths for submitting and declaring monthly reports, retrieving report summaries and reconciliation results, and receiving report summary callbacks.
- The API now automatically applies the previous calendar month and its corresponding tax year to each monthly report in internal calls.

### What impact does this have?

- Consumers must stop including tax year and month in ISA Returns API request paths.

## 25 June 2026

### Updates

- The `nilReturn` field in the declaration endpoint request body is now mandatory. Previously it was optional, requests without a `nilReturn` field will now receive a `400 Bad Request` response.
- Added new error response `MISSING_NIL_RETURN` (400) returned when the `nilReturn` field is not present in the declaration request body.
- Added new error response `MONTHLY_RETURN_NOT_SUBMITTED` (422) returned when a declaration is made with `nilReturn` as `false` but no monthly return data has been submitted.

### What impact does this have?

- Consumers must now include the `nilReturn` boolean field in all declaration requests.
- Consumers should handle the new `MISSING_NIL_RETURN` and `MONTHLY_RETURN_NOT_SUBMITTED` error codes in their error handling logic.
