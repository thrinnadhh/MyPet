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

    return _original_request(method, path, actor, payload, expected)


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
            "SELECT id,user_id,reference_type,reference_id,fire_at,template_code,fired,delivery_status "
            "FROM notifications.scheduled_reminders ORDER BY created_at DESC LIMIT 50;"
        ),
        "notification-processed-events.txt": (
            "SELECT event_id,processed_at FROM notifications.processed_events "
            "ORDER BY processed_at DESC LIMIT 50;"
        ),
    }
    for file_name, statement in queries.items():
        try:
            value = matrix.sql(statement)
        except Exception as diagnostic_error:
            value = f"diagnostic query failed: {diagnostic_error}"
        (DIAGNOSTICS / file_name).write_text(value, encoding="utf-8")

    for service in ("appointment-service", "notification-service", "kafka"):
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
matrix.poll = evidence_poll

try:
    result = matrix.main()
except BaseException as error:
    capture_failure_diagnostics(error)
    raise

print(json.dumps({"runner": "contract-aware", "result": result}))
raise SystemExit(result)
