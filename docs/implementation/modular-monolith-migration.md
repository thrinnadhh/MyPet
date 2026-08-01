# MyPet Modular-Monolith Migration

## Objective

Consolidate the existing Spring Boot microservices into one production deployable while preserving public API contracts, mobile-app behavior, database data, and clear business-module boundaries.

## Non-negotiable controls

- Complete and merge one milestone before starting the next milestone's production changes.
- Preserve existing `/api/v1/**` contracts unless a separately reviewed compatibility change is required.
- Do not allow one business module to access another module's repository directly.
- Keep the M0 distributed stack operational until M8 verification and M9 cutover are complete.
- Maintain an executable rollback path at every milestone.
- Require backend tests, mobile validation, production-hardening checks, and milestone-specific integration checks before merge.

## Specialist review lanes

The checked-in `.agents` workspace is used as the review framework:

- architecture and project-planner: module boundaries and milestone sequencing;
- backend-specialist: Spring Boot application and module integration;
- database-architect: schemas, Flyway and transaction ownership;
- security-auditor: authentication, authorization and trust-boundary changes;
- devops-engineer: CI, containers, health probes and cutover;
- test-engineer / QA automation: regression, integration and E2E evidence;
- mobile-developer: customer and merchant/captain contract compatibility.

Independent analysis and validation lanes may run concurrently. Writes to shared build, application, security and migration files remain serialized.

## Milestones

### M0 — Stable baseline — complete

- Clean-volume PostgreSQL bootstrap.
- All infrastructure healthy.
- All 13 backend applications ready.
- Backend tests, both mobile apps and production-hardening gates green.
- Representative public, authenticated and authorization-boundary smoke checks green.

Baseline merge commit: `f8319834f94c6bea7e0181aba07c87327c860f42`.

### M1 — Application shell — complete

Delivered a new `mypet-application` Spring Boot module that:

- starts independently without database, Redis, Kafka or business-module dependencies;
- exposes liveness, readiness, info and Prometheus actuator endpoints;
- supports graceful shutdown;
- verifies the actuator contracts through real HTTP integration tests;
- is built and tested by the existing Gradle CI suite.

Exit evidence:

- `:mypet-application:test` passed;
- `:mypet-application:bootJar` succeeded;
- the complete backend, production-hardening and both mobile validation suites passed;
- clean-volume Full Stack Smoke passed without removing any existing service or Compose deployment.

M1 merge commit: `01efdb22dcceb803341a2f11c38ab3bf7d48f819`.
M1 closure merge commit: `93bf1672d7b300d934bba589f1fb7ed1493cf055`.

### M2 — Internal modules — complete

Delivered explicit internal-module boundaries while preserving the distributed runtime:

- each module owns a stable descriptor containing its id, base package and legacy application class;
- `mypet-application` depends on each module non-transitively, so business jars are packaged without activating database, Redis, Kafka, gRPC, scheduler or service-specific dependencies;
- component scanning remains restricted to `com.pawsnearme.application`, keeping every legacy service `@SpringBootApplication` dormant in the consolidated JVM;
- an immutable application-owned catalog exposes linked module ids through `/actuator/info`;
- architecture tests reject direct business-service Gradle dependencies and direct access to another module's repository package;
- existing service boot jars and Compose deployments remain unchanged.

Exit evidence:

- all twelve module jars were linked and discoverable;
- only `MyPetApplication` was active;
- Gradle and repository boundary tests passed;
- complete backend, generated-artifact, production-hardening and both mobile checks passed;
- clean-volume Full Stack Smoke run `30681978754` passed.

M2 merge commit: `8f18799364fa558587a6c6f33c198860e1d06aae`.

### M3 — Security and gateway consolidation — in progress

Install a servlet-native application edge boundary while preserving the separate gateway as active ingress and rollback path:

- validate Supabase JWTs with HS256 secrets or ES256/RS256 JWK sets;
- restrict unsigned parsing to explicit local, dev or test profiles;
- remove spoofable user/internal headers and derive identity only from a validated JWT;
- reproduce the gateway's public-route and role-guard authorization matrix;
- enforce explicit-origin credentialed CORS;
- validate or generate `X-Request-Id` and return it on every edge-enabled response;
- enforce configurable bounded token-bucket rate limiting;
- replay successful unsafe requests carrying `Idempotency-Key` and reject conflicting reuse;
- expose edge mode through `/actuator/info`;
- keep `MYPET_EDGE_ENABLED=false` by default until traffic cutover.

Exit criteria:

- public, protected and role-restricted HTTP integration tests pass;
- spoofed identity/internal headers never reach application handlers;
- CORS preflight, request-ID, HTTP 429 and idempotency replay/conflict contracts pass;
- edge-enabled startup fails closed when JWT verification is not configured;
- `:mypet-application:test` and `:mypet-application:bootJar` pass;
- complete backend, production-hardening, mobile and clean-volume smoke validation remains green;
- no existing gateway route, service runtime, migration, Compose service or mobile contract is removed.

### M4 — Database consolidation

Run one application against the existing schemas while preserving migration history and ownership. Introduce an application-controlled Flyway strategy without rewriting production history.

### M5 — Replace synchronous remote calls

Replace internal REST/gRPC calls with typed module interfaces. Keep public controllers and request/response contracts stable.

### M6 — Events and background work

Classify workflows into direct calls, in-process domain events and durable outbox jobs. Remove Kafka only after every consumer workflow has a verified replacement.

### M7 — Scheduled jobs

Consolidate scheduler ownership and ShedLock configuration, with an optional worker runtime profile for horizontally scaled background processing.

### M8 — Full feature verification

Verify customer, provider, catalog, appointment, order, payment, loyalty, captain, dispatch, review, notification, chat, content and admin workflows end to end.

### M9 — Infrastructure cutover

Deploy the consolidated API and optional worker alongside PostgreSQL and Redis. Perform rollback-tested traffic cutover and production observation.

### M10 — Remove legacy deployment

After the cutover window succeeds, remove obsolete service containers, gateway routes, internal service URLs, duplicated configuration and superseded event consumers.
