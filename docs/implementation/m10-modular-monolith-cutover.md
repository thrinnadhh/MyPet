# M10 — MyPet Modular-Monolith Cutover

## Target architecture

MyPet now deploys all backend business capabilities in one Spring Boot process:

```text
Customer / Merchant-Captain apps
              |
              v
      mypet-application :8080
        |       |       |
        v       v       v
   PostgreSQL  Redis   Kafka
```

`mypet-application` contains the provider, catalog, discovery, order,
appointment, dispatch, captain, notification, review, payment, chat and
content bounded contexts. Public `/api/v1/**` contracts remain unchanged.
Internal synchronous collaboration uses typed in-process module interfaces.
Kafka is retained for durable outbox work and asynchronous projections; it is
infrastructure and not an independently deployed business service.

## Production start

Create an environment file containing real JWT, Cashfree, encryption, object
storage and signing secrets. Then render and start only the monolith topology:

```bash
docker compose \
  --env-file .env.production \
  -f infra/docker-compose.yml \
  -f infra/docker-compose.monolith.yml \
  config

docker compose \
  --env-file .env.production \
  -f infra/docker-compose.yml \
  -f infra/docker-compose.monolith.yml \
  up -d --build
```

Do not combine `docker-compose.monolith.yml` and
`docker-compose.replicas.yml`. They represent mutually exclusive backend
runtimes.

## Required production settings

- `ALLOW_UNSIGNED_JWT=false`
- Configure either `SUPABASE_JWT_SECRET` or `SUPABASE_JWT_JWK_SET_URI`.
- `CASHFREE_SANDBOX_MODE=false`
- Configure Cashfree client and webhook credentials.
- Configure `BANK_DATA_ENCRYPTION_KEY`.
- Configure medical-report object-storage credentials.
- Configure medical-document, case-evidence and checkout signing keys.
- Set public CORS origins explicitly.
- Keep `MYPET_MODULES_ENABLED=true`, `MYPET_EDGE_ENABLED=true`,
  `MYPET_DATABASE_ENABLED=true` and `MYPET_SCHEDULING_ROLE=ALL`.

## Runtime invariants

The cutover Compose contract enforces:

1. Exactly one backend deployment unit: `mypet-application`.
2. No `api-gateway` or `*-service` business containers.
3. One application-owned Hikari pool.
4. One shared outbox publisher.
5. One JDBC ShedLock provider using `orders.shedlock`.
6. All twelve existing schema-specific Flyway history tables retained.
7. Public API remains on host port `8080`.
8. Mobile applications require no API-base change.

## Verification

Static topology check:

```bash
bash scripts/check-monolith-compose.sh
```

Clean-volume live boot and business-path check:

```bash
bash scripts/test-monolith-stack.sh
```

The live check builds `mypet-application`, starts a clean PostgreSQL/Redis/Kafka
stack, waits for readiness, verifies M10 actuator metadata, confirms all twelve
migration histories, confirms only one backend deployment and executes an
authenticated loyalty API request through the embedded edge.

## Rollback

The distributed runtime remains available as a temporary rollback artifact:

```bash
docker compose \
  --env-file .env.production \
  -f infra/docker-compose.yml \
  -f infra/docker-compose.replicas.yml \
  up -d --build
```

Before rollback, stop the monolith topology to prevent duplicate scheduler and
consumer ownership:

```bash
docker compose \
  --env-file .env.production \
  -f infra/docker-compose.yml \
  -f infra/docker-compose.monolith.yml \
  down
```

The rollback stack uses the same schemas, migration histories, topics and API
contracts. Do not run both backend topologies against the same production
database simultaneously.

## Post-cutover observation

For the initial observation window, watch:

- readiness and liveness;
- Hikari pool saturation and connection latency;
- Kafka consumer lag and outbox retry volume;
- scheduled-job lock acquisition failures;
- payment webhook verification and reconciliation;
- request latency/error rate by API domain;
- JVM heap, GC pauses and thread counts.

After the observation window and rollback drill succeed, the legacy service
projects may remain as source modules, but their independent Docker deployment
wiring can be removed permanently.
