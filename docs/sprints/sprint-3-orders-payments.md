# Sprint 3: Orders And Payment Capture

## Goal

A customer can place a paid delivery order for a pet store, and the merchant can manage it.

## Acceptance Checklist

- [x] Customer cart, default address lookup, checkout, and sandbox payment-result flow are wired to backend.
- [x] Order service revalidates live catalog price and stock.
- [x] Payment service records documented sandbox success/failure equivalent.
- [x] Stock decrement is atomic and does not leave inconsistent state on order creation failure, payment failure, cancellation, or rejection.
- [x] `OrderPlaced`, `OrderCancelled`, `PaymentCaptured`, and `PaymentFailed` events include `event_id`.
- [x] Order status history is written for every transition.
- [x] Merchant queue supports accept, reject, preparing, and ready-for-pickup actions through the order status API.

## Verification

- Place a delivery order through the customer app.
- Create a default customer address through `/api/v1/addresses` before checkout; production checkout must fail visibly if no default address exists.
- Verify order, payment, stock, status history, and merchant queue.
- Verify payment failure does not decrement stock permanently.

## Proof Notes

- Backend unit proof: `./gradlew :catalog-service:test :order-service:test :payment-service:test`.
- Static sprint proof: `python3 backend/verify_sprint3.py`.
- Local stack startup: `scripts/start-sprint3-stack.sh start` and leave the terminal open while testing.
- Live API proof: `python3 backend/verify_sprint3_live.py`.
- Checkout success path creates an order, records `PaymentCaptured`, and keeps reserved stock decremented.
- Checkout failure path creates an order, records `PaymentFailed`, cancels the order, and restores reserved stock through `/api/v1/catalog/offerings/{offeringId}/restore-stock`.
- Live API proof captured on 2026-07-01 against restarted local `catalog-service`, `order-service`, and `payment-service`.
  - Run ID: `8d07f99d`
  - Success order: `9142d841-401f-4aaa-b5c2-4c1766d4720a`, event `PaymentCaptured`, event id `80088f46-9389-427a-bb58-eb2791b33177`, stock `10 -> 9`, order status `PLACED`, history rows `1`.
  - Failure order: `f7f12446-14e4-445d-8d44-a844936ee38f`, event `PaymentFailed`, event id `f7c9549e-9403-4a9b-864f-c1939e372efd`, stock restored to `10`, order status `CANCELLED`, history rows `2`.
- Repeatable live verifier proof captured through `backend/verify_sprint3_live.py` on 2026-07-01.
  - Run ID: `1718501e`
  - Success order: `a6a9515c-9a95-4f70-9ff3-61c7619a88c9`, event `PaymentCaptured`, event id `71b2398b-9f51-42cc-adb7-6d472b1b2b6d`, stock after checkout `9`, order status `PLACED`, history rows `1`.
  - Failure order: `db0759dd-a62a-48cb-bd20-c58398b4edef`, event `PaymentFailed`, event id `f418913e-6dcb-48c7-87fd-1b54b24cfdc6`, stock restored to `10`, order status `CANCELLED`, history rows `2`.
- Customer app UI proof captured on 2026-07-01 with `EXPO_PUBLIC_ALLOW_DEMO_MODE=false`.
  - App opened at `http://localhost:8101/shop`, signed in with a real Supabase customer, loaded live Shop discovery/catalog data, tapped `Pay`, then tapped `Fail`.
  - Success UI order: `b29a0fc6-776b-40d0-a81c-919109a45e8e`, transaction `SUCCESS`, gateway id `sandbox_captured_b29a0fc6-776b-40d0-a81c-919109a45e8e`, order status `PLACED`, history rows `1`.
  - Failure UI order: `3deaa5d8-55fd-4bcd-902d-b43fc9ee4920`, transaction `FAILED`, gateway id `sandbox_failed_3deaa5d8-55fd-4bcd-902d-b43fc9ee4920`, order status `CANCELLED`, history rows `2`, reserved stock restored.
