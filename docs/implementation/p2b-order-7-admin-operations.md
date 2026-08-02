# P2B Order 7 — Admin operations portal

## Scope

Order 7 replaces the legacy demo-oriented admin route with one responsive, ADMIN-only operations workspace. The route consumes live backend state and does not substitute local merchant, captain, dispute, support, content or configuration records when production requests fail.

## Operations modules

- Live order, delay, failed-payment, dispute and support counts
- Merchant and captain approval queues
- Order dispute resolution with mandatory notes
- Support-case visibility
- Pincode-level city, delivery and emergency-message controls
- Content banners, promotions and guide-writer access
- Immutable administrative audit history

## Service-area contract

`PUT /api/v1/orders/admin/operations/service-areas/{pincode}` requires:

- authenticated `ADMIN` role
- a valid six-digit Indian pincode
- city
- radius from 0.50 km to 100.00 km
- area and delivery availability flags
- a human-readable administrative reason
- request or trace identity

The write and its before/after representation are committed in one database transaction. The server, not the client, supplies the administrator identity and timestamp.

## Audit contract

Each service-area mutation records:

- administrator user ID
- action
- entity type and identifier
- previous value
- new value
- reason
- trace ID
- server timestamp

Audit rows are append-only through the application API. No update or delete endpoint is exposed.

## Production safety

- No production demo fallback in the admin route
- Backend ADMIN authorization on every new endpoint
- No client-side computation of operational counts
- Bounded audit reads
- Pincode and radius database constraints
- Traceable mutations

## Release boundary

Order 7 does not include recurring orders, loyalty, secure customer medical uploads, device QA, full connected E2E certification or beta distribution. Those remain Orders 8–12.
