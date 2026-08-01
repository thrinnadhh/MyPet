#!/usr/bin/env python3
"""Enforce gateway trust on every order-service module HTTP call."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG = (
    ROOT
    / "backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/config/InfraConfig.kt"
)
TEST = (
    ROOT
    / "backend/order-service/src/test/kotlin/com/pawsnearme/orderservice/config/InfraConfigTests.kt"
)

failures: list[str] = []

if not CONFIG.is_file():
    failures.append("order-service is missing InfraConfig.kt")
else:
    content = CONFIG.read_text(encoding="utf-8")
    for required in (
        '@Value("\\${gateway.trust.secret:}")',
        'request.headers.set("X-Internal-Gateway-Secret", gatewayTrustSecret)',
        "if (gatewayTrustSecret.isNotBlank())",
        "interceptors.add",
        "setConnectTimeout(3_000)",
        "setReadTimeout(10_000)",
    ):
        if required not in content:
            failures.append(f"order-service outbound trust configuration is missing: {required}")

if not TEST.is_file():
    failures.append("order-service is missing InfraConfigTests.kt")
else:
    tests = TEST.read_text(encoding="utf-8")
    for required in (
        "X-Internal-Gateway-Secret",
        "m8-gateway-secret",
        "blank gateway secret does not inject an empty trust header",
    ):
        if required not in tests:
            failures.append(f"order-service outbound trust test is missing: {required}")

if failures:
    for failure in failures:
        print(f"ERROR: {failure}", file=sys.stderr)
    raise SystemExit(1)

print("Order-service outbound module gateway trust wiring is enforced.")
