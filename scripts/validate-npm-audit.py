#!/usr/bin/env python3
"""Validate npm audit output without globally suppressing high-severity findings.

The only temporary exception is the unpatched `image-size` denial-of-service
pair published in the GitHub Advisory Database. Expo/Metro brings image-size in
transitively as build tooling. The exception is accepted only when:

* the exact two approved GHSA URLs are the only high-severity advisory roots;
* image-size is not a direct dependency;
* application source does not import/require image-size directly; and
* the temporary exception has not expired.

Every other high/critical advisory remains fail-closed.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import pathlib
import re
import sys
from typing import Any

ALLOWED_IMAGE_SIZE_ADVISORIES = {
    "https://github.com/advisories/GHSA-w3rx-r6r6-pgpr",
    "https://github.com/advisories/GHSA-5p2g-fcmc-qvqq",
}
EXCEPTION_EXPIRES = dt.date(2026, 9, 30)
SOURCE_SUFFIXES = {".js", ".jsx", ".mjs", ".cjs", ".ts", ".tsx"}
DIRECT_IMPORT_PATTERN = re.compile(
    r"(?:from\s+['\"]image-size(?:/[^'\"]*)?['\"]|require\(\s*['\"]image-size(?:/[^'\"]*)?['\"]\s*\))"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("audit_json", type=pathlib.Path)
    parser.add_argument("--app-dir", type=pathlib.Path, required=True)
    return parser.parse_args()


def high_or_critical(vulnerability: dict[str, Any]) -> bool:
    return str(vulnerability.get("severity", "")).lower() in {"high", "critical"}


def advisory_url(entry: Any) -> str | None:
    if not isinstance(entry, dict):
        return None
    url = entry.get("url")
    return str(url) if isinstance(url, str) else None


def direct_application_imports(app_dir: pathlib.Path) -> list[str]:
    matches: list[str] = []
    ignored_parts = {"node_modules", ".expo", "coverage", "dist", "build"}
    for path in app_dir.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in SOURCE_SUFFIXES:
            continue
        if any(part in ignored_parts for part in path.parts):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if DIRECT_IMPORT_PATTERN.search(text):
            matches.append(str(path.relative_to(app_dir)))
    return matches


def main() -> int:
    args = parse_args()
    today = dt.datetime.now(dt.timezone.utc).date()
    if today > EXCEPTION_EXPIRES:
        print(
            f"ERROR: temporary image-size audit exception expired on {EXCEPTION_EXPIRES.isoformat()}; "
            "re-evaluate upstream advisories before renewing.",
            file=sys.stderr,
        )
        return 1

    try:
        report = json.loads(args.audit_json.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"ERROR: could not parse npm audit JSON: {exc}", file=sys.stderr)
        return 1

    vulnerabilities = report.get("vulnerabilities")
    if not isinstance(vulnerabilities, dict):
        print("ERROR: npm audit JSON does not contain a vulnerabilities object", file=sys.stderr)
        return 1

    relevant: dict[str, dict[str, Any]] = {
        name: value
        for name, value in vulnerabilities.items()
        if isinstance(value, dict) and high_or_critical(value)
    }
    if not relevant:
        print("npm audit policy passed: no high or critical vulnerabilities.")
        return 0

    image_size = relevant.get("image-size")
    if image_size is None:
        print(
            "ERROR: high/critical npm vulnerabilities exist and are not rooted in the approved image-size exception: "
            + ", ".join(sorted(relevant)),
            file=sys.stderr,
        )
        return 1

    if bool(image_size.get("isDirect")):
        print("ERROR: image-size is a direct dependency; the build-tool-only exception is invalid.", file=sys.stderr)
        return 1

    root_urls = {
        url
        for entry in image_size.get("via", [])
        if (url := advisory_url(entry)) is not None
        and str(entry.get("severity", "")).lower() in {"high", "critical"}
    }
    if not root_urls or not root_urls.issubset(ALLOWED_IMAGE_SIZE_ADVISORIES):
        print(
            "ERROR: image-size has an unapproved high/critical advisory root: "
            + ", ".join(sorted(root_urls or {"<missing advisory URL>"})),
            file=sys.stderr,
        )
        return 1

    source_imports = direct_application_imports(args.app_dir.resolve())
    if source_imports:
        print(
            "ERROR: application source imports image-size directly, so the build-tool-only exception cannot apply: "
            + ", ".join(source_imports),
            file=sys.stderr,
        )
        return 1

    # npm propagates a vulnerable transitive dependency upward through packages
    # such as metro, metro-config and Expo CLI. Accept a propagated package only
    # if every high/critical root object is one of the approved GHSAs and every
    # string dependency points to another package already proven to be in this
    # same image-size-only closure.
    allowed = {"image-size"}
    pending = set(relevant) - allowed
    progress = True
    while pending and progress:
        progress = False
        for name in list(pending):
            via = relevant[name].get("via", [])
            if not isinstance(via, list) or not via:
                continue

            acceptable = True
            for entry in via:
                if isinstance(entry, str):
                    if entry in relevant and entry not in allowed:
                        acceptable = False
                        break
                    continue
                if isinstance(entry, dict):
                    severity = str(entry.get("severity", "")).lower()
                    if severity in {"high", "critical"}:
                        url = advisory_url(entry)
                        if url not in ALLOWED_IMAGE_SIZE_ADVISORIES:
                            acceptable = False
                            break
                    continue
                acceptable = False
                break

            if acceptable:
                allowed.add(name)
                pending.remove(name)
                progress = True

    if pending:
        print(
            "ERROR: high/critical npm vulnerabilities remain outside the approved image-size dependency closure: "
            + ", ".join(sorted(pending)),
            file=sys.stderr,
        )
        return 1

    metadata = report.get("metadata", {})
    counts = metadata.get("vulnerabilities", {}) if isinstance(metadata, dict) else {}
    print(
        "npm audit policy passed with a temporary, build-tool-only image-size exception "
        f"for {', '.join(sorted(root_urls))}; expires {EXCEPTION_EXPIRES.isoformat()}. "
        f"Reported high={counts.get('high', 'unknown')} critical={counts.get('critical', 'unknown')}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
