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
- verifies actuator contracts through real HTTP integration tests;
- is built and tested by the existing Gradle CI suite.

Exit evidence:

- `:mypet-application:test` passed;
- `:mypet-application:bootJar` succeeded;
- complete backend, production-hardening and both mobile validation suites passed;
- clean-volume Full Stack Smoke passed without removing any existing service or Compose deployment.

M1 merge commit: `01efdb22dcceb803341a2f11c38ab3bf7d48f819`.
M1 closure merge commit: `93bf1672d7b300d934bba589f1fb7ed1493cf055`.

### M2 — Internal modules — complete

Delivered explicit internal-module boundaries while preserving the distributed runtime:

- each module owns a stable descriptor containing its id, base package and legacy application class;
- `mypet-application` depends on every module non-transitively so business jars are packaged without activating infrastructure;
- component scanning remains restricted to `com.pawsnearme.application`, keeping legacy boot entry points dormant;
- an immutable application-owned catalog exposes linked module ids through `/actuator/info`;
- architecture tests reject direct business-service Gradle dependencies and cross-module repository access;
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
- spoofable user/internal headers are removed and identity derives only from a validated JWT;
- public-route and role-guard authorization behavior is represented in the application;
- explicit-origin credentialed CORS is enforced;
- request IDs, HTTP 429 rate limiting and bounded idempotency replay are application-owned;
- edge mode is exposed through `/actuator/info`;
- `MYPET_EDGE_ENABLED=false` remains the default until traffic cutover.

Exit evidence:

- public, protected, role-restricted, CORS, request-ID, rate-limit and idempotency HTTP tests passed;
- complete backend, generated-artifact, production-hardening and both mobile checks passed;
- clean-volume Full Stack Smoke run `30683380626` passed;
- Java & Mobile CI run `30683380636` passed.

M3 merge commit: `445cab6c7af8ec777746b460451e1df0a605d25d`.

### M4 — Database consolidation — complete

Delivered one application-owned PostgreSQL connection and Flyway coordinator without rewriting production history:

- each service's unchanged SQL files are copied under an application-only migration namespace;
- original service migrations, versions and descriptions remain in place;
- every owner reuses its exact legacy schema and `flyway_schema_history_*` table;
- discovery remains a distinct migration owner inside the existing `providers` schema;
- twelve Flyway owners execute sequentially through one shared Hikari pool;
- existing non-empty schemas retain version-1 baseline behavior;
- automatic datasource/Flyway configuration is disabled so the shell still starts without PostgreSQL when M4 is off;
- migration phase, owner count and current versions are exposed through actuator info and readiness health;
- a Compose shadow runtime operates on port 8093 while every legacy service remains available for rollback;
- `MYPET_DATABASE_ENABLED=false` remains the default.

Exit evidence:

- all twelve namespaced migration bundles were packaged;
- each owner mapped to its original schema and unique history table;
- clean-volume PostgreSQL validation retained all 13 schemas and all twelve history tables;
- zero failed Flyway records were found;
- `mypet-application` reported database phase `READY` with twelve completed owners;
- Java & Mobile CI run `30684353525` passed;
- Full Stack Smoke run `30684353527` passed.

M4 merge commit: `3220485ef7a988b8e8d1a6d179bf3a1048395ed3`.

### M5 — Replace synchronous remote calls — complete

Delivered transport-neutral module capabilities while retaining distributed rollback:

- catalog, provider, payment, discovery and order capabilities are defined in `common`;
- owning modules expose direct Spring facades;
- standalone services receive conditional HTTP adapters when direct facades are absent;
- order, appointment, dispatch, content and notification business services no longer execute internal HTTP routes;
- external Razorpay and Expo/FCM integrations remain unchanged;
- contract inventory and binding mode are exposed through `/actuator/info`;
- architecture tests reject transport execution from migrated business services.

Exit evidence:

- complete backend, typed-contract and transport-boundary tests passed;
- generated-artifact and production-hardening checks passed;
- customer and merchant/captain mobile validation passed;
- Java & Mobile CI run `30687169023` passed;
- Full Stack Smoke run `30687169021` passed;
- distributed fallback adapters remained operational.

M5 merge commit: `ff6b701019b1e6643452cfef8b7bb3cb3f252228`.

### M6 — Events and background work — in progress

Classify asynchronous work and install guarded replacements without removing the Kafka rollback path:

- direct consistency work remains on M5 typed module calls;
- projection and cache fan-out is classified as an in-process domain event;
- notification, reminder, dispatch and externally visible side effects remain durable outbox jobs;
- the common outbox poller delegates publication to a transport-neutral publisher;
- `KAFKA_ONLY` remains the default in every standalone service;
- `DUAL_SHADOW` emits a non-executing module event while Kafka remains authoritative;
- `IN_PROCESS_ONLY` is rejected unless the topic has a verified consumer bridge;
- existing Kafka listeners remain available as rollback adapters;
- in-process bridges reuse the existing listener business handlers instead of duplicating logic;
- vaccination reminder events are persisted to the provider outbox instead of sent directly;
- workflow classification, replacement readiness and delivery mode are exposed through `/actuator/info`.

M6 cutover controls:

- shadow events must never invoke business handlers;
- in-process bridges activate only when delivery mode is exactly `IN_PROCESS_ONLY`;
- an outbox record is marked published only after the selected publisher returns successfully;
- a failure stops the owner poller to preserve event ordering;
- topics without verified replacement remain Kafka-only;
- no Kafka topic, retry topic, DLQ, consumer group, migration, public API or Compose service is removed.

M6 exit criteria:

- workflow catalog validation and outbox routing tests pass;
- every current Kafka consumer topic has a guarded in-process bridge or is explicitly marked pending;
- all six outbox owners default to Kafka and support routed publication;
- vaccination reminders use the durable outbox path;
- actuator reports direct-call, in-process-event and durable-job counts;
- complete backend, generated-artifact and production-hardening checks pass;
- both mobile applications remain green;
- clean-volume Full Stack Smoke proves the distributed Kafka path remains operational.

### M7 — Scheduled jobs

Consolidate scheduler ownership and ShedLock configuration, with an optional worker runtime profile for horizontally scaled background processing.

### M8 — Full feature verification

Verify customer, provider, catalog, appointment, order, payment, loyalty, captain, dispatch, review, notification, chat, content and admin workflows end to end.

### M9 — Infrastructure cutover

Deploy the consolidated API and optional worker alongside PostgreSQL and Redis. Perform rollback-tested traffic cutover and production observation.

### M10 — Remove legacy deployment

After the cutover window succeeds, remove obsolete service containers, gateway routes, internal service URLs, duplicated configuration and superseded event consumers.
