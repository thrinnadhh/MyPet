# Architecture And Code Quality Actions

This file captures the highest-priority follow-up from the codebase audit. It is ordered by risk, not by implementation effort.

## P0: Identity And Authorization Boundary

- [ ] Strip inbound `X-User-Id` and `X-User-Role` at the API Gateway before injecting values derived from a validated JWT.
- [ ] Remove hardcoded admin API keys from service code.
- [x] Add route role guards for provider approval, catalog writes, order status changes, appointment status changes (Completed in Sprint 10), payout calculation, promotions, dispatch responses (Completed in Sprint 10), and billing.
- [x] Services should treat gateway headers as internal context, not user input. Request bodies should not be allowed to choose `customerId`, `ownerUserId`, `staffId`, or privileged role (Enforced for appointments & dispatch in Sprint 10).

## P0: Transaction And Event Reliability

- [x] Add an outbox table per write-owning service for order, appointment, payment, dispatch, review, provider, and billing events.
- [x] Add `event_id` to all event payloads and a processed-events store for each consumer.
- [x] Configure retry and DLQ topics before adding more consumers.
- [x] Replace best-effort `println` failures around Kafka publishing with observable failed-event records.

## P1: Service Boundaries

The current system is a microservice-style repo, but several services directly read other service schemas. Choose one of these paths:

1. **Modular monolith until launch:** keep one deployable backend with module boundaries, shared transactions, and faster iteration.
2. **Strict service boundaries:** each service owns its schema, cross-service reads happen through APIs/events/read models, and DB roles enforce that boundary.

Do not stay in the middle: it keeps microservice operational cost while preserving monolith coupling.

### Allowed Exceptions & Trust Boundaries

*   **Cross-Schema Read Exception:** `dispatch-service` (`DispatchService.getProviderCoordinates`) directly joins the `orders.orders` and `providers.providers` tables. This is an explicit, documented exception to the strict microservice database isolation boundary to avoid API roundtrips and minimize latency in the critical dispatch allocation loop.
*   **Service Port Trust Boundaries:** Internal microservices trust caller identity injected via `X-User-Id` and `X-User-Role` headers. The API Gateway serves as the hard security trust boundary: it MUST validate all incoming external Supabase JWTs, strip any client-supplied `X-User-*` headers, and inject the authenticated context. Direct microservice ports (e.g., 8080-8089) must not be exposed to the public internet and must only accept traffic from the gateway or internal VPC subnet.

## P1: Mobile Integration Quality

- [ ] Centralize API base URL configuration for both apps.
- [ ] Fail visibly when required production env vars are missing.
- [ ] Move demo providers/catalog/slots into explicit dev fixtures.
- [ ] Split large screens into feature hooks and components once behavior stabilizes.

## P1: Billing Add-on Hardening

- [ ] Resolve store/staff identity from authenticated context.
- [ ] Submit or enqueue the current cart before clearing it.
- [ ] Use atomic stock decrement queries guarded by `stock_quantity >= quantity`.
- [ ] Add a concurrency test proving two bill submissions cannot oversell the same item.
- [ ] Keep idempotency keys generated at bill-finalization time and reuse them across retries.

## P2: Repo Hygiene

- [ ] Remove generated `bin/`, `build/`, `.gradle/`, `.expo/`, and `node_modules/` artifacts from version control.
- [ ] Keep `imp files/` either documented as source inputs or moved under `docs/source/`.
- [ ] Add pre-commit or CI checks for TypeScript, Gradle tests, and generated-artifact detection.
