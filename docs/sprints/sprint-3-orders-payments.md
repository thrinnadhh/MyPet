# Sprint 3: Orders And Payment Capture

## Goal

A customer can place a paid delivery order for a pet store, and the merchant can manage it.

## Acceptance Checklist

- [x] Customer cart, default address lookup, checkout, and sandbox payment-result flow are wired to backend.
- [ ] Order service revalidates live catalog price and stock.
- [x] Payment service records documented sandbox success/failure equivalent.
- [ ] Stock decrement is atomic and does not leave inconsistent state on order failure.
- [ ] `OrderPlaced`, `OrderCancelled`, `PaymentCaptured`, and `PaymentFailed` events include `event_id`.
- [ ] Order status history is written for every transition.
- [ ] Merchant queue supports accept, reject, preparing, and ready-for-pickup actions.

## Verification

- Place a delivery order through the customer app.
- Create a default customer address through `/api/v1/addresses` before checkout; production checkout must fail visibly if no default address exists.
- Verify order, payment, stock, status history, and merchant queue.
- Verify payment failure does not decrement stock permanently.
