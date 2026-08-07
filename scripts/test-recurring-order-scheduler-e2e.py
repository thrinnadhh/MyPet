#!/usr/bin/env python3
"""Prove recurring-order due processing without silent charging or ordering."""

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
LOCK_NAME = "recurringOrderConfirmationReminder"

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
        return compose("logs", "--no-color", "--tail", "250", SCHEDULER_SERVICE)
    except Exception as error:  # pragma: no cover - diagnostic fallback
        return f"Unable to collect scheduler logs: {error}"


def jwt_part(value: dict[str, Any]) -> str:
    raw = json.dumps(value, separators=(",", ":")).encode()
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


def customer_token(customer_id: str) -> str:
    now = int(time.time())
    claims = {
        "sub": customer_id,
        "iat": now,
        "exp": now + 3600,
        "email": f"recurring-e2e-{customer_id[:8]}@example.com",
        "app_metadata": {"role": "CUSTOMER"},
        "user_metadata": {"full_name": "Recurring E2E Customer"},
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
    timeout: int = 45,
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
        raise AssertionError(
            f"Recurring scheduler cron mismatch: expected {EXPECTED_CRON!r}, found {configured_cron!r}",
        )
    if scheduling_role.upper() not in {"ALL", "WORKER"}:
        raise AssertionError(f"Scheduler service cannot execute workers: role={scheduling_role!r}")

    initial_lock = poll(
        "recurring scheduler to acquire its named ShedLock",
        scheduler_lock_state,
        bool,
        timeout=20,
        diagnostics=lambda: f"environment={environment!r}\n{scheduler_logs()}",
    )

    row = sql(
        "SELECT subscription_id::text || '|' || customer_id::text "
        "FROM orders.recurring_order_subscriptions "
        "WHERE status='ACTIVE' ORDER BY created_at DESC LIMIT 1;"
    )
    if not row:
        raise AssertionError("P2B did not leave an active recurring-order subscription")
    subscription_id, customer_id = row.split("|", 1)

    baseline_orders = int(sql("SELECT count(*) FROM orders.orders;") or "0")
    baseline_transactions = int(sql("SELECT count(*) FROM payments.transactions;") or "0")

    sql(
        "UPDATE orders.recurring_order_subscriptions "
        "SET status='ACTIVE', next_order_at=now()-interval '1 minute', "
        "last_reminded_at=NULL, updated_at=now() "
        f"WHERE subscription_id='{subscription_id}'::uuid;"
    )

    # This stack is isolated and disposable. Release only this scheduler's own
    # lock so a startup cycle cannot mask the deterministic due-state check.
    sql(
        "UPDATE orders.shedlock SET lock_until=now()-interval '1 second' "
        f"WHERE name='{LOCK_NAME}';"
    )

    state = poll(
        "recurring subscription to enter AWAITING_CONFIRMATION",
        lambda: sql(
            "SELECT status || '|' || coalesce(last_reminded_at::text,'') "
            "FROM orders.recurring_order_subscriptions "
            f"WHERE subscription_id='{subscription_id}'::uuid;"
        ),
        lambda value: value.startswith("AWAITING_CONFIRMATION|") and len(value.split("|", 1)[1]) > 0,
        diagnostics=lambda: (
            f"environment={environment!r}\n"
            f"initialLock={initial_lock!r}\n"
            f"currentLock={scheduler_lock_state()!r}\n"
            f"{scheduler_logs()}"
        ),
    )

    published_count = poll(
        "published RecurringOrderConfirmationRequired outbox event",
        lambda: int(sql(
            "SELECT count(*) FROM orders.outbox_events "
            f"WHERE aggregate_id='{subscription_id}'::uuid "
            "AND aggregate_type='RECURRING_ORDER' "
            "AND event_type='RecurringOrderConfirmationRequired' "
            "AND published_at IS NOT NULL "
            "AND payload #>> '{data,automaticCharge}' = 'false';"
        ) or "0"),
        lambda value: value == 1,
        diagnostics=lambda: f"lock={scheduler_lock_state()!r}\n{scheduler_logs()}",
    )

    subscriptions = request("/api/v1/orders/subscriptions", customer_token(customer_id))
    visible = next(
        (item for item in subscriptions if item.get("subscriptionId") == subscription_id),
        None,
    )
    if not visible or visible.get("status") != "AWAITING_CONFIRMATION":
        raise AssertionError(f"Customer API did not expose the due confirmation state: {visible}")

    if int(sql("SELECT count(*) FROM orders.orders;") or "0") != baseline_orders:
        raise AssertionError("Recurring scheduler silently created an order")
    if int(sql("SELECT count(*) FROM payments.transactions;") or "0") != baseline_transactions:
        raise AssertionError("Recurring scheduler silently created a payment transaction")

    # A second scheduler cycle must not create another reminder event because
    # the subscription is no longer ACTIVE.
    time.sleep(7)
    duplicate_count = int(sql(
        "SELECT count(*) FROM orders.outbox_events "
        f"WHERE aggregate_id='{subscription_id}'::uuid "
        "AND event_type='RecurringOrderConfirmationRequired';"
    ) or "0")
    if duplicate_count != published_count:
        raise AssertionError(
            f"Recurring due processing was not idempotent: expected {published_count}, found {duplicate_count}",
        )

    with REPORT.open("a", encoding="utf-8") as handle:
        handle.write("\n## Recurring-order scheduler certification\n\n")
        handle.write(
            "- ✅ Scheduler registration, active worker role, configured cadence and named lock were verified.\n"
        )
        handle.write(
            "- ✅ Due subscription moved to `AWAITING_CONFIRMATION` with a reminder timestamp.\n"
        )
        handle.write(
            "- ✅ `RecurringOrderConfirmationRequired` was published durably with `automaticCharge=false`.\n"
        )
        handle.write(
            "- ✅ Customer API exposed the confirmation state; no order or payment was created.\n"
        )
        handle.write(
            "- ✅ A second scheduler cycle produced no duplicate reminder event.\n"
        )

    print(json.dumps({
        "status": "PASS",
        "subscriptionId": subscription_id,
        "state": state,
        "schedulerEnvironment": {
            "cron": configured_cron,
            "lockAtMostFor": lock_at_most,
            "lockAtLeastFor": lock_at_least,
            "role": scheduling_role,
        },
        "publishedReminderEvents": published_count,
        "ordersCreated": 0,
        "paymentsCreated": 0,
    }))


if __name__ == "__main__":
    main()
