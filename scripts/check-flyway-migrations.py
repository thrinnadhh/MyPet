#!/usr/bin/env python3
"""Fail when a backend service contains malformed or duplicate Flyway versions."""

from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MIGRATION_ROOTS = sorted(
    (ROOT / "backend").glob("*/src/main/resources/db/migration")
)
MIGRATION_NAME = re.compile(r"^V(?P<version>[^_]+)__(?P<description>.+)\.sql$")

failures: list[str] = []
checked = 0

for directory in MIGRATION_ROOTS:
    versions: dict[str, list[str]] = defaultdict(list)

    for migration in sorted(directory.glob("*.sql")):
        match = MIGRATION_NAME.match(migration.name)
        if not match:
            failures.append(f"malformed migration name: {migration.relative_to(ROOT)}")
            continue

        checked += 1
        versions[match.group("version")].append(migration.name)

    for version, files in sorted(versions.items()):
        if len(files) > 1:
            service = directory.parents[4].name
            failures.append(
                f"{service} has duplicate Flyway version V{version}: {', '.join(files)}"
            )

if failures:
    for failure in failures:
        print(f"ERROR: {failure}", file=sys.stderr)
    raise SystemExit(1)

print(
    f"Flyway migration versions are unique across "
    f"{len(MIGRATION_ROOTS)} services ({checked} migrations checked)."
)
