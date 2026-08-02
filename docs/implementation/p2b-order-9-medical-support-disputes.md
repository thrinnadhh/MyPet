# P2B Order 9 — Secure medical documents, support and disputes

## Medical-document boundary

Medical files are appointment-scoped private objects. The database stores metadata and an opaque storage key, never a permanent public URL.

Upload lifecycle:

1. An authenticated customer, provider owner or administrator requests a ten-minute one-time upload reservation for an appointment.
2. The client uploads the file with the reservation token.
3. The server checks appointment authorization, declared MIME type, file signature and the 10 MB size limit.
4. The server writes the object under a random private key and stores metadata.
5. Every upload, signed-link issue, view and download is append-only audited with actor, trace ID and server time.

Access lifecycle:

- An authorized actor requests an inline or attachment link.
- The server issues a five-minute HMAC token bound to document, actor, expiry and disposition.
- The content route validates the signature and expiry before reading private storage.
- Responses use `Cache-Control: private, no-store` and `X-Content-Type-Options: nosniff`.

Supported content types are PDF, JPEG, PNG and WebP. The customer beta UI currently selects scanned images; the API supports PDF for provider and future document-picker clients.

## Customer support and dispute lifecycle

Customers may create order-owned cases for:

- missing item
- damaged item
- wrong item
- late delivery
- payment issue
- other

Cases expose `OPEN`, `UNDER_REVIEW`, `RESOLVED` and `REJECTED` states. Refund status is independently tracked as `NOT_APPLICABLE`, `PENDING`, `PROCESSING`, `COMPLETED` or `FAILED`.

Evidence uses the same one-time reservation and short-lived HMAC pattern as medical documents. Customer identity must own both the order and case. Administrators can review the queue, record mandatory resolution notes, resolve, reject or resolve while initiating a PaymentModule refund.

## Event interlinks

The order service publishes durable events for:

- `CustomerCaseCreated`
- `CustomerCaseEvidenceAdded`
- `CustomerCaseUpdated`

Notifications and administrative projections consume those events without inventing case or refund state.

## Production safeguards

- no permanent public medical/evidence links
- no trust in a client-supplied owner
- no file extension-only validation for medical documents
- one-time upload tokens
- bounded file size and content types
- private no-store signed reads
- administrator-only resolution and refund initiation
- payment service remains authoritative for final refund completion

## Release boundary

Physical-device accessibility/location validation, the exact ten connected E2E journeys and internal beta distribution remain Orders 10–12.
