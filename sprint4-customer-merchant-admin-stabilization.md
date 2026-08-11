# Sprint 4 — Customer, Merchant, Admin & New Feature Stabilization

Base: `main` after certified Sprint 3 transaction spine.
Branch: `agent/sprint4-customer-merchant-admin-stabilization`.

## Invariants

- Product orders use only the canonical order lifecycle: `PLACED -> ACCEPTED -> PREPARING -> READY_FOR_PICKUP -> ASSIGNED -> PICKED_UP -> DELIVERED -> COMPLETED`, with terminal `REJECTED` / `CANCELLED` where policy allows.
- Payment status is independent of order status.
- Customer, Merchant, Captain and Admin read the same canonical lifecycle; clients may add presentation labels such as “Arriving” but must not invent backend order states.
- Canonical RBAC roles for this sprint are exactly `CUSTOMER`, `MERCHANT`, `CAPTAIN`, `ADMIN`. `SUPER_ADMIN` is not introduced implicitly.
- MVP admin client decision: Merchant/Captain operational mobile app + Super Admin web. Both consume the same backend Admin API/RBAC; the mobile `/admin` surface is compatibility-only and must not define alternate backend contracts.
- Recurring orders revalidate and then create a normal `PLACED` order. They never create a parallel fulfillment lifecycle.
- Vet/Grooming remain appointment-domain workflows and never reuse `OrderStatus`.

## S4-01 — Canonical Customer Order Detail

Create `CustomerOrderDetailResponse` and return it from customer order detail instead of serializing raw `Order` entities. Include:

- orderId
- provider
- items
- pricing
- payment
- status
- flowStep
- statusHistory
- deliveryAddress
- deliveryContact
- captain
- timestamps
- cancellation
- invoice

The projection is server-owned and authorization aware.

## S4-02 — Canonical Customer Tracking

Tracking must include server truth for:

- paymentMethod
- paymentStatus
- captain
- ETA
- delivery status

The customer app must stop provider-name/detail/status fallbacks where the canonical DTO supplies the field.

## S4-03 — Admin observer/control plane

Expand the existing Admin operations snapshot to canonical lifecycle counters:

- ordersPlaced
- merchantPending
- preparing
- readyForPickup
- dispatchFailures
- pickedUp
- delivered
- cancelled
- paymentFailures
- refunds
- openSupportCases
- openDisputes

Admin order reads use the canonical order projections/state; no alternate lifecycle enum is introduced.

## S4-04 — Merchant/Admin separation

Keep the backend `UserRole` contract exactly:

`CUSTOMER`, `MERCHANT`, `CAPTAIN`, `ADMIN`.

Normalize user-visible/code naming so an `ADMIN` token is not presented as a distinct `SUPER_ADMIN` authorization role. Add regression tests for canonical roles.

## S4-05 — Admin client consolidation

MVP authority:

- Merchant/Captain mobile app: merchant/captain operations only.
- Super Admin web: administrative control plane.
- One backend Admin API and one `ADMIN` role.

The legacy mobile `/admin` route may remain only as compatibility/deprecation surface; new Admin order truth is implemented in the shared backend API and web client rather than duplicated state logic.

## S4-06 — Loyalty

Award exactly one purchase/service star only on trusted completion:

- product order `DELIVERED` -> +1 star
- grooming appointment `COMPLETED` -> +1 star
- vet appointment `COMPLETED` -> +1 star

Never award on `PLACED`, `ACCEPTED`, `CANCELLED`, `REJECTED` or payment refund.

Keep merchant-defined target/reward configuration; default/expected target remains 10 stars. Refund events reverse previously awarded stars idempotently.

## S4-07 — Recurring orders

Allowed cadences remain exactly `7, 15, 25, 30, 35` days.

When due:

`ACTIVE -> AWAITING_CONFIRMATION`

At confirmation:

1. revalidate source provider ownership/availability
2. revalidate each item stock and current price
3. revalidate delivery address/serviceability
4. create a fresh checkout quote
5. create a new normal order using the normal OrderService path
6. resulting order must be `PLACED`
7. schedule the next cadence only after normal order creation succeeds

No auto-charge and no parallel recurring fulfillment state machine.

## S4-08 — Vet/Grooming lifecycle

Keep slot availability in Catalog (`AVAILABLE`, `HELD`, `BOOKED`) and appointment business state separate.

Canonical appointment progression for paid-online service:

`HELD -> PAID -> CONFIRMED -> COMPLETED`

For pay-at-clinic, payment settlement may be represented at confirmation/completion without creating product `OrderStatus` values.

Appointment completion publishes/records service completion for loyalty and keeps existing Customer/Provider/Payment/Notification/Admin integrations.

## Certification gates

- Backend Kotlin compile/tests and bootJar
- Customer app typecheck/lint/tests
- Merchant/Captain app typecheck/lint/tests
- Super Admin web typecheck/tests
- P2B connected/device/customer/barcode regression gates
- Full Stack Smoke modular-monolith
- Full Stack Smoke distributed rollback
- New focused tests for canonical customer DTO, tracking, Admin snapshot, RBAC, loyalty completion/refund, recurring creation into `PLACED`, and appointment lifecycle separation
