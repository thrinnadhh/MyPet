#!/usr/bin/env python3
"""Static completeness gate for the M8 full-feature verification milestone."""

from __future__ import annotations

import ast
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAINS = {
    "customer", "provider", "catalog", "appointment", "order", "payment",
    "loyalty", "captain", "dispatch", "review", "notification", "chat",
    "content", "admin",
}

failures: list[str] = []

runtime_path = ROOT / "scripts/test-m8-feature-matrix.py"
runner_path = ROOT / "scripts/run-m8-feature-matrix.py"
catalog_path = (
    ROOT
    / "backend/mypet-application/src/main/kotlin/com/pawsnearme/application/verification/FeatureVerificationConfiguration.kt"
)
test_path = (
    ROOT
    / "backend/mypet-application/src/test/kotlin/com/pawsnearme/application/verification/FeatureVerificationCatalogTest.kt"
)
test_all_path = ROOT / "scripts/test-all.sh"
doc_path = ROOT / "docs/implementation/m8-full-feature-verification.md"

for path in (runtime_path, runner_path, catalog_path, test_path, test_all_path, doc_path):
    if not path.is_file():
        failures.append(f"missing M8 verification artifact: {path.relative_to(ROOT)}")

for python_path in (runtime_path, runner_path):
    if python_path.is_file():
        try:
            ast.parse(python_path.read_text(encoding="utf-8"), filename=str(python_path))
        except SyntaxError as exc:
            failures.append(f"M8 Python artifact is invalid: {exc}")

if runtime_path.is_file():
    source = runtime_path.read_text(encoding="utf-8")
    for domain in sorted(DOMAINS):
        if f'"{domain}"' not in source or f'passed("{domain}"' not in source:
            failures.append(f"M8 runtime verifier is missing executable evidence for {domain}")
    for required in (
        "CONCURRENCY",
        "IDEMPOTENCY",
        "AUTHORIZATION",
        "publishedOutboxEvents",
        "M8 matrix stopped",
    ):
        if required.lower() not in source.lower():
            failures.append(f"M8 runtime verifier is missing evidence marker: {required}")

if runner_path.is_file():
    runner = runner_path.read_text(encoding="utf-8")
    for required in (
        "test-m8-feature-matrix.py",
        "/api/v1/appointments/hold",
        "expected == (400,)",
        "expected = (409,)",
        "/status?status=IN_PROGRESS",
        "_original_request(method, path, actor, payload, expected=(400,))",
        '"unsupportedStatusRejected": True',
        "published AppointmentBooked outbox event with slot_start",
        "appointment-outbox.txt",
        "notification-service.log",
        "notification-consumer-group.txt",
        "appointments-events.txt",
    ):
        if required not in runner:
            failures.append(f"M8 runner is missing explicit contract or diagnostic mapping: {required}")

if catalog_path.is_file():
    catalog = catalog_path.read_text(encoding="utf-8")
    for domain in sorted(DOMAINS):
        if f'domain("{domain}"' not in catalog:
            failures.append(f"M8 feature catalog is missing {domain}")
    for evidence in (
        "AUTHORIZATION_BOUNDARY",
        "ASYNC_PROJECTION",
        "IDEMPOTENCY",
        "CONCURRENCY",
        "SCHEDULER",
    ):
        if evidence not in catalog:
            failures.append(f"M8 feature catalog is missing {evidence}")
    if '"cutoverAuthorized" to false' not in catalog:
        failures.append("M8 must not authorize infrastructure cutover")
    if '"legacyRollbackRequired" to true' not in catalog:
        failures.append("M8 must retain the distributed rollback requirement")

if test_path.is_file():
    tests = test_path.read_text(encoding="utf-8")
    if "assertEquals(14, catalog.domains.size)" not in tests:
        failures.append("M8 catalog test must enforce exactly fourteen domains")

if test_all_path.is_file():
    test_all = test_all_path.read_text(encoding="utf-8")
    if 'python3 "$ROOT/scripts/run-m8-feature-matrix.py"' not in test_all:
        failures.append("scripts/test-all.sh must execute the M8 contract-aware runner")

if doc_path.is_file():
    doc = doc_path.read_text(encoding="utf-8")
    for domain in sorted(DOMAINS):
        if f"`{domain}`" not in doc:
            failures.append(f"M8 runbook is missing domain {domain}")
    if "M9" not in doc or "not authorized" not in doc.lower():
        failures.append("M8 runbook must state that M9 cutover is not authorized")

if failures:
    for failure in failures:
        print(f"ERROR: {failure}", file=sys.stderr)
    raise SystemExit(1)

print(
    "M8 verification completeness passed for "
    f"{len(DOMAINS)} domains, scenario/runner syntax, evidence classes, async diagnostics and cutover guard."
)
