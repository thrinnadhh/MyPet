#!/usr/bin/env python3
"""Compatibility entrypoint for the P2B connected runtime.

The M8 graph predates an `updated_at` column on appointments. Keep the runtime
query aligned to the authoritative appointment schema without changing domain
storage solely for a test harness.
"""

from __future__ import annotations

import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "scripts/run-p2b-connected-e2e.py"
spec = importlib.util.spec_from_file_location("mypet_p2b_connected_runner", RUNNER)
if spec is None or spec.loader is None:
    raise SystemExit("Unable to load P2B connected runtime")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

original_sql = module.sql


def schema_compatible_sql(statement: str) -> str:
    return original_sql(statement.replace("ORDER BY updated_at DESC", "ORDER BY booked_at DESC"))


module.sql = schema_compatible_sql
raise SystemExit(module.main())
