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

COMPOSE_SEARCH_PATHS = {
    "provider-service": "providers,identity,public",
    "catalog-service": "catalog,providers,identity,public",
    "discovery-service": "providers,catalog,customer,public",
    "order-service": "orders,providers,catalog,public",
    "appointment-service": "appointments,catalog,providers,public",
    "dispatch-service": "dispatch,orders,providers,public",
    "captain-service": "captains,identity,public",
    "notification-service": "notifications,appointments,catalog,public",
    "review-service": "reviews,public",
    "payment-service": "payments,orders,appointments,captains,providers,public",
    "chat-service": "chat,identity,providers,public",
    "content-service": "content,public",
}

failures: list[str] = []

for service, schema in SHEDLOCK_SCHEMAS.items():
    kotlin_path = (
        ROOT
        / "backend"
        / service
        / "src/main/kotlin/com/pawsnearme"
        / service.replace("-", "")
        / "config/ShedLockConfig.kt"
    )
    if not kotlin_path.is_file():
        failures.append(
            f"missing ShedLock configuration: {kotlin_path.relative_to(ROOT)}"
        )
    else:
        content = kotlin_path.read_text(encoding="utf-8")
        expected = f'.withTableName("{schema}.shedlock")'
        if expected not in content:
            failures.append(
                f"{service} must use the schema-qualified lock table {schema}.shedlock"
            )

    migration_path = (
        ROOT
        / "backend"
        / service
        / "src/main/resources/db/migration/R__ensure_schema_shedlock.sql"
    )
    if not migration_path.is_file():
        failures.append(
            f"missing repeatable ShedLock migration: {migration_path.relative_to(ROOT)}"
        )
    else:
        migration = migration_path.read_text(encoding="utf-8")
        if f"CREATE TABLE IF NOT EXISTS {schema}.shedlock" not in migration:
            failures.append(
                f"{service} repeatable migration must create {schema}.shedlock"
            )

order_versioned_repair = (
    ROOT
    / "backend/order-service/src/main/resources/db/migration/V9__ensure_orders_shedlock_schema.sql"
)
if not order_versioned_repair.is_file():
    failures.append("order-service is missing its versioned ShedLock schema repair")
else:
    repair = order_versioned_repair.read_text(encoding="utf-8")
    if "CREATE TABLE IF NOT EXISTS orders.shedlock" not in repair:
        failures.append("order-service V9 must create orders.shedlock")

compose_path = ROOT / "infra/docker-compose.replicas.yml"
if not compose_path.is_file():
    failures.append("missing infra/docker-compose.replicas.yml")
else:
    compose = compose_path.read_text(encoding="utf-8")
    for service, search_path in COMPOSE_SEARCH_PATHS.items():
        expected = (
            "DB_URL: jdbc:postgresql://postgres:5432/pawsnearme?"
            f"currentSchema={search_path}&stringtype=unspecified"
        )
        if expected not in compose:
            failures.append(
                f"{service} Compose DB_URL must preserve currentSchema={search_path}"
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
        failures.append(
            "payment-service must scan shared persistence entities and repositories"
        )
    for package in (
        "com.pawsnearme.common.idempotency",
        "com.pawsnearme.common.outbox",
    ):
        if content.count(f'"{package}"') < 3:
            failures.append(
                f"payment-service must include {package} in entity, repository, and component scans"
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

gateway_filter = (
    ROOT
    / "backend/api-gateway/src/main/kotlin/com/pawsnearme/apigateway/filter/GatewayTrustHeaderFilter.kt"
)
gateway_filter_test = (
    ROOT
    / "backend/api-gateway/src/test/kotlin/com/pawsnearme/apigateway/filter/GatewayTrustHeaderFilterTests.kt"
)

if not gateway_filter.is_file():
    failures.append("api-gateway is missing GatewayTrustHeaderFilter.kt")
else:
    content = gateway_filter.read_text(encoding="utf-8")
    for required in (
        'const val SECRET_HEADER = "X-Internal-Gateway-Secret"',
        "headers.set(SECRET_HEADER, trustSecret)",
        "TRUST_INJECTION_ORDER = 10_000",
        "require(trustSecret.isNotBlank())",
    ):
        if required not in content:
            failures.append(f"GatewayTrustHeaderFilter.kt is missing: {required}")

if not gateway_filter_test.is_file():
    failures.append("api-gateway is missing GatewayTrustHeaderFilterTests.kt")

if failures:
    for failure in failures:
        print(f"ERROR: {failure}", file=sys.stderr)
    raise SystemExit(1)

print(
    "Runtime wiring checks passed for gateway trust injection, Payment shared "
    f"persistence, {len(SHEDLOCK_SCHEMAS)} ShedLock providers/migrations, and "
    f"{len(COMPOSE_SEARCH_PATHS)} Compose database search paths."
)
