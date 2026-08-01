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

### M3 — Security and gateway consolidation — complete

Delivered a servlet-native application edge boundary while preserving the separate gateway as active ingress and rollback path:

- Supabase JWT validation supports HS256 secrets and ES256/RS256 JWK sets;
- unsigned parsing is restricted to explicit local, dev or test profiles;
- spoofable user/internal headers are removed and identity is derived only from a validated JWT;
- the gateway's public-route and role-guard authorization matrix is represented in the application;
- explicit-origin credentialed CORS is enforced;
- `X-Request-Id`, HTTP 429 rate limiting and bounded idempotency replay are application-owned;
- edge mode is exposed through `/actuator/info`;
- `MYPET_EDGE_ENABLED=false` remains the default until traffic cutover.

Exit evidence:

- public, protected, role-restricted, CORS, request-ID, rate-limit and idempotency HTTP tests passed;
- complete backend, generated-artifact, production-hardening and both mobile checks passed;
- clean-volume Full Stack Smoke run `30683380626` passed;
- Java & Mobile CI run `30683380636` passed.

M3 merge commit: `445cab6c7af8ec777746b460451e1df0a605d25d`.

### M4 — Database consolidation — in progress

Introduce one application-owned PostgreSQL connection and Flyway coordinator without rewriting production history:

- package each service's unchanged SQL files under an application-only namespace such as `db/migration/provider`;
- retain the original service migration files, versions and descriptions in place;
- reuse the exact legacy schema and `flyway_schema_history_*` table for every owner;
- preserve discovery ownership in the existing `providers` schema with its separate discovery history table;
- run the twelve Flyway owners sequentially through one shared Hikari pool;
- baseline existing non-empty schemas at version 1 exactly as the service configurations do;
- disable Spring Boot's automatic datasource/Flyway configuration so the shell still starts without PostgreSQL when M4 is disabled;
- expose migration phase, owner count and current versions through `/actuator/info` and readiness health;
- add a Compose shadow runtime on port 8093 with edge ingress disabled;
- keep every legacy service and its Flyway configuration operational for rollback.

M4 exit criteria:

- all twelve namespaced migration bundles are present in the application artifact;
- each owner maps to its original schema and unique history table;
- a clean-volume PostgreSQL run creates or reuses all twelve history tables with zero failed records;
- `mypet-application` reports database phase `READY` and twelve completed owners;
- the distributed services, gateway, APIs, mobile validation and production-hardening checks remain green;
- no existing migration is renamed, deleted, reordered or edited solely for consolidation.

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
