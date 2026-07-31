#!/usr/bin/env python3
from __future__ import annotations
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []

def fail(message: str) -> None:
    failures.append(message)
    print(f"FAIL: {message}")

def check_http(name: str, url: str, expected: set[int], headers: dict[str, str] | None = None) -> None:
    try:
        with urlopen(Request(url, headers=headers or {}), timeout=5) as response:
            status = response.status
    except HTTPError as error:
        status = error.code
    except (URLError, TimeoutError, OSError) as error:
        fail(f"{name} is unavailable: {error}")
        return
    if status >= 500 or status not in expected:
        fail(f"{name} returned HTTP {status}; expected {sorted(expected)} and never 5xx")

for client in [
    ROOT / "apps/customer-app/src/services/api-client.ts",
    ROOT / "apps/merchant-captain-app/src/services/api-client.ts",
]:
    source = client.read_text(encoding="utf-8")
    if "X-Internal-Gateway-Secret" in source or "setGatewaySecret" in source:
        fail(f"internal gateway credential remains in {client.relative_to(ROOT)}")

manifest = ROOT / "infra/k8s/backend-services.yaml"
if manifest.exists():
    value = manifest.read_text(encoding="utf-8")
    for marker in ["your-org", ":latest", "REQUIRED_IMAGE_DIGEST"]:
        if marker in value:
            fail(f"deployment manifest contains unresolved marker: {marker}")

versions = set()
for gradle in (ROOT / "backend").rglob("build.gradle.kts"):
    versions.update(re.findall(r"spring-boot-dependencies:([0-9.]+)", gradle.read_text(encoding="utf-8")))
if len(versions) > 1:
    fail(f"multiple Spring Boot BOM versions found: {sorted(versions)}")

if os.getenv("RUN_LIVE_SMOKE_TESTS") == "true":
    base = os.environ["STAGING_GATEWAY_URL"].rstrip("/")
    check_http("gateway readiness", f"{base}/actuator/health/readiness", {200})
    check_http("unauthenticated admin rejection", f"{base}/api/v1/providers/pending", {401, 403})

report = {"passed": not failures, "failures": failures}
(ROOT / "backend/release-gate-report.json").write_text(json.dumps(report, indent=2) + "\n")
if failures:
    sys.exit(1)
print("All configured release gates passed.")
