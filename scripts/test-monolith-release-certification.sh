#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-mypet-monolith-release-e2e}"
REPORT="${MYPET_MONOLITH_REPORT:-$ROOT/build/reports/monolith-release-certification.md}"
ENV_FILE="$(mktemp)"

cat > "$ENV_FILE" <<'ENVEOF'
SPRING_PROFILES_ACTIVE=local
GATEWAY_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
INTERNAL_API_SECRET=abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789
BANK_DATA_ENCRYPTION_KEY=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=
MEDICAL_REPORTS_BUCKET=mypet-local-medical-reports
MEDICAL_REPORTS_REGION=ap-south-1
MEDICAL_REPORTS_ACCESS_KEY=local-test-access-key
MEDICAL_REPORTS_SECRET_KEY=local-test-secret-key
MEDICAL_DOCUMENT_SIGNING_KEY=0123456789abcdef0123456789abcdef
CASE_EVIDENCE_SIGNING_KEY=abcdef0123456789abcdef0123456789
PAYMENT_CHECKOUT_TOKEN_SECRET=0123456789abcdef0123456789abcdef
CASHFREE_CLIENT_ID=ci-interpolation-only-client-id
CASHFREE_CLIENT_SECRET=ci-interpolation-only-client-secret
CASHFREE_WEBHOOK_SECRET=local-cashfree-webhook-secret
CASHFREE_SANDBOX_MODE=true
RAZORPAY_WEBHOOK_SECRET=local-legacy-webhook-secret
ALLOW_UNSIGNED_JWT=true
NOTIFICATION_DELIVERY_MODE=LOGGED_DEV
ORDER_RECURRING_REMINDER_CRON="*/5 * * * * *"
ORDER_RECURRING_REMINDER_LOCK_AT_MOST_FOR=PT30S
ORDER_RECURRING_REMINDER_LOCK_AT_LEAST_FOR=PT0S
ENVEOF

COMPOSE=(
  docker compose
  -p "$PROJECT_NAME"
  --env-file "$ENV_FILE"
  -f "$ROOT/infra/docker-compose.yml"
  -f "$ROOT/infra/docker-compose.monolith.yml"
)

cleanup() {
  if [[ "${KEEP_STACK:-0}" != "1" ]]; then
    "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
  fi
  rm -f "$ENV_FILE"
}
trap cleanup EXIT

export COMPOSE_PROJECT_NAME="$PROJECT_NAME"
export MYPET_ENV_FILE="$ENV_FILE"
export MYPET_GATEWAY_URL="http://localhost:8080"
export MYPET_SMOKE_REPORT="$REPORT"
export MYPET_MONOLITH_REPORT="$REPORT"

# The base smoke owns stack startup. KEEP_STACK keeps the clean-volume monolith
# alive so the exact barcode, M8 and P2B runners execute against that process.
KEEP_STACK=1 bash "$ROOT/scripts/test-monolith-stack.sh"

cat >> "$REPORT" <<'EOF_REPORT'

## Modular-monolith connected certification
EOF_REPORT

bash "$ROOT/scripts/test-barcode-e2e.sh"
python3 "$ROOT/scripts/run-m8-feature-matrix.py"
python3 "$ROOT/scripts/run-p2b-connected-e2e-entry.py"
python3 "$ROOT/scripts/test-recurring-order-scheduler-e2e.py"
bash "$ROOT/scripts/test-database-backup-restore.sh"

cat >> "$REPORT" <<'EOF_REPORT'

## Release-certification result

**PASS** — the primary modular-monolith topology completed the barcode inventory
flow, the connected fourteen-domain M8 matrix, all ten P2B customer → merchant
→ captain → admin journeys, deterministic recurring-order due processing, and
an isolated PostgreSQL dump/restore comparison. The evidence covers public HTTP
contracts, authorization, database persistence, Kafka/outbox behavior,
notifications/UI contracts, concurrency, idempotency, private-document access,
confirmation-only recurring orders, and disaster recovery.
EOF_REPORT

echo "Monolith release certification report: $REPORT"
