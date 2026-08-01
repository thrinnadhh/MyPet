# Client API Error and Formatting Contract

## Error envelope

Backend services should return the following shape through the API gateway:

```json
{
  "code": "ORDER_NOT_CANCELLABLE",
  "message": "This order can no longer be cancelled.",
  "traceId": "request-123",
  "fieldErrors": {
    "reason": ["A reason is required."]
  },
  "timestamp": "2026-08-02T00:00:00Z",
  "path": "/api/v1/orders/123/cancel",
  "error": "This order can no longer be cancelled."
}
```

`error` is a temporary compatibility alias. New clients use `message` and `code`.

## Client behavior

- `401`: authentication flow
- `403`: unauthorized state
- `404`: unavailable/removed state
- `409`: refresh stale server state
- `400` or `422`: render field validation where available
- `429`: show retry guidance using `Retry-After`
- `5xx`: recoverable server state with trace ID available to support
- network `TypeError`: offline/network state

Clients must not derive business state from the message string. Decisions use HTTP status and stable `code` values.

## Formatting

Both mobile applications use identical formatter source for:

- INR currency with `en-IN` grouping;
- date, time and date-time presentation;
- metres/kilometres;
- percentages;
- generic status labels;
- order, appointment and delivery terminology.

A regression test compares the customer and operational contract files byte-for-byte to prevent drift.

## Compatibility

The shared backend handler emits the typed envelope for common bad request, validation, not-found, conflict and internal-error cases. Existing service-specific handlers using `ErrorResponse(error)` remain source-compatible and continue exposing the `error` alias while they are migrated to stable domain codes.
