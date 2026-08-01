# MyPet Modular-Monolith Migration

## Objective

Consolidate the Spring Boot microservices into one production deployable while preserving public APIs, mobile behavior, production data, module ownership and a tested rollback path.

## Non-negotiable controls

- Merge one milestone before starting the next production change.
- Preserve `/api/v1/**` contracts unless a separate compatibility change is approved.
- Never access another module's repository directly.
- Keep the distributed stack operational until M8 verification and M9 cutover complete.
- Require backend, architecture, production-hardening, mobile and clean-volume evidence before merge.

## Milestones

### M0 — Stable baseline — complete

- Clean-volume PostgreSQL bootstrap.
- All infrastructure and 13 backend applications healthy.
- Backend, mobile and representative API/security flows green.

Baseline merge: `f8319834f94c6bea7e0181aba07c87327c860f42`.

### M1 — Application shell — complete

- Added independently bootable `mypet-application`.
- Added liveness, readiness, info and Prometheus endpoints.
- Preserved all legacy deployments.

Merge: `01efdb22dcceb803341a2f11c38ab3bf7d48f819`.
Closure merge: `93bf1672d7b300d934bba589f1fb7ed1493cf055`.

### M2 — Internal modules — complete

- Linked all twelve business modules as dormant libraries.
- Added immutable module descriptors and architecture boundaries.
- Prevented cross-module repository access and accidental legacy app activation.

Merge: `8f18799364fa558587a6c6f33c198860e1d06aae`.

### M3 — Security and gateway consolidation — complete

- Added servlet-native JWT, authorization, CORS, request-ID, rate-limit and idempotency controls.
- Preserved the separate gateway as active ingress and rollback.
- Kept application edge mode disabled by default.

Merge: `445cab6c7af8ec777746b460451e1df0a605d25d`.

### M4 — Database consolidation — complete

- Added one application-owned Hikari pool and twelve sequential Flyway owners.
- Preserved every schema, migration file and exact history-table name.
- Added the port-8093 database shadow runtime.

Merge: `3220485ef7a988b8e8d1a6d179bf3a1048395ed3`.

### M5 — Replace synchronous remote calls — complete

- Added typed catalog, provider, payment, discovery and order module APIs.
- Added direct facades and conditional distributed HTTP adapters.
- Removed internal transport knowledge from migrated business services.

Merge: `ff6b701019b1e6643452cfef8b7bb3cb3f252228`.

### M6 — Events and background work — complete

- Classified 13 workflows as direct calls, in-process events or durable outbox jobs.
- Replaced hard-coded outbox Kafka publication with a routed publisher.
- Added `KAFKA_ONLY`, non-executing `DUAL_SHADOW` and guarded `IN_PROCESS_ONLY` modes.
- Added in-process bridges for verified consumer paths.
- Moved vaccination publication to the durable provider outbox.
- Retained all Kafka topics, listeners, retries, DLQs and consumer groups.

Exit evidence:

- Java & Mobile CI run `30688060245` passed.
- Full Stack Smoke run `30688060238` passed.
- Distributed Kafka rollback remained operational.

Merge: `abcfe92163c746a46362f4b4030ee2700870ef7e`.

### M7 — Scheduled jobs — complete

- Added an immutable catalog of fourteen jobs owned by eight modules.
- Added runtime roles `ALL`, `API`, `WORKER` and `DISABLED`.
- API mode removes periodic scheduler registration while retaining business beans.
- Legacy deployments continue to default to `ALL`.
- One shared JDBC ShedLock provider uses database time.
- All eight existing schema-owned lock tables and job cadences remain unchanged.
- Appointment hold cleanup gained an explicit distributed lock.
- Every scheduler owner has API and worker profiles.
- Optional worker processes use the same service images and publish no service ports.

Exit evidence:

- Java & Mobile CI run `30689531704` passed.
- Full Stack Smoke run `30689531712` passed in legacy `ALL` mode.
- Fourteen-job catalog, nine lock annotations, eight owner profiles and Compose split checks passed.

Merge: `7963e35e2a79927174c9b571ce9afd27b1b1790a`.

### M8 — Full feature verification — in progress

M8 adds a connected clean-volume business verification matrix without changing production traffic or removing the distributed rollback path.

Delivered on the M8 branch:

- immutable verification catalog for customer, provider, catalog, appointment, order, payment, loyalty, captain, dispatch, review, notification, chat, content and admin domains;
- one executable fixture graph linking identities, providers, offerings, slots, appointments, orders, payments, dispatch, reviews, notifications, chat and content;
- HTTP, database, authorization, asynchronous projection, idempotency, concurrency and scheduler evidence;
- application actuator metadata reporting fourteen domains;
- an explicit `cutoverAuthorized=false` and `legacyRollbackRequired=true` boundary;
- a dependency-free Python runtime verifier executed after the existing clean-volume smoke and extended feature checks;
- a static completeness gate that rejects missing domains, evidence classes or accidental cutover authorization;
- a detailed M8 failure-triage and evidence runbook.

M8 exit criteria:

- complete backend build and tests pass;
- the application reports M8 and exactly fourteen feature domains;
- generated-artifact and production-hardening checks pass;
- both mobile applications pass;
- clean-volume infrastructure and every distributed application are ready;
- all fourteen connected feature scenarios pass on the same PR head;
- authorization, concurrency, idempotency, scheduler, durable-outbox and asynchronous-projection evidence pass;
- no public API, migration, schema, Kafka topic, base Compose service or rollback component is removed;
- final validation evidence is recorded on the M8 PR.

M9 must not begin until M8 is reviewed and merged.

### M9 — Infrastructure cutover

Deploy the consolidated API and optional workers alongside PostgreSQL and Redis. Perform rollback-tested traffic cutover and production observation.

### M10 — Remove legacy deployment

After the cutover window succeeds, remove obsolete service containers, gateway routes, internal URLs, duplicated configuration and superseded consumers.
