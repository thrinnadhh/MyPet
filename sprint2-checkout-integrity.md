# Sprint 2 — Checkout, Payment & Inventory Integrity

## Overview
Make Order, Payment, Inventory, Coupon and Delivery pricing reconcile as one production-grade checkout system. Sprint 1's canonical order lifecycle remains authoritative; Sprint 2 must not re-couple payment success to merchant acceptance.

## Project Type
BACKEND + MOBILE integration. Backend is primary; React Native/Expo customer changes are limited to removing client-owned reconciliation/demo pricing divergence.

## Success Criteria
- Inventory reserve/restore operations use unique order/item operation identities, not offering+quantity.
- 100 independent qty-1 purchases against stock 100 result in stock 0, with no false idempotency suppression or oversell.
- Cashfree webhook/payment service owns payment reconciliation and OrderService observes PaymentCaptured/PaymentFailed/PaymentExpired events.
- Customer app never calls order confirmation to reconcile successful payment.
- Pending online orders expire deterministically after a configurable timeout (default 30 minutes), releasing inventory/coupon and cancelling the order with an auditable reason.
- Checkout compensation restores stock/coupon on every failed step and incomplete orders are not merchant-actionable.
- Delivery quote uses merchant origin + customer destination + serviceability + base/distance pricing; one merchant per cart remains enforced.
- Demo and production share the same pricing engine/business rules.
- Pricing hierarchy is subtotal - item discount - normal coupon - compatible loyalty + delivery + taxes = payable.
- Ordinary loyalty and normal coupon are incompatible by default; special 10-star reward coupon can stack with a normal coupon.
- Cashfree is the only active customer payment provider; dead Razorpay production paths/config/tests are removed or isolated as legacy migration evidence only.

## Tech Stack
- Kotlin / Spring Boot 3 / JPA / PostgreSQL / Flyway
- Kafka/outbox event handoff between PaymentService and OrderService
- Cashfree webhook as source of payment truth
- React Native + Expo customer app
- Existing GitHub Actions / connected E2E / modular-monolith and distributed rollback topology

## Agent Assignment
- database-architect: idempotency persistence, migrations, constraints/indexes, concurrency safety
- backend-specialist: checkout orchestration, payment events/workers, pricing/delivery/coupon rules
- mobile-developer: remove client reconciliation and demo business-rule forks
- qa-automation-engineer/test-engineer: concurrency, duplicate/replay, failure/abandonment and reconciliation matrix
- devops-engineer: CI/env cleanup for Cashfree-only production configuration

## Task Breakdown

### S2-01 Inventory idempotency
INPUT: current catalog/order stock mutation API and persistence.
OUTPUT: reserve/restore operations keyed by unique `RESERVE:{orderId}:{orderItemId}` / `RESTORE:{orderId}:{orderItemId}` equivalent identities.
VERIFY: duplicate same operation is idempotent; 100 unique customers/orders against stock=100 ends at exactly 0; concurrent over-reservation is rejected without negative stock.

### S2-02 Server-owned payment reconciliation
INPUT: Cashfree webhook -> PaymentService and existing client confirmation call.
OUTPUT: `PaymentCaptured` event/outbox consumed by OrderService, which sets only `paymentStatus=SUCCESS`; customer app observes order/payment state and performs no confirm mutation.
VERIFY: webhook success reconciles order without app callback; duplicate webhook is idempotent; order lifecycle remains PLACED until merchant action.

### S2-03 Pending payment timeout/abandonment
INPUT: PLACED+PENDING online orders and Cashfree failure/drop events.
OUTPUT: `PendingPaymentOrderWorker` with configurable default 30-minute timeout; FAILED/EXPIRED payment state, CANCELLED order, stock/coupon release, reason/history/event.
VERIFY: timeout, explicit failure and user-dropped events converge to the same deterministic reconciliation and are replay-safe.

### S2-04 Checkout transaction consistency
INPUT: quote -> stock -> coupon -> order -> items -> payment creation pipeline.
OUTPUT: transaction/compensation orchestration with stable idempotency identity and no merchant-actionable partial order.
VERIFY: injected failure at each step leaves inventory/coupon/order/payment in a reconciled state.

### S2-05 Real delivery quotation
INPUT: provider coordinates, customer coordinates and serviceability rules.
OUTPUT: route/serviceability quotation with base fee + distance fee and single-merchant-cart enforcement.
VERIFY: same route produces same quote in demo/prod; unserviceable route is rejected; fee changes with distance according to configured rules.

### S2-06 Demo pricing parity
INPUT: demo checkout and production checkout.
OUTPUT: one pricing engine/contract; demo changes only data source, payment adapter and external side effects.
VERIFY: identical cart/address inputs produce identical subtotal/discount/delivery/tax/payable outputs.

### S2-07 Coupon + loyalty hierarchy
INPUT: coupon reservation/redemption and loyalty reward contracts.
OUTPUT: explicit compatibility model and pricing order; ordinary loyalty cannot stack with normal coupon, special 10-star reward coupon can.
VERIFY: compatibility matrix + duplicate coupon/reward application tests + exact payable assertions.

### S2-08 Cashfree cleanup
INPUT: payment controller/services/env/tests.
OUTPUT: Cashfree declared active provider; unused Razorpay creation/webhook aliases/config/tests removed or quarantined from production execution.
VERIFY: production payment route contains no active Razorpay path and static gates reject reintroduction.

## Acceptance Matrix
Automate and verify exact reconciliation across Order / Payment / Inventory / Coupon for:
1. COD success
2. Online payment success
3. Online payment failure
4. Online payment abandonment/timeout
5. Duplicate payment webhook
6. Duplicate order API request
7. Duplicate coupon application
8. Two customers buying same SKU simultaneously
9. 100 concurrent qty-1 reservations from stock 100
10. Order cancellation
11. Merchant rejection

## Phase X — Verification
- Backend Gradle build/tests
- Flyway uniqueness/schema validation
- Canonical order contract drift check
- Customer mobile lint/type/build validation
- Repo `.agents` checklist/verification scripts where CI exposes equivalents
- Connected P2B E2E
- Barcode/inventory E2E
- Full Stack Smoke: modular-monolith + distributed rollback
- No completion claim until all money/stock acceptance cases reconcile exactly
