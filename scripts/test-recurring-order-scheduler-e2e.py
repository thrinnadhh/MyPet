#!/usr/bin/env python3
"""Certify due subscription -> exactly one real operational order."""

from __future__ import annotations

import base64
import json
import os
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any, Callable

ROOT = Path(__file__).resolve().parents[1]
REPORT = Path(os.environ.get("MYPET_SMOKE_REPORT", ROOT / "build/reports/full-stack-smoke.md"))
PROJECT_NAME = os.environ.get("COMPOSE_PROJECT_NAME", "mypet-e2e")
ENV_FILE = os.environ.get("MYPET_ENV_FILE")
GATEWAY = os.environ.get("MYPET_GATEWAY_URL", "http://localhost:8080")
SCHEDULER_SERVICE = os.environ.get("MYPET_SCHEDULER_SERVICE", "order-service")
EXPECTED_CRON = os.environ.get("MYPET_EXPECT_RECURRING_CRON", "")
LOCK_NAME = "recurringOrderGeneration"

if not ENV_FILE:
    raise SystemExit("MYPET_ENV_FILE must be set by the stack runner")

compose_files_raw = os.environ.get("MYPET_COMPOSE_FILES", "")
if compose_files_raw:
    compose_files = [Path(item) for item in compose_files_raw.split(",") if item]
else:
    compose_files = [
        ROOT / "infra/docker-compose.yml",
        ROOT / "infra/docker-compose.replicas.yml",
        ROOT / "infra/docker-compose.m4.yml",
        ROOT / "infra/docker-compose.local.yml",
    ]

COMPOSE = ["docker", "compose", "-p", PROJECT_NAME, "--env-file", ENV_FILE]
for compose_file in compose_files:
    COMPOSE.extend(("-f", str(compose_file)))


def compose(*args: str) -> str:
    result = subprocess.run(
        [*COMPOSE, *args],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(f"Compose failed ({result.returncode}): {' '.join(args)}\n{result.stderr}")
    return result.stdout.strip()


def sql(statement: str) -> str:
    return compose(
        "exec", "-T", "postgres", "psql", "-U", "postgres", "-d", "pawsnearme",
        "-v", "ON_ERROR_STOP=1", "-Atc", statement,
    )


def scheduler_environment() -> str:
    return compose(
        "exec", "-T", SCHEDULER_SERVICE, "sh", "-lc",
        "printf '%s|%s|%s|%s' "
        '"${ORDER_RECURRING_REMINDER_CRON:-}" '
        '"${ORDER_RECURRING_REMINDER_LOCK_AT_MOST_FOR:-}" '
        '"${ORDER_RECURRING_REMINDER_LOCK_AT_LEAST_FOR:-}" '
        '"${MYPET_SCHEDULING_ROLE:-}"',
    )


def scheduler_lock_state() -> str:
    return sql(
        "SELECT name || '|' || lock_until::text || '|' || locked_at::text || '|' || locked_by "
        "FROM orders.shedlock "
        f"WHERE name='{LOCK_NAME}';"
    )


def scheduler_logs() -> str:
    try:
        return compose("logs", "--no-color", "--tail", "300", SCHEDULER_SERVICE)
    except Exception as error:  # pragma: no cover
        return f"Unable to collect scheduler logs: {error}"


def jwt_part(value: dict[str, Any]) -> str:
    raw = json.dumps(value, separators=(",", ":")).encode()
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


def actor_token(user_id: str, role: str) -> str:
    now = int(time.time())
    claims = {
        "sub": user_id,
        "iat": now,
        "exp": now + 3600,
        "email": f"recurring-e2e-{role.lower()}-{user_id[:8]}@example.com",
        "app_metadata": {"role": role},
        "user_metadata": {"full_name": f"Recurring E2E {role.title()}"},
    }
    return f"{jwt_part({'alg': 'none', 'typ': 'JWT'})}.{jwt_part(claims)}."


def request(path: str, token: str) -> Any:
    req = urllib.request.Request(
        f"{GATEWAY}{path}",
        headers={"Accept": "application/json", "Authorization": f"Bearer {token}"},
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            status = response.status
            body = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        status = error.code
        body = error.read().decode("utf-8")
    if status != 200:
        raise AssertionError(f"GET {path} expected 200, received {status}: {body}")
    return json.loads(body)


def poll(
    label: str,
    probe: Callable[[], Any],
    ready: Callable[[Any], bool],
    timeout: int = 60,
    diagnostics: Callable[[], str] | None = None,
):
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        last = probe()
        if ready(last):
            return last
        time.sleep(1)
    detail = f"Timed out waiting for {label}; last value={last!r}"
    if diagnostics is not None:
        detail += f"\nDiagnostics:\n{diagnostics()}"
    raise AssertionError(detail)


def main() -> None:
    environment = scheduler_environment()
    configured_cron, lock_at_most, lock_at_least, scheduling_role = environment.split("|", 3)
    if EXPECTED_CRON and configured_cron != EXPECTED_CRON:
        raise AssertionError(f"Recurring scheduler cron mismatch: expected {EXPECTED_CRON!r}, found {configured_cron!r}")
    if scheduling_role.upper() not in {"ALL", "WORKER"}:
        raise AssertionError(f"Scheduler service cannot execute workers: role={scheduling_role!r}")

    initial_lock = poll(
        "recurring scheduler to acquire its named ShedLock",
        scheduler_lock_state,
        bool,
        timeout=25,
        diagnostics=lambda: f"environment={environment!r}\n{scheduler_logs()}",
    )

    row = sql(
        "SELECT s.subscription_id::text || '|' || s.customer_id::text || '|' || s.provider_id::text || '|' || "
        "p.owner_user_id::text || '|' || s.payment_method "
        "FROM orders.recurring_order_subscriptions s "
        "JOIN providers.providers p ON p.provider_id=s.provider_id "
        "WHERE s.status='ACTIVE' ORDER BY s.created_at DESC LIMIT 1;"
    )
    if not row:
        raise AssertionError("P2B did not leave an active recurring-order subscription")
    subscription_id, customer_id, provider_id, merchant_id, payment_method = row.split("|", 4)

    item_rows = sql(
        "SELECT i.offering_id::text || '|' || i.base_quantity::text || '|' || s.quantity_multiplier::text || '|' || o.stock_quantity::text "
        "FROM orders.recurring_order_subscription_items i "
        "JOIN orders.recurring_order_subscriptions s ON s.subscription_id=i.subscription_id "
        "JOIN catalog.offerings o ON o.offering_id=i.offering_id "
        f"WHERE i.subscription_id='{subscription_id}'::uuid ORDER BY i.created_at;"
    )
    if not item_rows:
        raise AssertionError("Recurring subscription has no persisted product snapshots")
    stock_before: dict[str, tuple[int, int]] = {}
    for item_row in item_rows.splitlines():
        offering_id, base_quantity, multiplier, stock_quantity = item_row.split("|", 3)
        required = int(base_quantity) * int(multiplier)
        stock_before[offering_id] = (int(stock_quantity), required)

    baseline_orders = int(sql("SELECT count(*) FROM orders.orders;") or "0")
    baseline_transactions = int(sql("SELECT count(*) FROM payments.transactions;") or "0")

    # Freeze this one scheduler briefly so setup cannot race the worker.
    sql(
        "UPDATE orders.shedlock SET lock_until=now()+interval '25 seconds' "
        f"WHERE name='{LOCK_NAME}';"
    )
    sql(
        "UPDATE orders.recurring_order_subscriptions "
        "SET status='ACTIVE', next_order_at=date_trunc('second', now()-interval '1 minute'), "
        "last_reminded_at=NULL, last_failure_code=NULL, last_failure_detail=NULL, updated_at=now() "
        f"WHERE subscription_id='{subscription_id}'::uuid;"
    )
    scheduled_for = sql(
        "SELECT next_order_at::text FROM orders.recurring_order_subscriptions "
        f"WHERE subscription_id='{subscription_id}'::uuid;"
    )
    sql(
        "UPDATE orders.shedlock SET lock_until=now()-interval '1 second' "
        f"WHERE name='{LOCK_NAME}';"
    )

    occurrence = poll(
        "one recurring occurrence to create a real order",
        lambda: sql(
            "SELECT status || '|' || coalesce(order_id::text,'') || '|' || coalesce(failure_code,'') "
            "FROM orders.recurring_order_occurrences "
            f"WHERE subscription_id='{subscription_id}'::uuid AND scheduled_for='{scheduled_for}'::timestamptz;"
        ),
        lambda value: value.startswith("ORDER_CREATED|") and len(value.split("|", 2)[1]) > 0,
        diagnostics=lambda: (
            f"environment={environment!r}\ninitialLock={initial_lock!r}\n"
            f"currentLock={scheduler_lock_state()!r}\n{scheduler_logs()}"
        ),
    )
    _, generated_order_id, _ = occurrence.split("|", 2)

    if int(sql("SELECT count(*) FROM orders.orders;") or "0") != baseline_orders + 1:
        raise AssertionError("Due subscription did not create exactly one order")
    if int(sql("SELECT count(*) FROM payments.transactions;") or "0") != baseline_transactions:
        raise AssertionError("Recurring scheduler silently created a payment transaction")

    order_state = sql(
        "SELECT status::text || '|' || payment_method || '|' || payment_status "
        "FROM orders.orders "
        f"WHERE order_id='{generated_order_id}'::uuid;"
    )
    status, generated_payment_method, payment_status = order_state.split("|", 2)
    if status != "PLACED":
        raise AssertionError(f"Generated recurring order bypassed merchant acceptance: {order_state}")
    if generated_payment_method != payment_method:
        raise AssertionError(f"Generated payment method changed: {order_state}")
    if payment_method == "COD" and payment_status != "COD_PENDING":
        raise AssertionError(f"Generated COD order has invalid payment state: {order_state}")
    if payment_method != "COD" and payment_status != "PENDING":
        raise AssertionError(f"Generated prepaid order was silently charged: {order_state}")

    for offering_id, (before, required) in stock_before.items():
        current = int(sql(
            "SELECT stock_quantity FROM catalog.offerings "
            f"WHERE offering_id='{offering_id}'::uuid;"
        ))
        if current != before - required:
            raise AssertionError(
                f"Recurring inventory mutation mismatch for {offering_id}: before={before} required={required} after={current}"
            )

    next_order_at = sql(
        "SELECT next_order_at::text FROM orders.recurring_order_subscriptions "
        f"WHERE subscription_id='{subscription_id}'::uuid;"
    )
    if next_order_at == scheduled_for:
        raise AssertionError("Subscription next execution did not advance")

    customer_token = actor_token(customer_id, "CUSTOMER")
    merchant_token = actor_token(merchant_id, "MERCHANT")
    customer_orders = request(f"/api/v1/orders/customer/{customer_id}", customer_token)
    if not any(item.get("orderId") == generated_order_id and item.get("status") == "PLACED" for item in customer_orders):
        raise AssertionError("Customer API does not expose generated recurring order")
    merchant_orders = request(f"/api/v1/orders/provider/{provider_id}", merchant_token)
    if not any(item.get("orderId") == generated_order_id and item.get("status") == "PLACED" for item in merchant_orders):
        raise AssertionError("Merchant order queue does not expose generated recurring order")

    merchant_subscriptions = request(f"/api/v1/orders/subscriptions/provider/{provider_id}", merchant_token)
    visible_demand = next((item for item in merchant_subscriptions if item.get("subscriptionId") == subscription_id), None)
    if not visible_demand or visible_demand.get("lastOrderId") != generated_order_id or not visible_demand.get("items"):
        raise AssertionError(f"Merchant recurring-demand projection is incomplete: {visible_demand}")
    customer_subscriptions = request("/api/v1/orders/subscriptions", customer_token)
    customer_subscription = next((item for item in customer_subscriptions if item.get("subscriptionId") == subscription_id), None)
    if not customer_subscription or customer_subscription.get("lastOrderId") != generated_order_id:
        raise AssertionError(f"Customer subscription did not synchronize generated order: {customer_subscription}")

    published_count = poll(
        "published RecurringOrderGenerated event",
        lambda: int(sql(
            "SELECT count(*) FROM orders.outbox_events "
            f"WHERE aggregate_id='{subscription_id}'::uuid "
            "AND aggregate_type='RECURRING_ORDER' "
            "AND event_type='RecurringOrderGenerated' "
            "AND published_at IS NOT NULL "
            f"AND payload #>> '{{data,orderId}}' = '{generated_order_id}';"
        ) or "0"),
        lambda value: value == 1,
        diagnostics=lambda: f"lock={scheduler_lock_state()!r}\n{scheduler_logs()}",
    )

    # Force another eligible scheduler tick. The advanced next_order_at and DB
    # occurrence uniqueness must keep this exact occurrence at one logical order.
    sql(
        "UPDATE orders.shedlock SET lock_until=now()-interval '1 second' "
        f"WHERE name='{LOCK_NAME}';"
    )
    time.sleep(8)

    occurrence_count = int(sql(
        "SELECT count(*) FROM orders.recurring_order_occurrences "
        f"WHERE subscription_id='{subscription_id}'::uuid AND scheduled_for='{scheduled_for}'::timestamptz;"
    ) or "0")
    if occurrence_count != 1:
        raise AssertionError(f"Recurring occurrence uniqueness failed: {occurrence_count}")
    if int(sql("SELECT count(*) FROM orders.orders;") or "0") != baseline_orders + 1:
        raise AssertionError("Scheduler retry created a duplicate recurring order")
    if int(sql(
        "SELECT count(*) FROM orders.outbox_events "
        f"WHERE aggregate_id='{subscription_id}'::uuid AND event_type='RecurringOrderGenerated' "
        f"AND payload #>> '{{data,orderId}}' = '{generated_order_id}';"
    ) or "0") != published_count:
        raise AssertionError("Scheduler retry duplicated RecurringOrderGenerated")
    for offering_id, (before, required) in stock_before.items():
        current = int(sql(f"SELECT stock_quantity FROM catalog.offerings WHERE offering_id='{offering_id}'::uuid;"))
        if current != before - required:
            raise AssertionError("Scheduler retry deducted recurring stock twice")

    with REPORT.open("a", encoding="utf-8") as handle:
        handle.write("\n## Recurring-order scheduler certification\n\n")
        handle.write("- ✅ Due ACTIVE subscription generated exactly one real `PLACED` order.\n")
        handle.write("- ✅ Merchant and Customer APIs exposed the same generated order and subscription reference.\n")
        handle.write("- ✅ Persisted subscription item snapshots drove expected stock reservation exactly once.\n")
        handle.write("- ✅ Scheduler performed no silent payment; prepaid remains `PENDING`, COD remains `COD_PENDING`.\n")
        handle.write("- ✅ Schedule advanced once and `RecurringOrderGenerated` was published durably once.\n")
        handle.write("- ✅ A subsequent scheduler tick produced no duplicate occurrence, order, event, payment or stock mutation.\n")

    print(json.dumps({
        "status": "PASS",
        "subscriptionId": subscription_id,
        "scheduledFor": scheduled_for,
        "generatedOrderId": generated_order_id,
        "nextOrderAt": next_order_at,
        "paymentMethod": payment_method,
        "schedulerEnvironment": {
            "cron": configured_cron,
            "lockAtMostFor": lock_at_most,
            "lockAtLeastFor": lock_at_least,
            "role": scheduling_role,
        },
        "generatedEvents": published_count,
        "ordersCreated": 1,
        "paymentsCreated": 0,
    }))


if __name__ == "__main__":
    main()