#!/usr/bin/env python3
"""Fail-closed npm audit policy for MyPet mobile applications.

The release gate rejects every high/critical npm vulnerability except a narrowly
scoped, time-bounded transitive advisory chain rooted only in `image-size`.
As of 2026-08-09 GitHub lists no patched release for the two allowlisted
`image-size` advisories. The package is consumed through Metro/Expo build tooling
and must not be imported by application source.

npm may propagate the root advisory object through every affected parent package,
so the verifier accepts propagated metadata only when every advisory object still
names `image-size` and carries one of the two exact GHSA identifiers. Any other
advisory or dependency root fails closed.
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


def direct_advisories_are_allowlisted(entries: list[dict[str, Any]]) -> bool:
    for entry in entries:
        package_name, ghsa = advisory_identity(entry)
        if package_name != ALLOWED_ROOT_PACKAGE or ghsa not in ALLOWED_GHSA_IDS:
            return False
    return True


def root_is_exact_allowlist(vulnerability: dict[str, Any]) -> bool:
    via = vulnerability.get("via", [])
    if not isinstance(via, list) or not via:
        return False
    if any(isinstance(entry, str) for entry in via):
        return False
    advisories = [entry for entry in via if isinstance(entry, dict)]
    if len(advisories) != len(via) or not direct_advisories_are_allowlisted(advisories):
        return False
    return {advisory_identity(entry)[1] for entry in advisories} == ALLOWED_GHSA_IDS


def vulnerability_allowed(
    name: str,
    vulnerabilities: dict[str, dict[str, Any]],
    memo: dict[str, bool],
    visiting: set[str],
) -> bool:
    if name in memo:
        return memo[name]
    if name in visiting:
        memo[name] = False
        return False
    vulnerability = vulnerabilities.get(name)
    if not vulnerability:
        memo[name] = False
        return False

    visiting.add(name)
    via = vulnerability.get("via", [])
    if not isinstance(via, list) or not via:
        visiting.remove(name)
        memo[name] = False
        return False

    direct_advisories = [entry for entry in via if isinstance(entry, dict)]
    dependency_edges = [entry for entry in via if isinstance(entry, str)]
    unexpected = [entry for entry in via if not isinstance(entry, (dict, str))]

    if name == ALLOWED_ROOT_PACKAGE:
        allowed = root_is_exact_allowlist(vulnerability)
    else:
        direct_ok = direct_advisories_are_allowlisted(direct_advisories)
        dependencies_ok = all(
            vulnerability_allowed(edge, vulnerabilities, memo, visiting)
            for edge in dependency_edges
        )
        allowed = not unexpected and direct_ok and dependencies_ok and bool(direct_advisories or dependency_edges)

    visiting.remove(name)
    memo[name] = allowed
    return allowed


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

    memo: dict[str, bool] = {}
    disallowed = [
        name
        for name in blocking
        if not vulnerability_allowed(name, vulnerabilities, memo, set())
    ]
    if disallowed:
        print(
            "FAIL: high/critical npm vulnerabilities are outside the exact temporary allowlist: "
            + ", ".join(sorted(disallowed)),
            file=sys.stderr,
        )
        return 1

    root = vulnerabilities.get(ALLOWED_ROOT_PACKAGE, {})
    effects = set(root.get("effects", []))
    if not effects.intersection(KNOWN_BUILD_CHAIN):
        print(
            "FAIL: image-size advisory is no longer connected to the expected Metro build-tool chain.",
            file=sys.stderr,
        )
        return 1

    print(
        "PASS WITH TEMPORARY SECURITY EXCEPTION: all high/critical findings resolve only to "
        f"{ALLOWED_ROOT_PACKAGE} advisories {sorted(ALLOWED_GHSA_IDS)} through Metro build tooling; "
        f"no app-source import was found; waiver expires {WAIVER_EXPIRES.isoformat()}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
