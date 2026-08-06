# MyPet Modular-Monolith Migration

## Objective

Consolidate the Spring Boot microservices into one production deployable while
preserving public APIs, mobile behavior, production data, bounded-context
ownership and a tested rollback path.

## Non-negotiable controls

- Preserve `/api/v1/**` contracts.
- Never access another module's repository directly.
- Retain existing PostgreSQL schemas and Flyway history tables.
- Keep durable event delivery and scheduler locking explicit.
- Never run the monolith and distributed backend topologies simultaneously
  against the same production database.
- Merge only with backend, mobile, static-hardening, monolith boot and rollback
  evidence.

## Completed milestones

### M0 — Stable distributed baseline

Clean-volume PostgreSQL bootstrap, all infrastructure and thirteen backend
applications healthy, backend/mobile tests and representative security flows
green.

Baseline merge: `f8319834f94c6bea7e0181aba07c87327c860f42`.

### M1 — Application shell

Added independently bootable `mypet-application`, actuator probes and metrics
while preserving legacy deployments.

Merges: `01efdb22dcceb803341a2f11c38ab3bf7d48f819` and
`93bf1672d7b300d934bba589f1fb7ed1493cf055`.

### M2 — Internal modules

Linked all twelve business modules, added immutable descriptors and enforced
repository/package boundaries.

Merge: `8f18799364fa558587a6c6f33c198860e1d06aae`.

### M3 — Security and gateway consolidation

Added servlet-native JWT, authorization, CORS, request ID, rate limiting and
idempotency controls inside `mypet-application`.

Merge: `445cab6c7af8ec777746b460451e1df0a605d25d`.

### M4 — Database consolidation

Added one application-owned Hikari pool and sequential migration ownership for
all twelve existing schemas/history tables.

Merge: `3220485ef7a988b8e8d1a6d179bf3a1048395ed3`.

### M5 — Typed module interfaces

Replaced migrated synchronous HTTP collaboration with typed module contracts,
direct facades and conditional distributed fallback adapters.

Merge: `ff6b701019b1e6643452cfef8b7bb3cb3f252228`.

### M6 — Events and background work

Classified direct calls, in-process events and durable outbox jobs; retained
Kafka rollback and added guarded routing modes.

Merge: `abcfe92163c746a46362f4b4030ee2700870ef7e`.

### M7 — Scheduled jobs

Catalogued fourteen jobs, introduced API/worker runtime roles and shared JDBC
ShedLock construction using database time.

Merge: `7963e35e2a79927174c9b571ce9afd27b1b1790a`.

### M8 — Full feature verification

Verified fourteen connected feature domains with HTTP, database,
authorization, async projection, idempotency, concurrency and scheduler
evidence while retaining the distributed rollback path.

Merge: `b8a1a06d040e0dbbb451f3b2caf5c3912be6a10d`.

## M9/M10 cutover implementation

Implemented on branch `agent/convert-to-modular-monolith` and draft PR `#73`.

### M9 — Infrastructure cutover

- `mypet-application` now packages transitive runtime dependencies from every
  bounded-context project.
- Conditional runtime activation scans controllers, services, repositories,
  entities and configuration properties into one Spring context.
- Legacy service boot classes, remote HTTP fallback configurations,
  gateway-trust filters, duplicate outbox pollers and duplicate ShedLock
  providers are excluded.
- One central outbox publisher and one JDBC lock provider are registered.
- `infra/docker-compose.monolith.yml` deploys one backend process on port 8080
  with PostgreSQL, Redis and Kafka infrastructure.
- Embedded edge security replaces the separate API gateway.

### M10 — Legacy deployment removal from the primary topology

- The primary monolith topology contains no `api-gateway` or `*-service`
  containers.
- Prometheus scrapes only `mypet-application`.
- The distributed Compose topology remains available strictly as a temporary
  rollback artifact.
- M10 actuator metadata authorizes cutover and reports that distributed
  rollback is available but no longer required for normal operation.

## Required validation before merge

1. Java and mobile CI passes.
2. Static production and monolith Compose gates pass.
3. Clean-volume modular-monolith boot passes.
4. Actuator reports twelve active in-process modules, embedded edge security,
   one database owner and scheduler role `ALL`.
5. All twelve Flyway history tables remain present and successful.
6. A real authenticated business API flow succeeds through port 8080.
7. The complete distributed rollback stack remains green independently.

Detailed deployment and rollback commands are in
`docs/implementation/m10-modular-monolith-cutover.md`.
