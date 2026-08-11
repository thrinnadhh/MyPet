# Sprint 3 — Merchant → Dispatch → Captain End-to-End

## Overview
Connect the operational order path from merchant action through dispatch assignment, captain execution, OTP handover, and customer-visible status without changing the certified Sprint 1/2 lifecycle/payment invariants.

Target:
`PLACED → ACCEPTED → PREPARING → READY_FOR_PICKUP → ASSIGNED → PICKED_UP → DELIVERED → COMPLETED`

## Project Type
Multi-surface **BACKEND + MOBILE** change.

- Backend owners: order-service, dispatch-service, captain-service, common module contracts.
- Mobile owner: `apps/merchant-captain-app` and customer order status surfaces.
- Verification owner: backend unit/integration tests, mobile contract tests, connected/full-stack CI.

## Success Criteria
1. Merchant order detail exposes customer delivery contact/address, items, quantities, payment, price breakdown, timestamps, status and ordered history.
2. Merchant actions are server-gated exactly by lifecycle status: `PLACED → ACCEPTED/REJECTED`, `ACCEPTED → PREPARING`, `PREPARING → READY_FOR_PICKUP`.
3. `acceptedAt`, `preparingAt`, `readyAt`, actor identity/role and note are persisted.
4. Dispatch starts only from the `PREPARING → READY_FOR_PICKUP` status event and duplicate events cannot create duplicate jobs/offers.
5. Dispatch candidate eligibility requires approved/ACTIVE captain + explicitly online + fresh location + no active accepted/picked-up delivery.
6. Existing nearest-captain, offers, timeout/retry, pickup OTP and delivery OTP behavior remains intact.
7. Captain offer/job APIs provide safe operational route context: merchant pickup, customer drop, ETA/distance when available, without exposing OTP secrets.
8. Captain app performs offer accept/reject → pickup navigation → pickup OTP → PICKED_UP → customer navigation → delivery OTP → DELIVERED.
9. Customer status labels preserve canonical backend stages including ASSIGNED/PICKED_UP/DELIVERED/COMPLETED.
10. Failure matrix covers merchant rejection/non-response, no captain, reject/timeout retries, three rejects, wrong OTPs, network/replay, cancellations/races and duplicate READY event.

## Tech Stack
- Kotlin/Spring Boot/JPA/Kafka/Redis GEO/ShedLock
- React Native/Expo/TypeScript
- Existing generated order lifecycle contract
- Existing `orders.events` and `dispatch.events` event channels

## File Structure / Expected Touchpoints
- `backend/order-service/.../model/Models.kt`
- `backend/order-service/.../repository/Repositories.kt`
- `backend/order-service/.../service/OrderService.kt`
- `backend/order-service/.../controller/OrderController.kt`
- `backend/order-service/src/main/resources/db/migration/*`
- `backend/dispatch-service/.../service/DispatchService.kt`
- `backend/dispatch-service/.../controller/DispatchController.kt`
- `backend/dispatch-service/.../model|repository/*`
- `backend/captain-service/.../service/CaptainService.kt`
- `backend/captain-service/.../controller/*`
- `apps/merchant-captain-app/src/app/orders.tsx`
- `apps/merchant-captain-app/src/app/delivery.tsx`
- `apps/merchant-captain-app/src/services/merchant-orders.ts`
- `apps/merchant-captain-app/src/services/captain-deliveries.ts`
- `apps/customer-app/src/screens/orders-screen.tsx` and shared formatters/contracts
- corresponding backend/mobile tests and connected verification scripts

## Task Breakdown

### S3-01 Merchant operational detail
**Agent:** mobile-developer + backend-specialist  
**Input:** provider-owned order ID  
**Output:** provider-authorized operational detail DTO + dedicated detail UI  
**Verify:** provider can see required fields/history; another provider/customer cannot use merchant-only detail.

### S3-02 Merchant SLA lifecycle timestamps/history
**Agent:** backend-specialist + database-architect  
**Dependencies:** S3-01  
**Input:** valid merchant status transition  
**Output:** `acceptedAt`, `preparingAt`, `readyAt`, history actor role/id/note  
**Verify:** exact transition matrix and timestamp/history assertions.

### S3-03 Dispatch-on-ready exactly once
**Agent:** backend-specialist  
**Dependencies:** S3-02  
**Input:** `PREPARING → READY_FOR_PICKUP` event  
**Output:** one dispatch job; duplicate event no-op  
**Verify:** non-ready transitions do not dispatch; duplicate READY event retains one job/offer chain.

### S3-04 Captain eligibility contract
**Agent:** backend-specialist  
**Input:** Redis GEO candidates  
**Output:** only ACTIVE + online + fresh-location + non-busy captains are offerable  
**Verify:** each negative eligibility condition is excluded independently.

### S3-05 Dispatch assignment preservation/integration
**Agent:** backend-specialist + test-engineer  
**Dependencies:** S3-03, S3-04  
**Output:** nearest eligible offer → timeout/reject retry → accepted assignment → OTP chain  
**Verify:** no-captain, first reject, timeout, max attempts and duplicate responses.

### S3-06 Captain app operational workflow
**Agent:** mobile-developer  
**Dependencies:** S3-05  
**Output:** enriched offer and active delivery screens with pickup/drop route context and strict server transitions  
**Verify:** accept/reject, pickup OTP, delivery OTP, restart/resume and network error handling.

### S3-07 Customer live status parity
**Agent:** mobile-developer  
**Dependencies:** lifecycle contract  
**Output:** customer labels/timeline map 1:1 to canonical stages  
**Verify:** PLACED, ACCEPTED, PREPARING, READY_FOR_PICKUP, ASSIGNED, PICKED_UP, DELIVERED, COMPLETED all render distinctly.

### S3-08 Failure/race certification
**Agent:** test-engineer  
**Dependencies:** all above  
**Output:** deterministic test matrix for rejection/non-response/no captain/retries/network/OTP/cancellation/duplicate events  
**Verify:** Order + Dispatch + Captain assignment remain consistent and no invalid terminal/lifecycle transitions occur.

## Rollback Strategy
- Keep Sprint 3 on a stacked branch based on certified Sprint 2.
- No change to Sprint 1 canonical lifecycle or Sprint 2 payment/inventory contract.
- Prefer additive DTOs/migrations and compatibility-safe module methods.
- Preserve existing dispatch job/offer schema unless a migration is required for an explicit invariant.

## Phase X — Verification
- [ ] Backend Gradle build + unit tests
- [ ] Order/dispatch/captain integration tests
- [ ] Canonical generated contract drift check
- [ ] Merchant/Captain lint + TypeScript compile
- [ ] Customer lint + TypeScript compile
- [ ] Connected E2E contract
- [ ] Full Stack Smoke — modular monolith
- [ ] Full Stack Smoke — distributed/rollback
- [ ] Failure matrix assertions
- [ ] No duplicate dispatch from duplicate READY event
- [ ] No OTP values exposed in captain/customer APIs

Phase X is not complete until the same final head is green across the applicable CI gates.