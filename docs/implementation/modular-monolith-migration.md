# MyPet Modular-Monolith Migration

## Objective

Consolidate the Spring Boot microservices into one production deployable while preserving public APIs, mobile behavior, production data, module ownership and a tested rollback path.

## Non-negotiable controls

- Merge one milestone before starting the next production change.
- Preserve `/api/v1/**` contracts unless a separate compatibility change is approved.
- Never access another module's repository directly.
- Keep the distributed stack operational until M8 verification and M9 cutover complete.
- Require backend, architecture, production-hardening, mobile and clean-volume smoke evidence before merge.

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

### M7 — Scheduled jobs — in progress

Consolidate scheduler ownership and support independently scalable worker processes without changing job semantics.

Delivered on the M7 branch:

- immutable catalog of fourteen jobs owned by eight modules;
- runtime roles `ALL`, `API`, `WORKER` and `DISABLED`;
- API-mode removal of Spring's scheduled annotation processor before singleton creation;
- legacy default `ALL` for unchanged standalone deployments;
- one shared JDBC ShedLock provider implementation using database time;
- thin module declarations preserving all eight existing schema-owned lock tables;
- explicit distributed locks on every scheduled method, including appointment hold cleanup;
- API and worker Spring profiles for every scheduler-owning module;
- optional Compose API overlay and unexposed worker launch/stop scripts;
- scheduler role, ownership, cadence and lock metadata through `/actuator/info`.

M7 exit criteria:

- catalog reports fourteen jobs and eight owners;
- nine `@Scheduled` declarations have nine explicit `@SchedulerLock` declarations;
- all eight lock configurations use the shared factory;
- API mode removes scheduler registration while retaining business beans;
- worker and legacy modes retain scheduler registration;
- all eight owners provide API and worker profiles;
- optional split deployment covers all owners without publishing worker service ports;
- complete backend, architecture, generated-artifact and production-hardening checks pass;
- both mobile applications pass;
- clean-volume Full Stack Smoke passes in unchanged legacy `ALL` mode;
- no migration, public API, Kafka topic, outbox contract or base Compose service is removed.

### M8 — Full feature verification

Verify customer, provider, catalog, appointment, order, payment, loyalty, captain, dispatch, review, notification, chat, content and admin workflows end to end.

### M9 — Infrastructure cutover

Deploy the consolidated API and optional workers alongside PostgreSQL and Redis. Perform rollback-tested traffic cutover and production observation.

### M10 — Remove legacy deployment

After the cutover window succeeds, remove obsolete service containers, gateway routes, internal URLs, duplicated configuration and superseded consumers.
