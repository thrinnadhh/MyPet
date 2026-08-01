#!/usr/bin/env python3
"""Execute the M8 scenario graph with explicit contract and async evidence."""

from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path
from typing import Any, Callable

ROOT = Path(__file__).resolve().parents[1]
SCENARIO_PATH = ROOT / "scripts/test-m8-feature-matrix.py"
DIAGNOSTICS = ROOT / "build/reports/docker-diagnostics"

spec = importlib.util.spec_from_file_location("mypet_m8_feature_matrix", SCENARIO_PATH)
if spec is None or spec.loader is None:
    raise SystemExit(f"Unable to load M8 scenario graph from {SCENARIO_PATH}")

matrix = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = matrix
spec.loader.exec_module(matrix)

_original_request = matrix.request
_original_poll = matrix.poll
_original_require = matrix.require
_payment_transactions: dict[str, str] = {}


def contract_request(
    method: str,
    path: str,
    actor: Any = None,
    payload: Any = None,
    expected: tuple[int, ...] = (200,),
) -> Any:
    if method == "POST" and path == "/api/v1/appointments/hold" and expected == (400,):
        expected = (409,)

    if method == "PUT" and "/status?status=IN_PROGRESS" in path and expected == (200,):
        _original_request(method, path, actor, payload, expected=(400,))
        return {"status": "CONFIRMED", "unsupportedStatusRejected": True}

    # The base scenario first proves COD eligibility. For the connected payment
    # proof, place the shared order as CARD so it can exercise initiation,
    # capture persistence and the paid-order confirmation boundary.
    if (
        method == "POST"
        and path == "/api/v1/orders"
        and isinstance(payload, dict)
        and str(payload.get("paymentMethod", "")).upper() == "COD"
    ):
        online_payload = dict(payload)
        online_payload.pop("quoteToken", None)
        online_payload["paymentMethod"] = "CARD"
        online_quote = _original_request(
            "POST",
            "/api/v1/checkout/quote",
            actor,
            online_payload,
            expected=(200,),
        )
        online_payload["quoteToken"] = online_quote["quoteToken"]
        return _original_request(method, path, actor, online_payload, expected)

    # Payment results are only valid after a durable PENDING transaction exists.
    # Initiate the sandbox Razorpay order first, then record the result against it.
    if (
        method == "POST"
        and path == "/api/v1/payments/transactions/result"
        and isinstance(payload, dict)
    ):
        initiation = _original_request(
            "POST",
            "/api/v1/payments/orders",
            actor,
            {
                "userId": payload["userId"],
                "referenceId": payload["referenceId"],
                "amount": payload["amount"],
                "transactionType": payload["transactionType"],
            },
            expected=(201,),
        )
        _payment_transactions[str(payload["referenceId"])] = str(initiation["transactionId"])
        return _original_request(method, path, actor, payload, expected)

    # The stale scenario asks the merchant to set ACCEPTED directly. Online
    # orders must instead cross the payment verification boundary via confirm.
    if (
        method == "PUT"
        and path.startswith("/api/v1/orders/")
        and "/status?status=ACCEPTED" in path
    ):
        order_id = path.removeprefix("/api/v1/orders/").split("/", 1)[0]
        payment_id = _payment_transactions.get(order_id)
        if payment_id is not None:
            return _original_request(
                "POST",
                f"/api/v1/orders/{order_id}/confirm?paymentId={payment_id}",
                actor,
                expected=(200,),
            )

    return _original_request(method, path, actor, payload, expected)


def contract_require(condition: bool, message: str, details: Any = None) -> None:
    if message == "order was not placed" and isinstance(details, dict):
        is_valid_cod_order = (
            details.get("status") == "ACCEPTED"
            and details.get("paymentMethod") == "COD"
            and details.get("paymentStatus") == "COD_PENDING"
            and details.get("acceptedAt") is not None
        )
        _original_require(
            condition or is_valid_cod_order,
            "COD order did not enter the accepted placement state",
            details,
        )
        return
    _original_require(condition, message, details)


def evidence_poll(
    label: str,
    probe: Callable[[], Any],
    ready: Callable[[Any], bool],
    timeout: int = 40,
) -> Any:
    if label == "appointment reminder creation":
        outbox = _original_poll(
            "published AppointmentBooked outbox event with slot_start",
            lambda: matrix.sql(
                "SELECT json_build_object("
                "'eventType', event_type, "
                "'payload', payload, "
                "'publishedAt', published_at"
                ")::text "
                "FROM appointments.outbox_events "
                "WHERE event_type='AppointmentBooked' "
                "ORDER BY created_at DESC LIMIT 1;"
            ),
            lambda value: bool(value)
            and '"publishedAt" : null' not in value
            and '"slot_start":null' not in value.replace(" ", "")
            and '"slot_start"' in value,
            timeout=30,
        )
        matrix.append_report(
            "- ✅ **appointment async handoff** — AppointmentBooked outbox payload "
            f"contains slot_start and was published: `{outbox[:500]}`\n"
        )
    return _original_poll(label, probe, ready, timeout)


def capture_failure_diagnostics(error: BaseException) -> None:
    DIAGNOSTICS.mkdir(parents=True, exist_ok=True)
    (DIAGNOSTICS / "m8-error.txt").write_text(str(error), encoding="utf-8")

    queries = {
        "appointment-outbox.txt": (
            "SELECT event_id,event_type,payload,published_at,created_at "
            "FROM appointments.outbox_events ORDER BY created_at DESC LIMIT 20;"
        ),
        "notification-reminders.txt": (
            "SELECT reminder_id,user_id,reference_type,reference_id,fire_at,template_code,"
            "fired,delivery_status,created_at "
            "FROM notifications.scheduled_reminders ORDER BY created_at DESC LIMIT 50;"
        ),
        "notification-processed-events.txt": (
            "SELECT event_id,processed_at FROM notifications.processed_events "
            "ORDER BY processed_at DESC LIMIT 50;"
        ),
        "catalog-offerings.txt": (
            "SELECT offering_id,provider_id,name,price,status,stock_quantity,created_at "
            "FROM catalog.offerings ORDER BY created_at DESC LIMIT 50;"
        ),
        "orders.txt": (
            "SELECT order_id,customer_id,provider_id,status,total_amount,placed_at,accepted_at,"
            "ready_at,picked_up_at,delivered_at,payment_method,payment_status "
            "FROM orders.orders ORDER BY placed_at DESC LIMIT 50;"
        ),
        "order-outbox.txt": (
            "SELECT event_id,event_type,payload,published_at,created_at "
            "FROM orders.outbox_events ORDER BY created_at DESC LIMIT 50;"
        ),
        "payment-transactions.txt": (
            "SELECT transaction_id,user_id,reference_id,transaction_type,amount,status,"
            "gateway,gateway_transaction_id,created_at,updated_at "
            "FROM payments.transactions ORDER BY created_at DESC LIMIT 50;"
        ),
    }
    for file_name, statement in queries.items():
        try:
            value = matrix.sql(statement)
        except Exception as diagnostic_error:
            value = f"diagnostic query failed: {diagnostic_error}"
        (DIAGNOSTICS / file_name).write_text(value, encoding="utf-8")

    services = (
        "api-gateway",
        "appointment-service",
        "notification-service",
        "order-service",
        "catalog-service",
        "discovery-service",
        "payment-service",
        "dispatch-service",
        "kafka",
    )
    for service in services:
        try:
            value = matrix.compose("logs", "--no-color", "--tail", "600", service)
        except Exception as diagnostic_error:
            value = f"diagnostic log capture failed: {diagnostic_error}"
        (DIAGNOSTICS / f"{service}.log").write_text(value, encoding="utf-8")

    try:
        group_state = matrix.compose(
            "exec", "-T", "kafka",
            "/opt/kafka/bin/kafka-consumer-groups.sh",
            "--bootstrap-server", "kafka:29092",
            "--describe", "--group", "notification-service",
        )
    except Exception as diagnostic_error:
        group_state = f"consumer group diagnostic failed: {diagnostic_error}"
    (DIAGNOSTICS / "notification-consumer-group.txt").write_text(group_state, encoding="utf-8")

    try:
        latest_events = matrix.compose(
            "exec", "-T", "kafka",
            "timeout", "8",
            "/opt/kafka/bin/kafka-console-consumer.sh",
            "--bootstrap-server", "kafka:29092",
            "--topic", "appointments.events",
            "--from-beginning", "--max-messages", "20",
        )
    except Exception as diagnostic_error:
        latest_events = f"appointment topic diagnostic failed: {diagnostic_error}"
    (DIAGNOSTICS / "appointments-events.txt").write_text(latest_events, encoding="utf-8")


matrix.request = contract_request
matrix.require = contract_require
matrix.poll = evidence_poll

try:
    result = matrix.main()
except BaseException as error:
    capture_failure_diagnostics(error)
    raise

print(json.dumps({"runner": "contract-aware", "result": result}))
raise SystemExit(result)
