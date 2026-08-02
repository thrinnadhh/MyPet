# P2B Order 6 — Merchant appointments and finance

## Appointment operations

- The merchant workspace reads appointment providers owned by the authenticated merchant.
- Bookings are grouped into Today, Upcoming, Completed, No-show and Cancelled queues.
- Search is limited to data already authorized for the merchant: appointment ID, compact customer/pet labels and service name.
- Only confirmed appointments expose merchant lifecycle actions.
- Allowed merchant transitions are `CONFIRMED → COMPLETED`, `CONFIRMED → NO_SHOW`, and `CONFIRMED → CANCELLED`.
- A held appointment may be cancelled, but merchants cannot manufacture `SLOT_HELD` or `EXPIRED` states.
- Terminal appointments cannot be transitioned again.
- Appointment history and invoices require authenticated appointment access.
- Completing an appointment continues to generate the server invoice; the client only renders the returned invoice.
- Live mode never falls back to fabricated bookings. Demo records are available only when explicit demo mode is enabled.

## Finance operations

- Revenue summaries are scoped to a provider owned by the authenticated merchant.
- Delivered-order revenue uses the persisted commission ledger when present and the provider commission rate for delivered orders not yet included in a payout cycle.
- Completed appointment value is included as appointment revenue.
- Gross revenue, net revenue, order commission and activity counts are provider-specific.
- Payout totals and payout history are merchant-account scoped because a single payout can combine earnings from multiple providers.
- The API reports this distinction explicitly through `payoutScope=MERCHANT_ACCOUNT`; the UI does not imply that account payouts belong only to the selected provider.
- Finance data is read-only for merchants. Payout calculation remains an administrator-only operation.

## Authorization

- Appointment lifecycle, history and invoice access is enforced by the backend.
- Merchant finance requires the authenticated merchant to own the requested provider; administrators may read it for support.
- Client route guards improve navigation but are not treated as an authorization boundary.
