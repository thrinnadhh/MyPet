# Sprint 8 Hardening Runbook

## Scope

This runbook covers Sprint 8 operations for admin, billing, load smoke, observability, backup, and rollback.

## Load Smoke

Run after local services or the gateway are up:

```bash
python3 scripts/load-smoke-sprint8.py --base-url http://localhost:8080 --requests 25 --concurrency 8
```

For direct service testing without the gateway:

```bash
DISCOVERY_BASE_URL=http://localhost:8083 \
ORDER_BASE_URL=http://localhost:8084 \
APPOINTMENT_BASE_URL=http://localhost:8085 \
CATALOG_BASE_URL=http://localhost:8082 \
python3 scripts/load-smoke-sprint8.py --requests 25 --concurrency 8
```

The smoke covers:

- Discovery: nearby provider search.
- Orders: customer order list.
- Appointments: customer appointment list.
- Billing: admin store bill list.

Pass rule: every target returns 2xx, with p95 latency recorded for comparison.

## Prometheus Scrape Targets

Each Spring service should expose `/actuator/prometheus` before production launch. Scrape the gateway and domain services:

- `api-gateway:8080`
- `provider-service:8081`
- `catalog-service:8082`
- `discovery-service:8083`
- `order-service:8084`
- `appointment-service:8085`
- `dispatch-service:8086`
- `captain-service:8087`
- `notification-service:8088`
- `review-service:8089`
- `payment-service:8090`

Minimum metrics:

- Request rate: `sum(rate(http_server_requests_seconds_count[5m])) by (application, uri, method, status)`
- Error rate: `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (application)`
- Latency: `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application, uri))`
- Kafka producer failures: `kafka_producer_record_error_total` or equivalent client error counter.
- Redis latency/errors: Redis exporter command latency and rejected connections.
- Billing throughput: bill creation count and failure count from catalog-service request metrics on `/api/v1/catalog/bills`.

## Grafana Dashboard

Dashboard sections:

- Gateway traffic: request rate, 4xx/5xx, p95 latency by route.
- Admin operations: provider approve, commission update, disputes, support cases.
- Billing: bill create/list rate, stock decrement failures, idempotency conflicts.
- Kafka: producer send rate, send errors, consumer lag by group.
- Redis: command latency, memory, evictions, blocked clients.
- Database: connection pool usage, slow queries, lock wait time.

Alert defaults:

- 5xx error rate above 2 percent for 10 minutes.
- Gateway p95 latency above 1500 ms for 10 minutes.
- Billing create p95 latency above 2000 ms for 10 minutes.
- Kafka consumer lag growing for 15 minutes.
- Redis evictions greater than 0 in 10 minutes.
- Database pool usage above 85 percent for 10 minutes.

## Backup And DR

Supabase/Postgres:

- Take daily logical backups and retain at least 14 days before launch.
- Before migrations, capture a fresh backup and record schema versions.
- Restore drill: restore to a temporary database, run migrations, run `scripts/verify-all.sh --flows`.

Kafka:

- Record topic list and retention settings before release.
- For recovery, replay from committed offsets when consumers are idempotent.
- Admin/support events are audit events; keep retention long enough for dispute windows.

Redis:

- Enable persistence for production slot holds and dispatch state.
- Document TTL defaults for appointment holds and dispatch offers.
- Recovery behavior: expired holds/offers can be regenerated; confirmed DB rows remain the source of truth.

Service rollback order:

1. Stop mobile rollout or feature flag risky UI.
2. Roll back api-gateway routes/guards if auth routing breaks.
3. Roll back domain service binaries in dependency order: payment, order, catalog/provider, appointment/dispatch.
4. Roll back DB only when a backward-compatible code rollback cannot run against the new schema.
5. Re-run smoke: Sprint 8 verifier, load smoke, and the affected sprint flow proof.

## Sprint 8 Release Gate

- `./gradlew :order-service:test :provider-service:test`
- `npm run typecheck` and `npm run lint` in `apps/merchant-captain-app`
- `python3 backend/verify_sprint8.py`
- `python3 scripts/load-smoke-sprint8.py --requests 25 --concurrency 8`
- Live admin flow: approve provider, resolve dispute, update commission, create and resolve support case.
