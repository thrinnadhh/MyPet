# M3 Security and Gateway Consolidation

## Overview

Move the API-edge security controls into `mypet-application` without removing or changing the existing Spring Cloud Gateway deployment. The consolidated controls remain disabled by default until traffic cutover, but are fully executable and verified in integration tests.

## Project Type

Backend — Kotlin, Spring Boot MVC, Spring Security resource server.

## Success Criteria

- JWT validation supports the existing HS256 secret, ES256/RS256 JWK set and explicit local/test unsigned mode.
- Inbound identity and internal-trust headers are removed and replaced only from a validated JWT.
- Existing public-path and role-gated authorization rules are represented in the application.
- Explicit-origin credentialed CORS behavior remains compatible and exposes request/idempotency/rate-limit headers.
- Every response has a validated or generated `X-Request-Id`.
- Configurable client throttling returns HTTP 429 with `Retry-After`.
- Unsafe requests carrying `Idempotency-Key` are replayed safely and conflicting payload reuse returns HTTP 409.
- Existing gateway routes, service applications, APIs, migrations, Compose stack and mobile clients remain unchanged.
- Complete backend, mobile, production-hardening and clean-volume full-stack validation passes.

## Architecture Decisions

- `mypet.edge.enabled=false` by default. M3 installs a shadow-ready edge boundary; M9 performs traffic cutover.
- The servlet application translates gateway behavior rather than importing WebFlux/Gateway classes.
- Rate limiting and idempotency use bounded in-memory stores for deterministic shell operation. Their interfaces isolate later distributed-store adapters.
- Spoofable identity headers are never trusted, including on public requests.
- Unsigned JWT parsing is restricted to active `local`, `dev` or `test` profiles.
- Current `api-gateway` source and deployment remain the production rollback path.

## Task Breakdown

1. **Security policy** — security-auditor + backend-specialist
   - Input: current gateway JWT/CORS/public-path/role rules.
   - Output: servlet resource-server configuration and shared authorization policy.
   - Verify: authentication, public path and RBAC integration tests.
2. **Identity boundary** — security-auditor
   - Input: current trusted identity headers and Supabase claims.
   - Output: sanitized, JWT-derived request identity.
   - Verify: spoofing and role-normalization tests.
3. **Request infrastructure** — backend-specialist + devops-engineer
   - Input: request correlation and gateway client-IP behavior.
   - Output: request-ID and bounded token-bucket filters.
   - Verify: response correlation and 429 tests.
4. **Idempotency boundary** — backend-specialist + test-engineer
   - Input: unsafe HTTP methods and `Idempotency-Key` contract.
   - Output: fingerprinted response replay with TTL and conflict detection.
   - Verify: replay, conflict and invalid-key tests.
5. **Compatibility and rollback** — architecture + mobile-developer
   - Input: existing distributed deployment and client contracts.
   - Output: no gateway/service/mobile modification and documented enablement switch.
   - Verify: repository diff, mobile CI and full-stack smoke.

## Rollback

Leave `MYPET_EDGE_ENABLED=false` and continue routing through `api-gateway`. Reverting the M3 branch removes only dormant application-edge controls and tests; no data, routes, service entry points or migration history are affected.

## Phase X Verification

- [ ] `:mypet-application:test`
- [ ] `:mypet-application:bootJar`
- [ ] Complete backend Gradle tests
- [ ] Generated-artifact checks
- [ ] Static production-hardening gates
- [ ] Customer mobile validation
- [ ] Merchant/captain mobile validation
- [ ] Clean-volume Full Stack Smoke
