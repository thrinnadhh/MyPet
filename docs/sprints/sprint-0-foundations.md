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

- `python infra/run_migrations.py`
- `./gradlew test` from `backend/`
- `npx tsc --noEmit` from each mobile app
