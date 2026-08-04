#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAYLOAD_DIR="$SCRIPT_DIR/customer-e2e/payload"
FILTER_OVERRIDE_PAYLOAD="$SCRIPT_DIR/customer-e2e/overrides/fix-filters.sh.gz.b64"
MODERN_PATCH_PAYLOAD="$SCRIPT_DIR/customer-e2e/overrides/modern-customer-patch.py.gz.b64"
EXPECTED_SHA256="2df6346f304f9e4c674014a1da819e5bd9cde197fc6c6fff92711398a157df2c"
EXPECTED_FILTER_OVERRIDE_SHA256="4e9547a821fd71623029858fc644e5bbc9e475d43aa02fa6e0e52e53dcd8500a"
EXPECTED_MODERN_PATCH_SHA256="1003defb4fb878058ed201af597851fdb3a9d3c1c181c593f038730dc30dd828"
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

verify_file() {
  local file="$1"
  local expected="$2"
  local label="$3"
  local actual
  actual="$(sha256_file "$file")"
  if [ "$actual" != "$expected" ]; then
    fail "$label integrity check failed. Expected $expected, got $actual."
  fi
  echo "$label verified: $actual"
}

shopt -s nullglob
parts=("$PAYLOAD_DIR"/installer.sh.gz.b64.part*)
shopt -u nullglob
if [ "${#parts[@]}" -ne "$EXPECTED_PARTS" ]; then
  fail "Expected $EXPECTED_PARTS legacy payload parts, found ${#parts[@]}."
fi

cat "${parts[@]}" | decode_base64 | gzip -dc > "$TMP_DIR/installer.sh"
verify_file "$TMP_DIR/installer.sh" "$EXPECTED_SHA256" "Legacy customer E2E installer"
bash -n "$TMP_DIR/installer.sh"

[ -f "$FILTER_OVERRIDE_PAYLOAD" ] || fail "Missing legacy filter compatibility override."
decode_base64 < "$FILTER_OVERRIDE_PAYLOAD" | gzip -dc > "$TMP_DIR/fix-filters.sh"
verify_file "$TMP_DIR/fix-filters.sh" "$EXPECTED_FILTER_OVERRIDE_SHA256" "Legacy filter override"
bash -n "$TMP_DIR/fix-filters.sh"

[ -f "$MODERN_PATCH_PAYLOAD" ] || fail "Missing modern customer patch."
decode_base64 < "$MODERN_PATCH_PAYLOAD" | gzip -dc > "$TMP_DIR/modern-customer-patch.py"
verify_file "$TMP_DIR/modern-customer-patch.py" "$EXPECTED_MODERN_PATCH_SHA256" "Modern customer patch"
python3 -m py_compile "$TMP_DIR/modern-customer-patch.py"

if [ "${1:-}" = "--verify-only" ]; then
  exit 0
fi

REPO="${1:-/Users/trinadh/projects/Mypet}"
HOME_FILE="$REPO/apps/customer-app/src/screens/home-screen.tsx"
[ -f "$HOME_FILE" ] || fail "Customer home screen not found at $HOME_FILE"

if grep -q "const CATEGORIES: CategoryItem\[\]" "$HOME_FILE"; then
  echo "Detected modern customer UI; applying the current-tree patch."
  exec python3 "$TMP_DIR/modern-customer-patch.py" "$REPO"
fi

if grep -q "const STORY_CATEGORIES" "$HOME_FILE"; then
  echo "Detected legacy customer UI; applying the legacy compatibility path."
  if ! grep -q "route: '/commerce/food'" "$HOME_FILE"; then
    bash "$TMP_DIR/fix-filters.sh" "$REPO"
  fi
  exec bash "$TMP_DIR/installer.sh" "$REPO"
fi

fail "Unsupported customer Home structure. Refusing to guess or overwrite the UI."
