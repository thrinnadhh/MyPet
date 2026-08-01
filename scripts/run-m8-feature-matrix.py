#!/usr/bin/env python3
"""Execute the M8 scenario graph with explicit HTTP contract equivalences."""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
SCENARIO_PATH = ROOT / "scripts/test-m8-feature-matrix.py"

spec = importlib.util.spec_from_file_location("mypet_m8_feature_matrix", SCENARIO_PATH)
if spec is None or spec.loader is None:
    raise SystemExit(f"Unable to load M8 scenario graph from {SCENARIO_PATH}")

matrix = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = matrix
spec.loader.exec_module(matrix)

_original_request = matrix.request


def contract_request(
    method: str,
    path: str,
    actor: Any = None,
    payload: Any = None,
    expected: tuple[int, ...] = (200,),
) -> Any:
    # Appointment slot contention is represented as HTTP 409 Conflict by the
    # global exception contract. Older focused tests accepted 400; M8 records
    # the public conflict semantic without weakening the double-book assertion.
    if method == "POST" and path == "/api/v1/appointments/hold" and expected == (400,):
        expected = (409,)
    return _original_request(method, path, actor, payload, expected)


matrix.request = contract_request
raise SystemExit(matrix.main())
