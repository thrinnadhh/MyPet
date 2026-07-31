#!/usr/bin/env python3
"""Fail when a backend service contains malformed or duplicate Flyway migrations."""

from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MIGRATION_ROOTS = sorted(
    (ROOT / "backend").glob("*/src/main/resources/db/migration")
)
VERSIONED_NAME = re.compile(r"^V(?P<version>[^_]+)__(?P<description>.+)\.sql$")
REPEATABLE_NAME = re.compile(r"^R__(?P<description>.+)\.sql$")

failures: list[str] = []
versioned_checked = 0
repeatable_checked = 0

for directory in MIGRATION_ROOTS:
    versions: dict[str, list[str]] = defaultdict(list)
    repeatable_descriptions: dict[str, list[str]] = defaultdict(list)

    for migration in sorted(directory.glob("*.sql")):
        versioned_match = VERSIONED_NAME.match(migration.name)
        if versioned_match:
            versioned_checked += 1
            versions[versioned_match.group("version")].append(migration.name)
            continue

        repeatable_match = REPEATABLE_NAME.match(migration.name)
        if repeatable_match:
            repeatable_checked += 1
            repeatable_descriptions[repeatable_match.group("description")].append(
                migration.name
            )
            continue

        failures.append(f"malformed migration name: {migration.relative_to(ROOT)}")

    service = directory.parents[4].name
    for version, files in sorted(versions.items()):
        if len(files) > 1:
            failures.append(
                f"{service} has duplicate Flyway version V{version}: {', '.join(files)}"
            )

    for description, files in sorted(repeatable_descriptions.items()):
        if len(files) > 1:
            failures.append(
                f"{service} has duplicate repeatable migration R__{description}: "
                f"{', '.join(files)}"
            )

if failures:
    for failure in failures:
        print(f"ERROR: {failure}", file=sys.stderr)
    raise SystemExit(1)

print(
    "Flyway migrations are well formed and unique across "
    f"{len(MIGRATION_ROOTS)} services "
    f"({versioned_checked} versioned, {repeatable_checked} repeatable)."
)
