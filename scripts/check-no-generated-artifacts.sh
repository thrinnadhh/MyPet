#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

git -C "$ROOT_DIR" ls-files | while IFS= read -r tracked_path; do
  if [[ -e "$ROOT_DIR/$tracked_path" ]] && [[ "$tracked_path" =~ (^|/)(node_modules|\.expo|\.gradle|build|bin)/|\.pyc$|__pycache__/ ]]; then
    echo "$tracked_path"
  fi
done >/tmp/pawsnearme-generated-artifacts.txt

if [[ -s /tmp/pawsnearme-generated-artifacts.txt ]]; then
  echo "Generated artifacts are tracked by git:" >&2
  cat /tmp/pawsnearme-generated-artifacts.txt >&2
  exit 1
fi

echo "No generated artifacts are tracked."
