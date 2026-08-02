#!/usr/bin/env python3
"""Validate the P2B accessibility and physical-device QA evidence matrix."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ALLOWED_STATUSES = {"NOT_RUN", "PASS", "FAIL", "BLOCKED"}
REQUIRED_CHECKS = {
    "layout-320",
    "font-200",
    "screen-reader",
    "touch-targets",
    "keyboard",
    "reduced-motion",
    "deep-links",
    "push",
    "location-grant",
    "location-deny",
    "gps-disabled",
    "background-location",
    "offline-restart",
    "payment-sandbox",
    "medical-upload",
}
REQUIRED_DEVICES = {"android-small", "android-modern", "ios-modern"}


def fail(message: str) -> None:
    print(f"DEVICE_QA_INVALID: {message}", file=sys.stderr)
    raise SystemExit(1)


def validate(path: Path, release_gate: bool) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schemaVersion") != 1:
        fail("schemaVersion must be 1")
    if data.get("release") != "v0.9.0-beta.1":
        fail("release must be v0.9.0-beta.1")

    devices = data.get("devices")
    checks = data.get("checks")
    blockers = data.get("blockers")
    if not isinstance(devices, list) or not isinstance(checks, list) or not isinstance(blockers, list):
        fail("devices, checks and blockers must be arrays")

    device_ids = {device.get("id") for device in devices}
    if device_ids != REQUIRED_DEVICES:
        fail(f"device IDs must be exactly {sorted(REQUIRED_DEVICES)}")
    for device in devices:
        if device.get("physicalDeviceRequired") is not True:
            fail(f"{device.get('id')} must require physical-device evidence")

    check_ids = [check.get("id") for check in checks]
    if len(check_ids) != len(set(check_ids)):
        fail("check IDs must be unique")
    if set(check_ids) != REQUIRED_CHECKS:
        missing = REQUIRED_CHECKS.difference(check_ids)
        extra = set(check_ids).difference(REQUIRED_CHECKS)
        fail(f"check set mismatch; missing={sorted(missing)} extra={sorted(extra)}")

    for check in checks:
        status = check.get("status")
        required_on = check.get("requiredOn")
        if status not in ALLOWED_STATUSES:
            fail(f"{check.get('id')} has invalid status {status}")
        if not isinstance(required_on, list) or not required_on:
            fail(f"{check.get('id')} must name at least one device")
        if not set(required_on).issubset(REQUIRED_DEVICES):
            fail(f"{check.get('id')} references an unknown device")
        if status == "PASS" and not check.get("evidence"):
            fail(f"{check.get('id')} cannot pass without evidence")
        if status in {"FAIL", "BLOCKED"} and not check.get("notes"):
            fail(f"{check.get('id')} requires notes for status {status}")

    if release_gate:
        non_pass = [check["id"] for check in checks if check.get("status") != "PASS"]
        if non_pass:
            fail(f"release requires PASS for every check: {', '.join(non_pass)}")
        if blockers:
            fail("release requires an empty blockers list")
        if not data.get("updatedAt") or not data.get("executedBy"):
            fail("release requires updatedAt and executedBy")

    mode = "release" if release_gate else "structure"
    print(f"DEVICE_QA_VALID mode={mode} checks={len(checks)} devices={len(devices)}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--matrix", default="qa/p2b-device-qa-matrix.json")
    parser.add_argument("--release", action="store_true")
    args = parser.parse_args()
    validate(Path(args.matrix), args.release)


if __name__ == "__main__":
    main()
