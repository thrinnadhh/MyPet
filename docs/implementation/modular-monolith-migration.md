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
- clean-volume Full Stack Smoke run `30679851793` passed without removing any existing service or Compose deployment.

M1 merge commit: `01efdb22dcceb803341a2f11c38ab3bf7d48f819`.
M1 closure merge commit: `93bf1672d7b300d934bba589f1fb7ed1493cf055`.

### M2 — Internal modules — in progress

Link the twelve business services into `mypet-application` as explicit library modules while preserving the distributed runtime:

- each module owns a stable descriptor containing its id, base package and legacy application class;
- `mypet-application` depends on each module non-transitively so business jars are packaged without prematurely activating database, Redis, Kafka, gRPC, scheduler or service-specific dependencies;
- component scanning remains restricted to `com.pawsnearme.application`, keeping every legacy service `@SpringBootApplication` dormant in the consolidated JVM;
- an immutable application-owned catalog exposes linked module ids through `/actuator/info`;
- architecture tests reject direct business-service Gradle dependencies and direct access to another module's repository package;
- existing service boot jars and Compose deployments remain unchanged.

Exit criteria:

- the consolidated catalog contains exactly provider, catalog, discovery, order, appointment, dispatch, captain, notification, review, payment, chat and content;
- every legacy application class resource is present in the consolidated runtime classpath;
- only `MyPetApplication` is registered as a Spring Boot application bean;
- `mypet-application` starts without PostgreSQL, Redis, Kafka or gRPC infrastructure;
- `:mypet-application:test` and `:mypet-application:bootJar` pass;
- complete backend, production-hardening, mobile and clean-volume smoke validation remains green.

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
