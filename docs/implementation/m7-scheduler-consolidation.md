# M7 Scheduler Consolidation

## Objective

Make scheduled-work ownership explicit and horizontally scalable without changing business APIs, database schemas, job cadences, or the distributed rollback deployment.

## Inventory

M7 inventories fourteen jobs owned by eight modules:

| Owner | Jobs |
| --- | --- |
| order | outbox publish, compensation retry, delivered-order completion |
| appointment | outbox publish, expired-hold cleanup |
| dispatch | outbox publish, offer-timeout processing |
| provider | outbox publish |
| review | outbox publish |
| payment | outbox publish, weekly payout calculation |
| notification | due-reminder dispatch, vaccination-reminder synchronization |
| content | expired banner-auction closure |

The catalog records each component, method, cadence, schema-qualified lock table and lock name. Six outbox jobs share the same implementation but retain separate schema-owned lock tables.

## Runtime roles

`mypet.scheduling.role` supports four values:

- `ALL`: legacy default. Request handling and scheduled work run in the same service process.
- `API`: request handling remains active, but Spring's scheduled annotation processor is removed before singleton creation.
- `WORKER`: scheduled work executes normally. Worker profiles bind service and management ports to random unexposed ports by default.
- `DISABLED`: no scheduled execution; intended for diagnostics and rollback isolation.

Existing deployments that do not set this property remain in `ALL`, preserving M0-M6 behavior.

## Shared scheduler infrastructure

`common` now owns:

- runtime-role parsing;
- the scheduler registration gate;
- the immutable fourteen-job catalog;
- schema-qualified lock-table validation;
- the sole `JdbcTemplateLockProvider` construction path;
- database-time locking through `usingDbTime()`.

Each module retains only a thin declaration of its existing lock table. No lock table is moved, renamed, recreated or shared across schemas.

## Lock guarantees

Every `@Scheduled` method has an explicit `@SchedulerLock`. M7 adds the missing lock for appointment hold expiration.

Lock identities are unique as `schema.table/lock-name`, including outbox jobs whose method-level lock name is intentionally shared across module-specific lock tables.

## API/worker split

The scheduler-owning modules provide:

- `application-api.yml` with role `API`;
- `application-worker.yml` with role `WORKER` and random ports.

The optional `infra/docker-compose.m7.yml` overlay changes the eight request-serving containers to API ownership. `scripts/start-m7-workers.sh` launches unexposed worker containers from the same service images and configuration. `scripts/stop-m7-workers.sh` removes them.

Example:

```bash
export GATEWAY_SECRET='...'
export INTERNAL_API_SECRET='...'

docker compose \
  -f infra/docker-compose.yml \
  -f infra/docker-compose.replicas.yml \
  -f infra/docker-compose.m7.yml \
  up -d --build

bash scripts/start-m7-workers.sh
```

## Rollout

1. Keep the current distributed deployment in the default `ALL` mode.
2. Validate the M7 catalog and shared lock factory in CI.
3. Start one worker process per scheduler-owning module.
4. Verify lock acquisition and job progress.
5. Apply the M7 overlay so API containers switch to `API` mode.
6. Scale worker containers independently where throughput requires it.
7. Retain ShedLock coordination when running multiple worker replicas.

## Rollback

1. Remove or stop the dedicated worker containers.
2. Remove the M7 Compose overlay or set `MYPET_SCHEDULING_ROLE=ALL` on the original services.
3. Restart the original service containers.

Rollback does not require a migration because M7 preserves all existing tables, job payloads, cadences and lock records.

## Safety constraints

- API mode removes only periodic registration; business beans and controllers remain available.
- Worker mode uses the same tested service artifact and database credentials.
- No fixed host port is published for worker containers.
- No scheduler is moved to a module that does not own its data.
- No public route, Kafka topic, outbox contract, mobile contract or Compose base service is removed.
- Existing lock tables remain the source of truth during mixed `ALL`/`WORKER` rollout.

## Exit criteria

- the catalog contains fourteen jobs and eight owners;
- nine scheduled method declarations have nine explicit scheduler locks;
- all eight modules delegate lock-provider construction to `common`;
- API-role tests prove scheduler registration is removed;
- worker-role tests prove scheduler registration remains;
- API and worker profiles exist for every owner;
- optional Compose and launcher configuration covers all owners;
- complete backend, generated-artifact and production-hardening gates pass;
- both mobile applications remain green;
- clean-volume Full Stack Smoke passes in legacy `ALL` mode.

M8 must not begin until the M7 pull request is merged.
