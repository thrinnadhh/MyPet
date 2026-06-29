# Sprint 3: Orders And Payment Capture

## Goal

A customer can place a paid delivery order for a pet store, and the merchant can manage it.

## Acceptance Checklist

- [ ] Customer cart, address selection, checkout, and payment flow are wired to backend.
- [ ] Order service revalidates live catalog price and stock.
- [ ] Payment service captures Razorpay sandbox payment or records documented sandbox equivalent.
- [ ] Stock decrement is atomic and does not leave inconsistent state on order failure.
- [ ] `OrderPlaced`, `OrderCancelled`, `PaymentCaptured`, and `PaymentFailed` events include `event_id`.
- [ ] Order status history is written for every transition.
- [ ] Merchant queue supports accept, reject, preparing, and ready-for-pickup actions.

## Verification

- Place a delivery order through the customer app.
- Verify order, payment, stock, status history, and merchant queue.
- Verify payment failure does not decrement stock permanently.
