#!/usr/bin/env bash
set -euo pipefail

WORKERS=(
  mypet-provider-worker
  mypet-order-worker
  mypet-appointment-worker
  mypet-dispatch-worker
  mypet-notification-worker
  mypet-review-worker
  mypet-payment-worker
  mypet-content-worker
)

docker rm -f "${WORKERS[@]}" >/dev/null 2>&1 || true
echo "M7 worker containers stopped."
