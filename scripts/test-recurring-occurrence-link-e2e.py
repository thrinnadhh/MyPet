#!/usr/bin/env python3
"""Verify the generated recurring order is durably linked to its occurrence."""

from __future__ import annotations

import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROJECT_NAME = os.environ.get("COMPOSE_PROJECT_NAME", "mypet-e2e")
ENV_FILE = os.environ.get("MYPET_ENV_FILE")
if not ENV_FILE:
    raise SystemExit("MYPET_ENV_FILE must be set by the stack runner")

compose_files_raw = os.environ.get("MYPET_COMPOSE_FILES", "")
if compose_files_raw:
    compose_files = [Path(item) for item in compose_files_raw.split(",") if item]
else:
    compose_files = [
        ROOT / "infra/docker-compose.yml",
        ROOT / "infra/docker-compose.replicas.yml",
        ROOT / "infra/docker-compose.m4.yml",
        ROOT / "infra/docker-compose.local.yml",
    ]

COMPOSE = ["docker", "compose", "-p", PROJECT_NAME, "--env-file", ENV_FILE]
for compose_file in compose_files:
    COMPOSE.extend(("-f", str(compose_file)))


def sql(statement: str) -> str:
    result = subprocess.run(
        [*COMPOSE, "exec", "-T", "postgres", "psql", "-U", "postgres", "-d", "pawsnearme", "-v", "ON_ERROR_STOP=1", "-Atc", statement],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr)
    return result.stdout.strip()


row = sql(
    "SELECT r.occurrence_id::text || '|' || r.order_id::text || '|' || o.recurring_occurrence_id::text "
    "FROM orders.recurring_order_occurrences r "
    "JOIN orders.orders o ON o.order_id=r.order_id "
    "WHERE r.status='ORDER_CREATED' ORDER BY r.created_at DESC LIMIT 1;"
)
if not row:
    raise AssertionError("No generated recurring occurrence/order pair exists")
occurrence_id, order_id, order_occurrence_id = row.split("|", 2)
if occurrence_id != order_occurrence_id:
    raise AssertionError(
        f"Generated order occurrence identity mismatch: occurrence={occurrence_id} order={order_occurrence_id}"
    )

order_links = int(sql(
    "SELECT count(*) FROM orders.orders "
    f"WHERE recurring_occurrence_id='{occurrence_id}'::uuid;"
) or "0")
if order_links != 1:
    raise AssertionError(f"Occurrence {occurrence_id} is linked to {order_links} orders, expected exactly one")

occurrence_links = int(sql(
    "SELECT count(*) FROM orders.recurring_order_occurrences "
    f"WHERE occurrence_id='{occurrence_id}'::uuid AND order_id='{order_id}'::uuid;"
) or "0")
if occurrence_links != 1:
    raise AssertionError("Occurrence ledger lost its generated order link")

print(
    "RECURRING_OCCURRENCE_LINK_OK "
    f"occurrence={occurrence_id} order={order_id} unique_order_links={order_links}"
)
