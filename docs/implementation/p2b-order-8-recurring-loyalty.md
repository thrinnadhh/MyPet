# P2B Order 8 — Recurring orders and loyalty

## Recurring-order beta policy

MyPet beta implements scheduled reorder reminders with customer confirmation. It does not silently create orders or charge stored payment methods.

Allowed cadences are exactly:

- 7 days
- 15 days
- 25 days
- 30 days
- 35 days

A subscription references a previously delivered or completed order. When due, the scheduler changes the subscription to `AWAITING_CONFIRMATION` and emits a durable reminder event. Customer confirmation invokes server-side reorder validation for provider availability, offering state, stock and current price. A successful validation directs the customer to request a new quote and complete payment.

Supported controls:

- pause
- resume
- skip next reminder
- change cadence
- change quantity multiplier
- change address
- cancel
- confirm due reorder

## Loyalty contract

- Loyalty is scoped to customer and provider.
- The first welcome star may be claimed once when enabled.
- Later stars are awarded only from completed qualifying order or appointment events.
- The target remains 10 stars.
- Merchant reward choices are ₹50, ₹100, ₹150 and ₹200.
- Provider writes require the authenticated merchant to own the selected provider.
- A missing user identity is rejected; no fallback actor is generated.
- Program changes are recorded in the loyalty audit log.
- The special coupon issued on completing 10 stars is marked as eligible to combine with one normal coupon. Ordinary promotional and loyalty discounts remain non-stackable unless that explicit reward is used.

## Safety guarantees

- no automatic payment mandate in beta
- no client-created lifecycle state
- no duplicate active subscription for the same source order
- no scheduler order creation
- no spoofed merchant role or user headers
- no hardcoded provider ID
- durable reminder events
- server-authoritative ownership, cadence and reward validation

## Release boundary

Secure medical upload, support/dispute completion, physical-device QA, final connected E2E certification and internal beta distribution remain Orders 9–12.
