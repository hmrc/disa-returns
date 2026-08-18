# DISA Returns API Changelog

All notable changes to this API will be documented in this file. Learn about API features, fixes, deprecations and documentation changes.

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
