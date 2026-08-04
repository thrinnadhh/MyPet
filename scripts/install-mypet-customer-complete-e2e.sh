#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAYLOAD_DIR="$SCRIPT_DIR/customer-e2e/payload"
EXPECTED_SHA256="2df6346f304f9e4c674014a1da819e5bd9cde197fc6c6fff92711398a157df2c"
EXPECTED_PARTS=8
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

fail() {
  echo "Customer E2E installer error: $*" >&2
  exit 1
}

decode_base64() {
  if printf '' | base64 --decode >/dev/null 2>&1; then
    base64 --decode
  elif printf '' | base64 -D >/dev/null 2>&1; then
    base64 -D
  else
    base64 -d
  fi
}

sha256_file() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file" | awk '{print $1}'
  else
    fail "Neither sha256sum nor shasum is installed."
  fi
}

shopt -s nullglob
parts=("$PAYLOAD_DIR"/installer.sh.gz.b64.part*)
shopt -u nullglob

if [ "${#parts[@]}" -ne "$EXPECTED_PARTS" ]; then
  fail "Expected $EXPECTED_PARTS payload parts, found ${#parts[@]}."
fi

cat "${parts[@]}" \
  | decode_base64 \
  | gzip -dc > "$TMP_DIR/installer.sh"

ACTUAL_SHA256="$(sha256_file "$TMP_DIR/installer.sh")"
if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
  fail "Integrity check failed. Expected $EXPECTED_SHA256, got $ACTUAL_SHA256."
fi

bash -n "$TMP_DIR/installer.sh"

echo "Customer E2E installer verified: $ACTUAL_SHA256"

if [ "${1:-}" = "--verify-only" ]; then
  exit 0
fi

exec bash "$TMP_DIR/installer.sh" "$@"
