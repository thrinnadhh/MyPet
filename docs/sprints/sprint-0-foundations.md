# Sprint 0: Foundations

## Goal

Make local development, CI, database, Kafka, Redis, and app shells repeatable before feature work continues.

## Acceptance Checklist

- [ ] Supabase project exists with `pgcrypto` and `postgis` enabled.
- [ ] SQL files in `imp files/` run successfully against staging in the documented order.
- [ ] Local infra starts from `infra/docker-compose.yml`.
- [ ] Kafka topics exist: `orders.events`, `appointments.events`, `payments.events`, `providers.events`, `dispatch.events`.
- [ ] Redis is available for geo lookups, slot locks, dispatch state, and billing barcode cache.
- [ ] GitHub Actions builds backend and both mobile apps on PR.
- [ ] Generated artifacts are not committed: `node_modules`, `.expo`, `.gradle`, `build`, `bin`.

## Verification

- `python infra/run_migrations.py --reset-local-development-database`
- `./gradlew test` from `backend/`
- `npx tsc --noEmit` from each mobile app

## Local Infra Proof Notes

- Passed on 2026-07-03 IST: `.venv/bin/python backend/verify_sprint0.py` with `6 passed, 0 failed`.
- Clean infra proof still requires a fresh local Postgres/Kafka/Redis run from `infra/docker-compose.yml`.
- Kafka topic evidence to record during that run: `orders.events`, `appointments.events`, `payments.events`, `providers.events`, and `dispatch.events`.
- Redis evidence to record during that run: `PING` response plus a short-lived key write/read/delete.
- If the existing local provider database reports Flyway checksum drift for provider migration V2, treat it as local schema-history drift. Repair/reset only the local database after backing up needed data; do not edit already-applied migrations to match local history.

## Local-Only Flyway Drift Recovery

1. Prefer a fresh local database for release proof.
2. If keeping the existing local database, inspect the `flyway_schema_history` row for the provider service V2 migration.
3. If the schema matches the current migration and only the checksum drifted locally, run Flyway repair for the local provider database.
4. If the schema does not match, reset the local provider database and re-run migrations from the repo.
