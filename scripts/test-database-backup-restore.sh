#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-mypet-monolith-release-e2e}"
ENV_FILE="${MYPET_ENV_FILE:?MYPET_ENV_FILE must point to the active stack environment file}"
REPORT="${MYPET_SMOKE_REPORT:-$ROOT/build/reports/monolith-release-certification.md}"
EVIDENCE_DIR="$ROOT/build/reports/database-backup-restore"
BACKUP_FILE="$EVIDENCE_DIR/pawsnearme.dump"
GLOBALS_FILE="$EVIDENCE_DIR/postgres-globals.sql"
EVIDENCE_FILE="$EVIDENCE_DIR/evidence.json"
RESTORE_DB="pawsnearme_restore_${GITHUB_RUN_ID:-local}_$$"
mkdir -p "$EVIDENCE_DIR"

COMPOSE=(
  docker compose
  -p "$PROJECT_NAME"
  --env-file "$ENV_FILE"
  -f "$ROOT/infra/docker-compose.yml"
  -f "$ROOT/infra/docker-compose.monolith.yml"
)

pass() {
  printf '%s\n' "- ✅ $*" | tee -a "$REPORT"
}

cleanup() {
  "${COMPOSE[@]}" exec -T postgres psql -U postgres -d postgres \
    -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS \"$RESTORE_DB\" WITH (FORCE);" \
    >/dev/null 2>&1 || true
  rm -f "$BACKUP_FILE" "$GLOBALS_FILE"
}
trap cleanup EXIT

snapshot_sql="
SELECT jsonb_build_object(
  'profiles', (SELECT count(*) FROM identity.profiles),
  'providers', (SELECT count(*) FROM providers.providers),
  'offerings', (SELECT count(*) FROM catalog.offerings),
  'orders', (SELECT count(*) FROM orders.orders),
  'appointments', (SELECT count(*) FROM appointments.appointments),
  'dispatch_jobs', (SELECT count(*) FROM dispatch.dispatch_jobs),
  'transactions', (SELECT count(*) FROM payments.transactions),
  'reviews', (SELECT count(*) FROM reviews.reviews),
  'notifications', (SELECT count(*) FROM notifications.notifications),
  'conversations', (SELECT count(*) FROM chat.conversations),
  'content_banners', (SELECT count(*) FROM content.promo_banners),
  'subscriptions', (SELECT count(*) FROM orders.recurring_order_subscriptions),
  'customer_cases', (SELECT count(*) FROM orders.customer_cases),
  'medical_documents', (SELECT count(*) FROM appointments.medical_documents),
  'published_outbox_events', (
    SELECT
      (SELECT count(*) FROM orders.outbox_events WHERE published_at IS NOT NULL) +
      (SELECT count(*) FROM appointments.outbox_events WHERE published_at IS NOT NULL) +
      (SELECT count(*) FROM payments.outbox_events WHERE published_at IS NOT NULL)
  )
)::text;
"

source_snapshot="$("${COMPOSE[@]}" exec -T postgres psql -U postgres -d pawsnearme -At \
  -v ON_ERROR_STOP=1 -c "$snapshot_sql")"
[[ -n "$source_snapshot" ]] || { echo "Source database snapshot is empty" >&2; exit 1; }

"${COMPOSE[@]}" exec -T postgres pg_dump -U postgres -d pawsnearme -Fc > "$BACKUP_FILE"
"${COMPOSE[@]}" exec -T postgres pg_dumpall -U postgres --globals-only > "$GLOBALS_FILE"
[[ -s "$BACKUP_FILE" ]] || { echo "Database dump was not created" >&2; exit 1; }
[[ -s "$GLOBALS_FILE" ]] || { echo "Global-role dump was not created" >&2; exit 1; }
pass "PostgreSQL database and global-role backups were created"

"${COMPOSE[@]}" exec -T postgres pg_restore --list < "$BACKUP_FILE" >/dev/null
pass "The custom-format backup catalog is readable"

"${COMPOSE[@]}" exec -T postgres createdb -U postgres "$RESTORE_DB"
"${COMPOSE[@]}" exec -T postgres pg_restore -U postgres \
  --no-owner --no-privileges --exit-on-error -d "$RESTORE_DB" < "$BACKUP_FILE"
pass "The backup restored into an isolated database without errors"

restore_snapshot="$("${COMPOSE[@]}" exec -T postgres psql -U postgres -d "$RESTORE_DB" -At \
  -v ON_ERROR_STOP=1 -c "$snapshot_sql")"
[[ "$source_snapshot" == "$restore_snapshot" ]] || {
  printf 'Source snapshot: %s\nRestore snapshot: %s\n' "$source_snapshot" "$restore_snapshot" >&2
  exit 1
}
pass "Restored business-table and published-outbox counts match the source"

schema_count="$("${COMPOSE[@]}" exec -T postgres psql -U postgres -d "$RESTORE_DB" -Atc "
SELECT count(*) FROM information_schema.schemata
WHERE schema_name IN (
  'auth','identity','providers','catalog','orders','appointments','dispatch',
  'captains','payments','reviews','notifications','chat','content'
);")"
[[ "$schema_count" == "13" ]] || { echo "Expected 13 restored schemas, found $schema_count" >&2; exit 1; }

history_count="$("${COMPOSE[@]}" exec -T postgres psql -U postgres -d "$RESTORE_DB" -Atc "
SELECT count(*) FROM (VALUES
  (to_regclass('providers.flyway_schema_history_provider')),
  (to_regclass('catalog.flyway_schema_history_catalog')),
  (to_regclass('providers.flyway_schema_history_discovery')),
  (to_regclass('orders.flyway_schema_history_order')),
  (to_regclass('appointments.flyway_schema_history_appointment')),
  (to_regclass('dispatch.flyway_schema_history_dispatch')),
  (to_regclass('captains.flyway_schema_history_captain')),
  (to_regclass('notifications.flyway_schema_history_notification')),
  (to_regclass('reviews.flyway_schema_history_review')),
  (to_regclass('payments.flyway_schema_history_payment')),
  (to_regclass('chat.flyway_schema_history_chat')),
  (to_regclass('content.flyway_schema_history_content'))
) AS history(table_name) WHERE table_name IS NOT NULL;")"
[[ "$history_count" == "12" ]] || { echo "Expected 12 restored Flyway histories, found $history_count" >&2; exit 1; }
pass "All application schemas and twelve Flyway histories survived restore"

backup_sha="$(sha256sum "$BACKUP_FILE" | awk '{print $1}')"
globals_sha="$(sha256sum "$GLOBALS_FILE" | awk '{print $1}')"
backup_size="$(wc -c < "$BACKUP_FILE" | tr -d ' ')"
globals_size="$(wc -c < "$GLOBALS_FILE" | tr -d ' ')"

SOURCE_SNAPSHOT="$source_snapshot" RESTORE_SNAPSHOT="$restore_snapshot" \
BACKUP_SHA="$backup_sha" GLOBALS_SHA="$globals_sha" \
BACKUP_SIZE="$backup_size" GLOBALS_SIZE="$globals_size" \
python3 - "$EVIDENCE_FILE" <<'PY'
import json
import os
import sys
from datetime import datetime, timezone

path = sys.argv[1]
payload = {
    "status": "PASS",
    "verifiedAt": datetime.now(timezone.utc).isoformat(),
    "sourceDatabase": "pawsnearme",
    "restoreDatabase": "isolated-temporary-database",
    "sourceSnapshot": json.loads(os.environ["SOURCE_SNAPSHOT"]),
    "restoreSnapshot": json.loads(os.environ["RESTORE_SNAPSHOT"]),
    "databaseBackup": {
        "sha256": os.environ["BACKUP_SHA"],
        "bytes": int(os.environ["BACKUP_SIZE"]),
        "format": "pg_dump-custom",
    },
    "globalRolesBackup": {
        "sha256": os.environ["GLOBALS_SHA"],
        "bytes": int(os.environ["GLOBALS_SIZE"]),
        "format": "pg_dumpall-globals-sql",
    },
    "restoredSchemaCount": 13,
    "restoredFlywayHistoryCount": 12,
}
with open(path, "w", encoding="utf-8") as handle:
    json.dump(payload, handle, indent=2, sort_keys=True)
    handle.write("\n")
PY
pass "Machine-readable backup/restore evidence was recorded without retaining database contents"
