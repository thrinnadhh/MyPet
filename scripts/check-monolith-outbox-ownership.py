#!/usr/bin/env python3
"""Fail when a durable outbox aggregate has no explicit monolith owner."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "backend/mypet-application/src/main/kotlin/com/pawsnearme/application/runtime/SchemaQualifiedOutboxPersistence.kt"

text = REGISTRY.read_text(encoding="utf-8")

schemas_block = re.search(r"val schemas: List<String> = listOf\((.*?)\n\s*\)", text, re.DOTALL)
if schemas_block is None:
    raise SystemExit("ERROR: could not parse the modular-monolith outbox schema allowlist")

schemas = set(re.findall(r'"([a-z_]+)"', schemas_block.group(1)))
expected_schemas = {
    "orders",
    "appointments",
    "providers",
    "catalog",
    "dispatch",
    "reviews",
    "payments",
}
if schemas != expected_schemas:
    missing = sorted(expected_schemas - schemas)
    unexpected = sorted(schemas - expected_schemas)
    raise SystemExit(
        "ERROR: monolith outbox schema allowlist drifted; "
        f"missing={missing} unexpected={unexpected}",
    )

owners_block = re.search(r"private val owners: Map<String, String> = mapOf\((.*?)\n\s*\)", text, re.DOTALL)
if owners_block is None:
    raise SystemExit("ERROR: could not parse the modular-monolith aggregate owner registry")

owners = dict(re.findall(r'"([A-Z_]+)"\s+to\s+"([a-z_]+)"', owners_block.group(1)))
if not owners:
    raise SystemExit("ERROR: modular-monolith aggregate owner registry is empty")

unknown_owner_schemas = sorted(set(owners.values()) - schemas)
if unknown_owner_schemas:
    raise SystemExit(
        "ERROR: aggregate owners reference schemas outside the allowlist: "
        + ", ".join(unknown_owner_schemas),
    )

used_by_type: dict[str, set[str]] = {}
for source in sorted((ROOT / "backend").glob("*-service/src/main/kotlin/**/*.kt")):
    source_text = source.read_text(encoding="utf-8")
    for aggregate_type in re.findall(r'aggregateType\s*=\s*"([A-Z_]+)"', source_text):
        used_by_type.setdefault(aggregate_type, set()).add(source.relative_to(ROOT).as_posix())

if not used_by_type:
    raise SystemExit("ERROR: no literal production outbox aggregate types were discovered")

missing_owners = sorted(set(used_by_type) - set(owners))
if missing_owners:
    details = "; ".join(
        f"{aggregate_type}: {', '.join(sorted(used_by_type[aggregate_type]))}"
        for aggregate_type in missing_owners
    )
    raise SystemExit(
        "ERROR: production outbox aggregate types have no modular-monolith owner: " + details,
    )

print(
    "Monolith outbox ownership passed: "
    f"{len(used_by_type)} production aggregate types map to {len(schemas)} schemas.",
)
