#!/usr/bin/env python3
"""Validate entity names and JPQL references for the shared persistence unit."""

from __future__ import annotations

import re
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend"

ENTITY_ANNOTATION = re.compile(
    r"^\s*@Entity(?:\s*\(\s*(?:name\s*=\s*)?\"([^\"]+)\"\s*\))?\s*$"
)
CLASS_DECLARATION = re.compile(
    r"^\s*(?:(?:public|internal|private|protected|open|abstract|sealed|data|enum|value|annotation)\s+)*class\s+([A-Za-z_][A-Za-z0-9_]*)\b"
)
PACKAGE_DECLARATION = re.compile(r"^\s*package\s+([A-Za-z0-9_.]+)\s*$")


@dataclass(frozen=True)
class EntityDefinition:
    entity_name: str
    class_name: str
    qualified_class_name: str
    path: Path
    line: int


def scan_file(path: Path) -> list[EntityDefinition]:
    lines = path.read_text(encoding="utf-8").splitlines()
    package_name = ""
    for line in lines:
        match = PACKAGE_DECLARATION.match(line)
        if match:
            package_name = match.group(1)
            break

    definitions: list[EntityDefinition] = []
    pending_entity_name: str | None = None
    pending_line = 0

    for index, line in enumerate(lines, start=1):
        entity_match = ENTITY_ANNOTATION.match(line)
        if entity_match:
            pending_entity_name = entity_match.group(1) or ""
            pending_line = index
            continue

        if pending_entity_name is None:
            continue

        stripped = line.strip()
        if not stripped or stripped.startswith("//") or stripped.startswith("/*") or stripped.startswith("*"):
            continue
        if stripped.startswith("@"):
            continue

        class_match = CLASS_DECLARATION.match(line)
        if class_match:
            class_name = class_match.group(1)
            entity_name = pending_entity_name or class_name
            qualified = f"{package_name}.{class_name}" if package_name else class_name
            definitions.append(
                EntityDefinition(entity_name, class_name, qualified, path, pending_line)
            )
        pending_entity_name = None
        pending_line = 0

    return definitions


def stale_jpql_references(
    source_files: list[Path],
    renamed_entities: list[EntityDefinition],
) -> list[tuple[EntityDefinition, Path, int, str]]:
    stale: list[tuple[EntityDefinition, Path, int, str]] = []
    for entity in renamed_entities:
        pattern = re.compile(
            rf"\b(?:FROM|JOIN|UPDATE|DELETE\s+FROM)\s+{re.escape(entity.class_name)}\b",
            re.IGNORECASE,
        )
        for path in source_files:
            text = path.read_text(encoding="utf-8")
            for match in pattern.finditer(text):
                line = text.count("\n", 0, match.start()) + 1
                excerpt = " ".join(match.group(0).split())
                stale.append((entity, path, line, excerpt))
    return stale


def main() -> int:
    source_files = sorted(
        path
        for path in BACKEND.glob("*/src/main/kotlin/**/*.kt")
        if path.is_file()
    )
    definitions = [definition for path in source_files for definition in scan_file(path)]

    by_name: dict[str, list[EntityDefinition]] = defaultdict(list)
    for definition in definitions:
        by_name[definition.entity_name].append(definition)

    duplicates = {
        name: entries
        for name, entries in sorted(by_name.items())
        if len({entry.qualified_class_name for entry in entries}) > 1
    }

    renamed = [definition for definition in definitions if definition.entity_name != definition.class_name]
    stale_queries = stale_jpql_references(source_files, renamed)

    failed = False
    if duplicates:
        failed = True
        print(
            "ERROR: duplicate JPA entity names are incompatible with the shared "
            "modular-monolith persistence unit:",
            file=sys.stderr,
        )
        for name, entries in duplicates.items():
            print(f"  {name!r}:", file=sys.stderr)
            for entry in entries:
                relative = entry.path.relative_to(ROOT)
                print(
                    f"    - {entry.qualified_class_name} ({relative}:{entry.line})",
                    file=sys.stderr,
                )

    if stale_queries:
        failed = True
        print(
            "ERROR: JPQL still references a Kotlin class name after its JPA entity "
            "name was explicitly qualified:",
            file=sys.stderr,
        )
        for entity, path, line, excerpt in stale_queries:
            print(
                f"  - {path.relative_to(ROOT)}:{line}: {excerpt!r}; use "
                f"{entity.entity_name!r} instead of {entity.class_name!r}",
                file=sys.stderr,
            )

    if failed:
        print(
            "Assign unique module-qualified @Entity names and update every JPQL "
            "FROM/JOIN/UPDATE reference to the effective entity name.",
            file=sys.stderr,
        )
        return 1

    print(
        f"JPA persistence-unit contract passed: {len(definitions)} entities have "
        f"{len(by_name)} unique names and {len(renamed)} explicit-name JPQL mappings are current."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
