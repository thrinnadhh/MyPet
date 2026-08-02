#!/usr/bin/env python3
"""Compatibility entrypoint for the P2B connected runtime.

The M8 graph predates an `updated_at` column on appointments and now emits a
structured Markdown PASS marker instead of the legacy completion sentence.
Keep Order 11 aligned to the authoritative schema and report contract without
weakening its fourteen-domain evidence requirements.
"""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "scripts/run-p2b-connected-e2e.py"
spec = importlib.util.spec_from_file_location("mypet_p2b_connected_runner", RUNNER)
if spec is None or spec.loader is None:
    raise SystemExit("Unable to load P2B connected runtime")
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)

original_sql = module.sql


def schema_compatible_sql(statement: str) -> str:
    return original_sql(statement.replace("ORDER BY updated_at DESC", "ORDER BY booked_at DESC"))


def compatible_verify_m8_report() -> None:
    report = module.REPORT.read_text(encoding="utf-8")
    if "## M8 full feature verification matrix" not in report:
        raise AssertionError("M8 connected runtime evidence is missing")

    missing = sorted(
        domain for domain in module.REQUIRED_DOMAINS if f"**{domain}**" not in report
    )
    if missing:
        raise AssertionError(f"M8 report is missing domain evidence: {missing}")

    completion_marker = (
        f"**PASS** — all {len(module.REQUIRED_DOMAINS)} required feature domains "
        "completed through one connected"
    )
    if completion_marker not in report:
        raise AssertionError("M8 did not report complete fourteen-domain success")


module.sql = schema_compatible_sql
module.verify_m8_report = compatible_verify_m8_report
raise SystemExit(module.main())
