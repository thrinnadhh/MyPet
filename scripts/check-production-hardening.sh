#!/usr/bin/env bash
set -euo pipefail

fail_if_found() {
  local description="$1"
  shift
  if grep -R --line-number "$@"; then
    echo "ERROR: ${description}" >&2
    exit 1
  fi
}

fail_if_found \
  "mobile clients contain an internal gateway credential header" \
  --exclude-dir=node_modules --exclude='*.md' \
  'X-Internal-Gateway-Secret' apps/customer-app apps/merchant-captain-app

fail_if_found \
  "mobile applications contain the development gateway secret" \
  --exclude-dir=node_modules --exclude='*.md' \
  'dev-gateway-secret-key' apps

fail_if_found \
  "Kubernetes manifests still contain the placeholder registry organization" \
  --exclude='*.md' \
  'ghcr.io/your-org' infra/k8s

fail_if_found \
  "Kubernetes manifests use mutable latest image tags" \
  --exclude='*.md' \
  'image: .*:latest' infra/k8s

python3 scripts/check-flyway-migrations.py
python3 backend/scan_dependencies.py
