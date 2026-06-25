# DISA Returns API Changelog

All notable changes to this API will be documented in this file. Learn about API features, fixes, deprecations and documentation changes.

## 25 June 2026

### Updates

- The `nilReturn` field in the declaration endpoint request body is now mandatory. Previously it was optional, requests without a `nilReturn` field will now receive a `400 Bad Request` response.
- Added new error response `MISSING_NIL_RETURN` (400) returned when the `nilReturn` field is not present in the declaration request body.
- Added new error response `MONTHLY_RETURN_NOT_SUBMITTED` (422) returned when a declaration is made with `nilReturn` as `false` but no monthly return data has been submitted.

### What impact does this have?

- Consumers must now include the `nilReturn` boolean field in all declaration requests.
- Consumers should handle the new `MISSING_NIL_RETURN` and `MONTHLY_RETURN_NOT_SUBMITTED` error codes in their error handling logic.
