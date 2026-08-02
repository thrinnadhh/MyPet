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
test_all = (ROOT / "scripts/test-all.sh").read_text(encoding="utf-8")
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

assert 'python3 "$ROOT/scripts/run-p2b-connected-e2e-entry.py"' in test_all

print("P2B_CONNECTED_E2E_CONTRACT_OK journeys=10 dimensions=6")
