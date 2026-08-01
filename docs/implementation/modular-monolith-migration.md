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

- Added the independently bootable `mypet-application` module.
- Added liveness, readiness, info and Prometheus actuator endpoints.
- Added graceful shutdown and an HTTP readiness integration test.
- Preserved the existing distributed deployment and mobile contracts.

M1 merge commit: `01efdb22dcceb803341a2f11c38ab3bf7d48f819`.

### M2 — Internal modules — in progress

Package the reusable portion of each business service as a separate `monolith` artifact while retaining every standalone boot JAR for rollback.

M2 controls:

- exclude standalone `*ServiceApplication` launchers from consolidated artifacts;
- exclude service `application.yml` files and Flyway migrations;
- assemble all 12 business-module artifacts in `mypet-application`;
- keep database, Flyway, Kafka, Redis and gRPC auto-configuration dormant;
- publish a typed module registry with package and schema ownership metadata;
- verify marker availability, launcher exclusion and resource isolation in a booted integration test;
- keep the M0 clean-volume distributed stack green.

### M3 — Security and gateway consolidation

Move JWT validation, identity derivation, CORS, request IDs, rate limits, idempotency and authorization into the application. Preserve external API behavior before retiring the separate gateway.

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
