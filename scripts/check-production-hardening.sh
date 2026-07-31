#!/usr/bin/env bash
set -euo pipefail

! grep -R --line-number --exclude-dir=node_modules --exclude='*.md' \
  'X-Internal-Gateway-Secret' apps/customer-app apps/merchant-captain-app
! grep -R --line-number --exclude-dir=node_modules 'dev-gateway-secret-key' apps
! grep -R --line-number 'ghcr.io/your-org' infra/k8s
! grep -R --line-number 'image: .*:latest' infra/k8s
python3 backend/scan_dependencies.py
