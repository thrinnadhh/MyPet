#!/usr/bin/env python3
"""Sprint 8 read-only load smoke for discovery, orders, appointments, and billing.

This is intentionally dependency-free so it can run on any developer machine or CI
runner once local services are up. It exercises read paths only; destructive writes
belong in focused flow verifiers.
"""

from __future__ import annotations

import argparse
import json
import os
import statistics
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from typing import Iterable
from urllib.parse import urlencode


DEFAULT_CUSTOMER_ID = "11111111-1111-4111-8111-111111111401"
DEFAULT_PROVIDER_ID = "11111111-1111-4111-8111-111111111201"
DEFAULT_STORE_ID = "11111111-1111-4111-8111-111111111201"
DEFAULT_ADMIN_ID = "99999999-9999-4999-8999-999999999999"


@dataclass(frozen=True)
class Target:
    name: str
    url: str
    headers: dict[str, str]


@dataclass(frozen=True)
class Result:
    name: str
    ok: bool
    status: int
    elapsed_ms: float
    error: str | None = None


def env_url(name: str, fallback: str) -> str:
    return os.environ.get(name, fallback).rstrip("/")


def build_targets(args: argparse.Namespace) -> list[Target]:
    gateway = args.base_url.rstrip("/")
    discovery_url = env_url("DISCOVERY_BASE_URL", gateway)
    order_url = env_url("ORDER_BASE_URL", gateway)
    appointment_url = env_url("APPOINTMENT_BASE_URL", gateway)
    catalog_url = env_url("CATALOG_BASE_URL", gateway)

    discovery_query = urlencode({
        "longitude": args.longitude,
        "latitude": args.latitude,
        "radius": args.radius_km,
    })

    admin_headers = {
        "X-User-Role": "ADMIN",
        "X-User-Id": args.admin_user_id,
    }

    return [
        Target("discovery", f"{discovery_url}/api/v1/discovery/providers?{discovery_query}", {}),
        Target("orders", f"{order_url}/api/v1/orders/customer/{args.customer_id}", {}),
        Target("appointments", f"{appointment_url}/api/v1/appointments/customer/{args.customer_id}", {}),
        Target("billing", f"{catalog_url}/api/v1/catalog/bills?storeId={args.store_id}", admin_headers),
    ]


def fetch(target: Target, timeout: float) -> Result:
    request = urllib.request.Request(target.url, headers=target.headers)
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            response.read()
            elapsed_ms = (time.perf_counter() - started) * 1000
            return Result(target.name, 200 <= response.status < 300, response.status, elapsed_ms)
    except urllib.error.HTTPError as exc:
        elapsed_ms = (time.perf_counter() - started) * 1000
        body = exc.read().decode("utf-8", errors="replace")[:240]
        return Result(target.name, False, exc.code, elapsed_ms, body)
    except Exception as exc:
        elapsed_ms = (time.perf_counter() - started) * 1000
        return Result(target.name, False, 0, elapsed_ms, str(exc))


def percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, int(round((pct / 100) * (len(ordered) - 1))))
    return ordered[index]


def summarize(results: Iterable[Result]) -> tuple[bool, dict[str, dict[str, float | int]]]:
    by_name: dict[str, list[Result]] = {}
    for result in results:
        by_name.setdefault(result.name, []).append(result)

    passed = True
    summary: dict[str, dict[str, float | int]] = {}
    for name, rows in sorted(by_name.items()):
        latencies = [row.elapsed_ms for row in rows]
        failures = [row for row in rows if not row.ok]
        if failures:
            passed = False
        summary[name] = {
            "requests": len(rows),
            "failures": len(failures),
            "avg_ms": round(statistics.mean(latencies), 2),
            "p95_ms": round(percentile(latencies, 95), 2),
            "max_ms": round(max(latencies), 2),
        }
    return passed, summary


def main() -> int:
    parser = argparse.ArgumentParser(description="Sprint 8 read-only load smoke")
    parser.add_argument("--base-url", default=os.environ.get("API_BASE_URL", "http://localhost:8080"))
    parser.add_argument("--requests", type=int, default=int(os.environ.get("SPRINT8_LOAD_REQUESTS", "8")))
    parser.add_argument("--concurrency", type=int, default=int(os.environ.get("SPRINT8_LOAD_CONCURRENCY", "4")))
    parser.add_argument("--timeout", type=float, default=float(os.environ.get("SPRINT8_LOAD_TIMEOUT", "5")))
    parser.add_argument("--customer-id", default=os.environ.get("SPRINT8_CUSTOMER_ID", DEFAULT_CUSTOMER_ID))
    parser.add_argument("--provider-id", default=os.environ.get("SPRINT8_PROVIDER_ID", DEFAULT_PROVIDER_ID))
    parser.add_argument("--store-id", default=os.environ.get("SPRINT8_STORE_ID", DEFAULT_STORE_ID))
    parser.add_argument("--admin-user-id", default=os.environ.get("SPRINT8_ADMIN_USER_ID", DEFAULT_ADMIN_ID))
    parser.add_argument("--longitude", type=float, default=float(os.environ.get("SPRINT8_LONGITUDE", "77.5946")))
    parser.add_argument("--latitude", type=float, default=float(os.environ.get("SPRINT8_LATITUDE", "12.9716")))
    parser.add_argument("--radius-km", type=float, default=float(os.environ.get("SPRINT8_RADIUS_KM", "5.0")))
    args = parser.parse_args()

    targets = build_targets(args)
    scheduled = [target for target in targets for _ in range(args.requests)]
    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        futures = [executor.submit(fetch, target, args.timeout) for target in scheduled]
        results = [future.result() for future in as_completed(futures)]

    passed, summary = summarize(results)
    payload = {
        "ok": passed,
        "duration_ms": round((time.perf_counter() - started) * 1000, 2),
        "targets": summary,
    }
    print(json.dumps(payload, indent=2, sort_keys=True))

    if not passed:
        for result in results:
            if not result.ok:
                print(f"FAIL {result.name} status={result.status} error={result.error}")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
