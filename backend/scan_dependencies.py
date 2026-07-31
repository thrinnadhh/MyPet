#!/usr/bin/env python3
from __future__ import annotations
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
versions: dict[str, list[str]] = {}
for gradle in (ROOT / "backend").rglob("build.gradle.kts"):
    text = gradle.read_text(encoding="utf-8")
    for version in re.findall(r"spring-boot-dependencies:([0-9.]+)", text):
        versions.setdefault(version, []).append(str(gradle.relative_to(ROOT)))
root_gradle = (ROOT / "backend/build.gradle.kts").read_text(encoding="utf-8")
plugin = re.search(r'org\.springframework\.boot"\) version "([0-9.]+)"', root_gradle)
if plugin:
    versions.setdefault(plugin.group(1), []).append("backend/build.gradle.kts (plugin)")
if len(versions) != 1:
    print(json.dumps({"error": "inconsistent Spring Boot versions", "versions": versions}, indent=2))
    sys.exit(1)
print(json.dumps({"springBootVersion": next(iter(versions)), "files": sum(versions.values(), [])}, indent=2))
