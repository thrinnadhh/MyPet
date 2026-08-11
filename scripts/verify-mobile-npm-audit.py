#!/usr/bin/env python3
"""Fail-closed npm audit policy for MyPet mobile applications.

The release gate rejects every high/critical npm vulnerability except a narrowly
scoped, time-bounded transitive advisory chain rooted only in `image-size`.
As of 2026-08-09 GitHub lists no patched release for the two allowlisted
`image-size` advisories. The package is consumed through Metro/Expo build tooling
and must not be imported by application source.

npm's audit graph may contain dependency cycles between Metro packages. This
verifier therefore validates all advisory objects first, then proves that every
high/critical package has a cycle-safe path through its `via` edges to the exact
allowlisted `image-size` root. Any other advisory or terminal root fails closed.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import pathlib
import re
import subprocess
import sys
from typing import Any

ALLOWED_ROOT_PACKAGE = "image-size"
ALLOWED_GHSA_IDS = {
    "GHSA-w3rx-r6r6-pgpr",
    "GHSA-5p2g-fcmc-qvqq",
}
KNOWN_BUILD_CHAIN = {
    "metro",
    "metro-config",
    "metro-transform-worker",
    "@expo/metro",
    "@expo/metro-config",
}
WAIVER_EXPIRES = dt.date(2026, 9, 1)
SEVERITY_RANK = {"info": 0, "low": 1, "moderate": 2, "high": 3, "critical": 4}
BLOCK_AT = SEVERITY_RANK["high"]
IMPORT_PATTERN = re.compile(
    r"(?:from\s+['\"]image-size(?:/[^'\"]*)?['\"]|require\(\s*['\"]image-size(?:/[^'\"]*)?['\"]\s*\))"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("app_dir", type=pathlib.Path)
    return parser.parse_args()


def run_audit(app_dir: pathlib.Path) -> dict[str, Any]:
    result = subprocess.run(
        ["npm", "audit", "--json"],
        cwd=app_dir,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if not result.stdout.strip():
        raise RuntimeError(f"npm audit returned no JSON output: {result.stderr.strip()}")
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"npm audit returned invalid JSON: {exc}") from exc


def ghsa_from_url(url: str) -> str | None:
    match = re.search(r"GHSA-[A-Za-z0-9-]+", url)
    return match.group(0) if match else None


def application_imports_image_size(app_dir: pathlib.Path) -> list[str]:
    hits: list[str] = []
    source_root = app_dir / "src"
    if not source_root.exists():
        return hits
    for path in source_root.rglob("*"):
        if path.suffix not in {".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs"} or not path.is_file():
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if IMPORT_PATTERN.search(text):
            hits.append(str(path.relative_to(app_dir)))
    return hits


def advisory_identity(entry: dict[str, Any]) -> tuple[str | None, str | None]:
    return (
        str(entry.get("name")) if entry.get("name") is not None else None,
        ghsa_from_url(str(entry.get("url", ""))),
    )


def advisory_is_allowlisted(entry: dict[str, Any]) -> bool:
    package_name, ghsa = advisory_identity(entry)
    return package_name == ALLOWED_ROOT_PACKAGE and ghsa in ALLOWED_GHSA_IDS


def root_is_exact_allowlist(vulnerability: dict[str, Any]) -> bool:
    via = vulnerability.get("via", [])
    if not isinstance(via, list) or not via:
        return False
    if any(not isinstance(entry, dict) for entry in via):
        return False
    advisories = [entry for entry in via if isinstance(entry, dict)]
    if not all(advisory_is_allowlisted(entry) for entry in advisories):
        return False
    return {advisory_identity(entry)[1] for entry in advisories} == ALLOWED_GHSA_IDS


def every_advisory_object_is_allowlisted(vulnerabilities: dict[str, dict[str, Any]]) -> bool:
    for vulnerability in vulnerabilities.values():
        via = vulnerability.get("via", [])
        if not isinstance(via, list):
            return False
        for entry in via:
            if isinstance(entry, dict) and not advisory_is_allowlisted(entry):
                return False
            if not isinstance(entry, (dict, str)):
                return False
    return True


def reaches_allowlisted_root(
    name: str,
    vulnerabilities: dict[str, dict[str, Any]],
    visited: set[str],
) -> bool:
    if name == ALLOWED_ROOT_PACKAGE:
        root = vulnerabilities.get(name)
        return bool(root and root_is_exact_allowlist(root))
    if name in visited:
        return False

    vulnerability = vulnerabilities.get(name)
    if not vulnerability:
        return False
    via = vulnerability.get("via", [])
    if not isinstance(via, list) or not via:
        return False

    next_visited = {*visited, name}
    for entry in via:
        if isinstance(entry, dict):
            if advisory_is_allowlisted(entry):
                return True
            continue
        if isinstance(entry, str) and reaches_allowlisted_root(entry, vulnerabilities, next_visited):
            return True
    return False


def main() -> int:
    args = parse_args()
    app_dir = args.app_dir.resolve()
    today = dt.datetime.now(dt.timezone.utc).date()

    audit = run_audit(app_dir)
    vulnerabilities = audit.get("vulnerabilities", {})
    if not isinstance(vulnerabilities, dict):
        raise RuntimeError("Unsupported npm audit JSON: vulnerabilities object missing")

    blocking = {
        name: details
        for name, details in vulnerabilities.items()
        if SEVERITY_RANK.get(str(details.get("severity", "")).lower(), 99) >= BLOCK_AT
    }
    if not blocking:
        print("PASS: npm audit contains no high or critical vulnerabilities.")
        return 0

    if today > WAIVER_EXPIRES:
        print(
            f"FAIL: temporary image-size waiver expired on {WAIVER_EXPIRES.isoformat()}; "
            "upgrade/remove the vulnerable build dependency or renew through explicit security review.",
            file=sys.stderr,
        )
        return 1

    source_hits = application_imports_image_size(app_dir)
    if source_hits:
        print(
            "FAIL: image-size is imported by application source and cannot use the build-tool waiver: "
            + ", ".join(source_hits),
            file=sys.stderr,
        )
        return 1

    blocking_graph = {name: details for name, details in blocking.items()}
    root = blocking_graph.get(ALLOWED_ROOT_PACKAGE)
    if not root or not root_is_exact_allowlist(root):
        print("FAIL: exact image-size advisory root is absent or has changed.", file=sys.stderr)
        return 1

    if not every_advisory_object_is_allowlisted(blocking_graph):
        print("FAIL: a high/critical advisory object is outside the exact image-size allowlist.", file=sys.stderr)
        return 1

    disallowed = [
        name
        for name in blocking_graph
        if not reaches_allowlisted_root(name, blocking_graph, set())
    ]
    if disallowed:
        print(
            "FAIL: high/critical npm vulnerabilities do not resolve exclusively to the allowlisted root: "
            + ", ".join(sorted(disallowed)),
            file=sys.stderr,
        )
        return 1

    effects = set(root.get("effects", []))
    if not effects.intersection(KNOWN_BUILD_CHAIN):
        print(
            "FAIL: image-size advisory is no longer connected to the expected Metro build-tool chain.",
            file=sys.stderr,
        )
        return 1

    print(
        "PASS WITH TEMPORARY SECURITY EXCEPTION: every high/critical npm node reaches only "
        f"{ALLOWED_ROOT_PACKAGE} advisories {sorted(ALLOWED_GHSA_IDS)} through Metro build tooling; "
        f"no app-source import was found; waiver expires {WAIVER_EXPIRES.isoformat()}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
