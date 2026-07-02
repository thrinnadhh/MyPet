# Sprint 4: Dispatch And Captain Delivery Loop

## Goal

A pet-store order can move from ready for pickup to delivered through a real captain flow.

## Acceptance Checklist

- [x] Captain can go online/offline and publish current location.
- [x] Dispatch consumes order-ready events and offers jobs to nearest available captains.
- [x] Offer expiry retries after 30 seconds.
- [x] Max-attempt failure path is visible to ops and does not silently cancel without trace.
- [x] Captain can accept, mark picked up, and mark delivered.
- [x] Delivery proof flow is implemented with pickup and delivery proof-code endpoints.
- [x] Captain earnings are recorded after delivery.

## Verification

- Simulate ready-for-pickup order with multiple captains.
- Verify timeout, reassignment, accept, pickup, delivery, and earnings.

Automated proof:

- Static: `python backend/verify_sprint4.py`
- Backend: `./gradlew :dispatch-service:test :captain-service:test`
- Mobile: merchant/captain app `npm run typecheck` and `npm run lint`
- Live flow: `python backend/verify_sprint4_live.py` with provider, order, dispatch, captain, Kafka, Redis, and Postgres running. The live verifier calls local services directly and supplies the trusted headers that the gateway injects after JWT validation.

Implementation notes:

- Offer response is bound to the authenticated captain context.
- Pickup and delivery progress is recorded through dispatch endpoints, which update order status using the assigned captain id.
- Dispatch failure after max attempts publishes `DispatchJobFailed` with `event_id`, `occurred_at`, `order_id`, and `job_id`; the customer order is not silently cancelled.
- Captain earnings remain owned by captain-service and are created from the delivered order event.

Latest live proof captured:

- Run ID: `319bc69c`
- Order: `15d23aa4-d926-4e61-acb9-fcfdab61811a`
- Dispatch job: `7c7292d3-6f20-4715-bebf-d9ed651db621`
- Accepted captain: `b319e4b4-9c6c-485b-8f7b-605718f7a0d7`
