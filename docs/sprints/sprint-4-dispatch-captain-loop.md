# Sprint 4: Dispatch And Captain Delivery Loop

## Goal

A pet-store order can move from ready for pickup to delivered through a real captain flow.

## Acceptance Checklist

- [ ] Captain can go online/offline and publish current location.
- [ ] Dispatch consumes order-ready events and offers jobs to nearest available captains.
- [ ] Offer expiry retries after 30 seconds.
- [ ] Max-attempt failure path is visible to ops and does not silently cancel without trace.
- [ ] Captain can accept, mark picked up, and mark delivered.
- [ ] Delivery proof flow is implemented or explicitly deferred.
- [ ] Captain earnings are recorded after delivery.

## Verification

- Simulate ready-for-pickup order with multiple captains.
- Verify timeout, reassignment, accept, pickup, delivery, and earnings.
