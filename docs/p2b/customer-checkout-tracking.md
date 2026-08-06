# P2B Order 5 — Customer checkout and tracking

## Checkout boundary

- Checkout quotes and order totals remain server-authoritative.
- Customers may select COD, UPI, or card.
- Online payment starts from a persisted `PENDING` transaction owned by the authenticated customer.
- The payment service creates a Cashfree order server-side and receives a short-lived `payment_session_id`.
- The app opens a short-lived, HMAC-signed MyPet hosted checkout page that invokes Cashfree Checkout.
- Browser or device callbacks never mark a payment successful.
- Only a verified Cashfree order status or a correctly signed Cashfree webhook can move the transaction to `SUCCESS`.
- Webhook verification uses the exact raw request body, `x-webhook-timestamp`, and `x-webhook-signature` before JSON parsing.
- Webhook events are idempotent because Cashfree can retry delivery.
- The cart is cleared only after the app observes server-confirmed `SUCCESS` and confirms the paid order.
- Retrying payment reuses the existing MyPet order and pending Cashfree order when possible.

## Tracking boundary

- Active order details refresh every eight seconds.
- Polling stops for delivered, completed, cancelled, or rejected orders.
- Online payment state is shown separately from fulfilment state.
- A pending or failed online payment can be retried from the order detail screen.
- Status history is rendered from server data; the client does not invent fulfilment events.

## Security notes

- Hosted checkout URLs expire after ten minutes and are signed by a server-only secret distinct from Cashfree credentials.
- Checkout session creation verifies transaction ownership.
- Payment status reads verify transaction ownership.
- Cashfree client ID, client secret and webhook material stay server-side; the app receives only a payment session through the signed MyPet checkout page.
- Production webhooks must use HTTPS and the configured Cashfree webhook/API version.
