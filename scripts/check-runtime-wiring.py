#!/usr/bin/env python3
"""Fail when critical shared runtime infrastructure is wired incorrectly."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

SHEDLOCK_SCHEMAS = {
    "appointment-service": "appointments",
    "content-service": "content",
    "dispatch-service": "dispatch",
    "notification-service": "notifications",
    "order-service": "orders",
    "payment-service": "payments",
    "provider-service": "providers",
    "review-service": "reviews",
}

failures: list[str] = []

for service, schema in SHEDLOCK_SCHEMAS.items():
    path = (
        ROOT
        / "backend"
        / service
        / "src/main/kotlin/com/pawsnearme"
        / service.replace("-", "")
        / "config/ShedLockConfig.kt"
    )
    if not path.is_file():
        failures.append(f"missing ShedLock configuration: {path.relative_to(ROOT)}")
        continue

    content = path.read_text(encoding="utf-8")
    expected = f'.withTableName("{schema}.shedlock")'
    if expected not in content:
        failures.append(
            f"{service} must use the schema-qualified lock table {schema}.shedlock"
        )

payment_application = (
    ROOT
    / "backend/payment-service/src/main/kotlin/com/pawsnearme/paymentservice/PaymentServiceApplication.kt"
)
payment_outbox_config = (
    ROOT
    / "backend/payment-service/src/main/kotlin/com/pawsnearme/paymentservice/config/OutboxConfig.kt"
)
payment_outbox_migration = (
    ROOT
    / "backend/payment-service/src/main/resources/db/migration/V3__outbox.sql"
)

if not payment_application.is_file():
    failures.append("missing PaymentServiceApplication.kt")
else:
    content = payment_application.read_text(encoding="utf-8")
    if "@EntityScan" not in content or "@EnableJpaRepositories" not in content:
        failures.append("payment-service must scan shared Outbox entities and repositories")
    if content.count('"com.pawsnearme.common.outbox"') < 3:
        failures.append(
            "payment-service must include common.outbox in entity, repository, and component scans"
        )

if not payment_outbox_config.is_file():
    failures.append("payment-service is missing OutboxConfig.kt")
else:
    content = payment_outbox_config.read_text(encoding="utf-8")
    for required in ("OutboxPoller", "OutboxRepository", "KafkaTemplate"):
        if required not in content:
            failures.append(f"payment OutboxConfig.kt is missing {required}")

if not payment_outbox_migration.is_file():
    failures.append("payment-service is missing V3__outbox.sql")

if failures:
    for failure in failures:
        print(f"ERROR: {failure}", file=sys.stderr)
    raise SystemExit(1)

print(
    "Runtime wiring checks passed for Payment Outbox and "
    f"{len(SHEDLOCK_SCHEMAS)} schema-qualified ShedLock providers."
)
