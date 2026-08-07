#!/usr/bin/env python3
"""Static completeness gate for the ten Phase 2B connected journeys."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manifest_path = ROOT / "qa/p2b-connected-journeys.json"
manifest_text = manifest_path.read_text(encoding="utf-8")
manifest = json.loads(manifest_text)
journeys = manifest.get("journeys", [])

assert manifest.get("schemaVersion") == 1
assert manifest.get("release") == "v0.9.0-beta.1"
assert len(journeys) == 10
assert [item["id"] for item in journeys] == [f"J{number:02d}" for number in range(1, 11)]

required_dimensions = {"http", "database", "events", "notifications", "uiContracts", "idempotency"}
for journey in journeys:
    assert journey.get("title")
    assert journey.get("domains")
    assert required_dimensions.issubset(journey)
    for dimension in required_dimensions:
        assert isinstance(journey[dimension], list)

for event in ("CustomerCaseCreated", "CustomerCaseUpdated", "PaymentCaptured", "DeliveryVerified"):
    assert event in manifest_text, event

runner = (ROOT / "scripts/run-p2b-connected-e2e.py").read_text(encoding="utf-8")
entrypoint = (ROOT / "scripts/run-p2b-connected-e2e-entry.py").read_text(encoding="utf-8")
recurring = (ROOT / "scripts/test-recurring-order-scheduler-e2e.py").read_text(encoding="utf-8")
test_all = (ROOT / "scripts/test-all.sh").read_text(encoding="utf-8")
monolith_certification = (ROOT / "scripts/test-monolith-release-certification.sh").read_text(encoding="utf-8")
monolith_stack = (ROOT / "scripts/test-monolith-stack.sh").read_text(encoding="utf-8")
recurring_scheduler = (
    ROOT
    / "backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/RecurringOrderScheduler.kt"
).read_text(encoding="utf-8")
scheduler_runtime = (
    ROOT / "backend/common/src/main/kotlin/com/pawsnearme/common/scheduling/SchedulerRuntime.kt"
).read_text(encoding="utf-8")
scheduler_executors = (
    ROOT
    / "backend/common/src/main/kotlin/com/pawsnearme/common/scheduling/SchedulerExecutorsConfiguration.kt"
).read_text(encoding="utf-8")
outbox_poller = (
    ROOT / "backend/common/src/main/kotlin/com/pawsnearme/common/outbox/OutboxPoller.kt"
).read_text(encoding="utf-8")
monolith_compose = (ROOT / "infra/docker-compose.monolith.yml").read_text(encoding="utf-8")
for token in (
    "verify_m8_report",
    "verify_persisted_graph",
    "verify_new_connected_flows",
    "verify_ui_contracts",
    "medical_document_access_logs",
    "recurring_order_subscriptions",
    "customer-cases",
):
    assert token in runner, token

for token in (
    "sys.modules[spec.name]",
    "schema_compatible_sql",
    "compatible_verify_m8_report",
    "required feature domains",
):
    assert token in entrypoint, token

for token in (
    "AWAITING_CONFIRMATION",
    "RecurringOrderConfirmationRequired",
    "automaticCharge",
    "payments.transactions",
    "silently created an order",
    "second scheduler cycle",
    "MYPET_COMPOSE_FILES",
    "MYPET_SCHEDULER_SERVICE",
    "scheduler_lock_state",
    "scheduler_logs",
    "lock_until=now()-interval '1 second'",
):
    assert token in recurring, token

assert 'python3 "$ROOT/scripts/run-p2b-connected-e2e-entry.py"' in test_all
assert 'python3 "$ROOT/scripts/test-recurring-order-scheduler-e2e.py"' in monolith_certification
assert 'ORDER_RECURRING_REMINDER_CRON="*/5 * * * * *"' in monolith_certification
assert "ORDER_RECURRING_REMINDER_LOCK_AT_MOST_FOR=PT30S" in monolith_certification
assert "ORDER_RECURRING_REMINDER_LOCK_AT_LEAST_FOR=PT0S" in monolith_certification
assert "MYPET_COMPOSE_FILES=" in monolith_certification
assert 'MYPET_SCHEDULER_SERVICE="mypet-application"' in monolith_certification
assert 'ENV_FILE="${MYPET_ENV_FILE:-}"' in monolith_stack
assert 'OWNS_ENV_FILE="false"' in monolith_stack
assert 'if [[ "$OWNS_ENV_FILE" == "true" ]]' in monolith_stack
assert "@WorkerScheduler" in recurring_scheduler
assert "order.recurring-reminder-lock-at-most-for:PT55M" in recurring_scheduler
assert "order.recurring-reminder-lock-at-least-for:PT1M" in recurring_scheduler
assert "order.recurring-confirmation-reminders" in scheduler_runtime
assert "recurringOrderConfirmationReminder" in scheduler_runtime
assert 'Bean(name = ["taskScheduler"])' in scheduler_executors
assert 'Bean(name = ["outboxTaskScheduler"])' in scheduler_executors
assert "mypet.scheduling.pool-size:8" in scheduler_executors
assert "mypet.scheduling.outbox-pool-size:2" in scheduler_executors
assert 'scheduler = "outboxTaskScheduler"' in outbox_poller
assert "ORDER_RECURRING_REMINDER_LOCK_AT_MOST_FOR" in monolith_compose
assert "ORDER_RECURRING_REMINDER_LOCK_AT_LEAST_FOR" in monolith_compose

print("P2B_CONNECTED_E2E_CONTRACT_OK journeys=10 dimensions=6 recurring_scheduler=1 outbox_scheduler=isolated")
