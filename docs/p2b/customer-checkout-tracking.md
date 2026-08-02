# P2B Order 5 — Customer checkout and tracking

## Checkout boundary

- Checkout quotes and order totals remain server-authoritative.
- Customers may select COD, UPI, or card.
- Online payment starts from a persisted `PENDING` transaction owned by the authenticated customer.
- The app opens a short-lived, HMAC-signed hosted checkout session served by the payment service.
- Browser or device callbacks never mark a payment successful.
- Only the signed Razorpay webhook can move the transaction to `SUCCESS`.
- The cart is cleared only after the app observes server-confirmed `SUCCESS` and confirms the paid order.
- Retrying payment reuses the existing order and pending Razorpay order when possible.

## Tracking boundary

- Active order details refresh every eight seconds.
- Polling stops for delivered, completed, cancelled, or rejected orders.
- Online payment state is shown separately from fulfilment state.
- A pending or failed online payment can be retried from the order detail screen.
- Status history is rendered from server data; the client does not invent fulfilment events.

## Security notes

- Hosted checkout URLs expire after ten minutes and are signed by a server-only secret.
- Checkout session creation verifies transaction ownership.
- Payment status reads verify transaction ownership.
- Razorpay keys exposed to checkout are public key IDs only; secrets remain server-side.
