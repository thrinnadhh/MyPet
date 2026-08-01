# M4 — Database Consolidation Execution Plan

## Goal

Run `mypet-application` against the existing PostgreSQL database through one connection pool while retaining every service-owned schema, migration file and Flyway history table.

## Safety boundary

M4 changes migration orchestration only.

It does not:

- activate business controllers, repositories or schedulers in the consolidated JVM;
- replace the API gateway;
- remove a service datasource or Flyway configuration;
- rename, delete, reorder or edit an existing migration;
- merge schemas or history tables;
- change public/mobile API contracts.

## Ownership matrix

| Owner | Schema | Flyway history table | Application location |
|---|---|---|---|
| provider | `providers` | `flyway_schema_history_provider` | `classpath:db/migration/provider` |
| catalog | `catalog` | `flyway_schema_history_catalog` | `classpath:db/migration/catalog` |
| discovery | `providers` | `flyway_schema_history_discovery` | `classpath:db/migration/discovery` |
| order | `orders` | `flyway_schema_history_order` | `classpath:db/migration/order` |
| appointment | `appointments` | `flyway_schema_history_appointment` | `classpath:db/migration/appointment` |
| dispatch | `dispatch` | `flyway_schema_history_dispatch` | `classpath:db/migration/dispatch` |
| captain | `captains` | `flyway_schema_history_captain` | `classpath:db/migration/captain` |
| notification | `notifications` | `flyway_schema_history_notification` | `classpath:db/migration/notification` |
| review | `reviews` | `flyway_schema_history_review` | `classpath:db/migration/review` |
| payment | `payments` | `flyway_schema_history_payment` | `classpath:db/migration/payment` |
| chat | `chat` | `flyway_schema_history_chat` | `classpath:db/migration/chat` |
| content | `content` | `flyway_schema_history_content` | `classpath:db/migration/content` |

The provider migration lineage remains responsible for its existing identity/provider bootstrap SQL. Discovery remains a separate migration owner even though its tables reside in `providers`.

## Implementation

1. Copy service migration resources into namespaced paths during `mypet-application:processResources`.
2. Add PostgreSQL, Hikari and Flyway runtime dependencies only to the consolidated application.
3. Disable Boot datasource/Flyway auto-configuration and activate M4 only through `MYPET_DATABASE_ENABLED=true`.
4. Build one Hikari pool from `MYPET_DB_*` settings.
5. Execute one Flyway instance per owner, sequentially, with:
   - original schema;
   - original history table;
   - `baselineOnMigrate=true` and baseline version `1`;
   - migration naming validation;
   - clean disabled;
   - out-of-order disabled;
   - schema creation disabled.
6. Fail application startup if any owner cannot migrate or validate.
7. Expose phase and owner status through actuator info and health.
8. Start an M4 shadow application in Compose while leaving all legacy services active.

## Validation

- Unit/architecture tests verify the exact ownership matrix.
- Every namespaced location must contain well-formed SQL migrations.
- Disabled-mode tests prove the shell still starts without PostgreSQL.
- Clean-volume Docker validation proves:
  - all thirteen application schemas remain;
  - all twelve history tables exist;
  - no history table contains a failed migration;
  - the consolidated application reaches `READY` with twelve owners;
  - all distributed services and gateway flows remain operational.

## Rollback

1. Stop or remove the `docker-compose.m4.yml` overlay.
2. Set `MYPET_DATABASE_ENABLED=false` for the consolidated application.
3. Continue deploying the existing services, which retain their original datasource, migration resources and Flyway history tables.
4. Do not delete any M4-created history entry; the entries are in the same legacy histories and remain valid for service-owned Flyway.

## Exit gate

M4 is complete only after its PR is merged with backend/mobile CI and clean-volume Full Stack Smoke green. M5 production changes must start from the M4 merge commit.
