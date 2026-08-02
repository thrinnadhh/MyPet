#!/usr/bin/env python3
from __future__ import annotations
import json
import os
import re
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []


def fail(message: str) -> None:
    failures.append(message)
    print(f"FAIL: {message}")


def check_http(name: str, url: str, expected: set[int], headers: dict[str, str] | None = None) -> None:
    try:
        with urlopen(Request(url, headers=headers or {}), timeout=5) as response:
            status = response.status
    except HTTPError as error:
        status = error.code
    except (URLError, TimeoutError, OSError) as error:
        fail(f"{name} is unavailable: {error}")
        return
    if status >= 500 or status not in expected:
        fail(f"{name} returned HTTP {status}; expected {sorted(expected)} and never 5xx")


for client in [
    ROOT / "apps/customer-app/src/services/api-client.ts",
    ROOT / "apps/merchant-captain-app/src/services/api-client.ts",
]:
    source = client.read_text(encoding="utf-8")
    if "X-Internal-Gateway-Secret" in source or "setGatewaySecret" in source:
        fail(f"internal gateway credential remains in {client.relative_to(ROOT)}")

manifest = ROOT / "infra/k8s/backend-services.yaml"
if manifest.exists():
    value = manifest.read_text(encoding="utf-8")
    for marker in ["your-org", ":latest", "REQUIRED_IMAGE_DIGEST"]:
        if marker in value:
            fail(f"deployment manifest contains unresolved marker: {marker}")

    provider_base_url = re.search(
        r"^\s*PROVIDER_PUBLIC_BASE_URL:\s*['\"]?(https://[^'\"\s]+)",
        value,
        re.MULTILINE,
    )
    if not provider_base_url:
        fail("provider uploads require an explicit HTTPS public API base URL")

    deployments = [
        document for document in value.split("---")
        if re.search(r"\bkind:\s*Deployment\b", document)
    ]
    if len(deployments) != 13:
        fail(f"expected 13 backend deployments, found {len(deployments)}")

    required_deployment_controls = {
        "pod runAsNonRoot": "        runAsNonRoot: true",
        "pod seccomp": "        seccompProfile:",
        "service-account token disabled": "      automountServiceAccountToken: false",
        "container privilege escalation disabled": "          allowPrivilegeEscalation: false",
        "container privileged disabled": "          privileged: false",
        "read-only root filesystem": "          readOnlyRootFilesystem: true",
        "capabilities dropped": "            drop:",
        "CPU request": "            cpu:",
        "memory request": "            memory:",
        "writable tmp mount": "          mountPath: /tmp",
        "bounded tmp volume": "          sizeLimit: 256Mi",
    }
    for deployment in deployments:
        name_match = re.search(r"metadata:\s*\n\s*name:\s*([^\s]+)", deployment)
        name = name_match.group(1) if name_match else "unknown-deployment"
        if "        resources:\n          requests:" not in deployment:
            fail(f"deployment {name} does not declare resource requests")
        for control, marker in required_deployment_controls.items():
            if marker not in deployment:
                fail(f"deployment {name} is missing {control}")

    required_secrets = {
        "appointment-service": {"INTERNAL_API_SECRET"},
        "catalog-service": {"INTERNAL_API_SECRET"},
        "order-service": {"INTERNAL_API_SECRET"},
        "provider-service": {
            "INTERNAL_API_SECRET",
            "MEDICAL_REPORTS_BUCKET",
            "MEDICAL_REPORTS_REGION",
        },
        "notification-service": {"INTERNAL_API_SECRET"},
        "content-service": {"INTERNAL_API_SECRET"},
        "captain-service": {"BANK_DATA_ENCRYPTION_KEY"},
        "payment-service": {
            "INTERNAL_API_SECRET",
            "RAZORPAY_KEY_ID",
            "RAZORPAY_KEY_SECRET",
            "RAZORPAY_WEBHOOK_SECRET",
        },
    }
    for deployment in deployments:
        name_match = re.search(r"metadata:\s*\n\s*name:\s*([^\s]+)", deployment)
        if not name_match:
            continue
        name = name_match.group(1)
        for key in required_secrets.get(name, set()):
            expected = (
                f"        - name: {key}\n"
                "          valueFrom:\n"
                "            secretKeyRef:\n"
                "              name: pawsnearme-backend-secrets\n"
                f"              key: {key}"
            )
            if expected not in deployment:
                fail(f"deployment {name} does not explicitly require secret {key}")

hpa_pdb = ROOT / "infra/k8s/hpa-pdb.yaml"
if not hpa_pdb.exists():
    fail("HPA/PDB manifest is missing")
else:
    hpa_value = hpa_pdb.read_text(encoding="utf-8")
    for workload in ["api-gateway", "order-service", "payment-service"]:
        if not re.search(
            rf"kind:\s*HorizontalPodAutoscaler[\s\S]*?name:\s*{re.escape(workload)}\b",
            hpa_value,
        ):
            fail(f"HPA is missing for {workload}")
        if not re.search(
            rf"kind:\s*PodDisruptionBudget[\s\S]*?name:\s*{re.escape(workload)}\b",
            hpa_value,
        ):
            fail(f"PodDisruptionBudget is missing for {workload}")

versions = set()
for gradle in (ROOT / "backend").rglob("build.gradle.kts"):
    versions.update(
        re.findall(
            r"spring-boot-dependencies:([0-9.]+)",
            gradle.read_text(encoding="utf-8"),
        )
    )
if len(versions) > 1:
    fail(f"multiple Spring Boot BOM versions found: {sorted(versions)}")

if os.getenv("RUN_LIVE_SMOKE_TESTS") == "true":
    base = os.environ["STAGING_GATEWAY_URL"].rstrip("/")
    check_http("gateway readiness", f"{base}/actuator/health/readiness", {200})
    check_http("unauthenticated admin rejection", f"{base}/api/v1/providers/pending", {401, 403})

report = {"passed": not failures, "failures": failures}
(ROOT / "backend/release-gate-report.json").write_text(json.dumps(report, indent=2) + "\n")
if failures:
    sys.exit(1)
print("All configured release gates passed.")
