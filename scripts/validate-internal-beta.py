#!/usr/bin/env python3
"""Validate MyPet v0.9.0-beta.1 configuration and optional release authorization."""

from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = "v0.9.0-beta.1"
APP_VERSION = "0.9.0-beta.1"


def fail(message: str) -> None:
    raise SystemExit(f"INTERNAL_BETA_BLOCKED: {message}")


def read_json(relative: str) -> dict:
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def validate_config() -> dict:
    release = read_json("release/v0.9.0-beta.1.json")
    device = read_json("qa/p2b-device-qa-matrix.json")
    journeys = read_json("qa/p2b-connected-journeys.json")
    customer = read_json("apps/customer-app/app.json")["expo"]
    operations = read_json("apps/merchant-captain-app/app.json")["expo"]
    customer_eas = read_json("apps/customer-app/eas.json")
    operations_eas = read_json("apps/merchant-captain-app/eas.json")

    if release.get("schemaVersion") != 1 or release.get("version") != VERSION:
        fail("release manifest version mismatch")
    if release.get("releaseType") != "INTERNAL_BETA" or release.get("pilotCity") != "Tirupati":
        fail("release must remain the Tirupati internal beta")
    if customer.get("version") != APP_VERSION or operations.get("version") != APP_VERSION:
        fail("both Expo applications must use 0.9.0-beta.1")
    if customer.get("android", {}).get("versionCode") != 901 or operations.get("android", {}).get("versionCode") != 901:
        fail("both Android applications must use versionCode 901")
    if customer.get("ios", {}).get("buildNumber") != "1" or operations.get("ios", {}).get("buildNumber") != "1":
        fail("both iOS applications must use buildNumber 1")
    for name, eas in (("customer", customer_eas), ("operations", operations_eas)):
        profile = eas.get("build", {}).get("internal-beta")
        if not profile or profile.get("distribution") != "internal":
            fail(f"{name} internal-beta EAS profile is missing")
        env = profile.get("env", {})
        if env.get("EXPO_PUBLIC_ALLOW_DEMO_MODE") != "false":
            fail(f"{name} beta build must disable demo mode")
        if env.get("EXPO_PUBLIC_RELEASE_VERSION") != VERSION:
            fail(f"{name} beta build version environment mismatch")
    if journeys.get("release") != VERSION or len(journeys.get("journeys", [])) != 10:
        fail("the exact ten connected journeys are not configured")
    if device.get("release") != VERSION:
        fail("device QA matrix release mismatch")

    return {"release": release, "device": device}


def validate_release() -> None:
    state = validate_config()
    release = state["release"]
    if release.get("tagAuthorized") is not True or release.get("distributionAuthorized") is not True:
        fail("tag and distribution authorization are still false")
    blockers = release.get("blockers", [])
    if blockers:
        fail(f"release blockers remain: {blockers}")
    artifacts = release.get("artifacts", [])
    if not artifacts or any(item.get("status") != "BUILT" or not item.get("url") for item in artifacts):
        fail("all internal beta artifacts require BUILT status and an evidence URL")
    subprocess.run(
        ["python", str(ROOT / "scripts/validate-p2b-device-qa.py"), "--release"],
        check=True,
    )
    subprocess.run(
        ["python", str(ROOT / "scripts/check-p2b-connected-e2e.py")],
        check=True,
    )
    print("INTERNAL_BETA_RELEASE_AUTHORIZED version=v0.9.0-beta.1")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--release", action="store_true")
    args = parser.parse_args()
    if args.release:
        validate_release()
    else:
        validate_config()
        print("INTERNAL_BETA_CONFIG_VALID version=v0.9.0-beta.1 authorization=blocked")


if __name__ == "__main__":
    main()
