#!/usr/bin/env python3
"""Create a service-level Markdown summary from Gradle JUnit XML results."""

from __future__ import annotations

import argparse
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        type=Path,
        default=ROOT / "build" / "reports" / "backend-test-summary.md",
    )
    args = parser.parse_args()

    rows: list[tuple[str, int, int, int, int, str]] = []
    for service in sorted((ROOT / "backend").iterdir()):
        if not service.is_dir():
            continue
        result_dir = service / "build" / "test-results" / "test"
        suites = list(result_dir.glob("TEST-*.xml"))
        if not suites:
            continue

        tests = failures = errors = skipped = 0
        for suite in suites:
            root = ET.parse(suite).getroot()
            tests += int(root.attrib.get("tests", 0))
            failures += int(root.attrib.get("failures", 0))
            errors += int(root.attrib.get("errors", 0))
            skipped += int(root.attrib.get("skipped", 0))

        status = "PASS" if failures == 0 and errors == 0 else "FAIL"
        rows.append((service.name, tests, failures, errors, skipped, status))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    total_tests = sum(row[1] for row in rows)
    total_failures = sum(row[2] + row[3] for row in rows)

    lines = [
        "# Backend automated-test summary",
        "",
        f"Total tests: **{total_tests}**  ",
        f"Total failures/errors: **{total_failures}**",
        "",
        "| Service | Tests | Failures | Errors | Skipped | Result |",
        "|---|---:|---:|---:|---:|---|",
    ]
    lines.extend(
        f"| {service} | {tests} | {failures} | {errors} | {skipped} | {status} |"
        for service, tests, failures, errors, skipped, status in rows
    )
    lines.append("")

    args.output.write_text("\n".join(lines), encoding="utf-8")
    print(args.output)


if __name__ == "__main__":
    main()
