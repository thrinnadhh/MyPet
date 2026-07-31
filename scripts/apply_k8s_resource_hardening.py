#!/usr/bin/env python3
from __future__ import annotations

import re
from pathlib import Path

MANIFEST = Path("infra/k8s/backend-services.yaml")

RESOURCE_PROFILES = {
    "api-gateway": ("250m", "384Mi", "1", "768Mi"),
    "discovery-service": ("250m", "512Mi", "1", "1Gi"),
    "catalog-service": ("250m", "512Mi", "1", "1Gi"),
    "appointment-service": ("250m", "512Mi", "1", "1Gi"),
    "provider-service": ("200m", "384Mi", "750m", "768Mi"),
    "order-service": ("300m", "512Mi", "1", "1Gi"),
    "dispatch-service": ("200m", "384Mi", "750m", "768Mi"),
    "captain-service": ("150m", "256Mi", "500m", "512Mi"),
    "notification-service": ("150m", "256Mi", "500m", "512Mi"),
    "review-service": ("150m", "256Mi", "500m", "512Mi"),
    "payment-service": ("300m", "512Mi", "1", "1Gi"),
    "chat-service": ("200m", "384Mi", "750m", "768Mi"),
    "content-service": ("150m", "256Mi", "500m", "512Mi"),
}

REQUIRED_SECRET_KEYS = {
    "catalog-service": ["INTERNAL_API_SECRET"],
    "order-service": ["INTERNAL_API_SECRET"],
    "provider-service": [
        "INTERNAL_API_SECRET",
        "MEDICAL_REPORTS_BUCKET",
        "MEDICAL_REPORTS_REGION",
    ],
    "notification-service": ["INTERNAL_API_SECRET"],
    "captain-service": ["BANK_DATA_ENCRYPTION_KEY"],
    "payment-service": [
        "RAZORPAY_KEY_ID",
        "RAZORPAY_KEY_SECRET",
        "RAZORPAY_WEBHOOK_SECRET",
    ],
}


def insert_once(text: str, marker: str, insertion: str, label: str) -> str:
    if marker not in text:
        raise RuntimeError(f"Missing marker for {label}: {marker!r}")
    return text.replace(marker, insertion + marker, 1)


def harden_deployment(document: str) -> str:
    match = re.search(r"kind: Deployment\nmetadata:\n  name: ([^\n]+)", document)
    if not match:
        return document
    name = match.group(1)
    if name not in RESOURCE_PROFILES:
        raise RuntimeError(f"No resource profile for deployment {name}")

    if "      automountServiceAccountToken: false\n" not in document:
        document = insert_once(
            document,
            "      securityContext:\n",
            "      automountServiceAccountToken: false\n",
            f"{name} service-account token",
        )

    pod_security_marker = "      securityContext:\n        runAsNonRoot: true\n"
    pod_security_replacement = (
        "      securityContext:\n"
        "        runAsNonRoot: true\n"
        "        runAsUser: 10001\n"
        "        runAsGroup: 10001\n"
        "        fsGroup: 10001\n"
        "        fsGroupChangePolicy: OnRootMismatch\n"
    )
    if "        runAsUser: 10001\n" not in document:
        if pod_security_marker not in document:
            raise RuntimeError(f"Missing pod security context for {name}")
        document = document.replace(pod_security_marker, pod_security_replacement, 1)

    container_security_marker = (
        "        securityContext:\n"
        "          allowPrivilegeEscalation: false\n"
    )
    container_security_replacement = (
        "        securityContext:\n"
        "          runAsNonRoot: true\n"
        "          runAsUser: 10001\n"
        "          privileged: false\n"
        "          allowPrivilegeEscalation: false\n"
    )
    if "          privileged: false\n" not in document:
        if container_security_marker not in document:
            raise RuntimeError(f"Missing container security context for {name}")
        document = document.replace(container_security_marker, container_security_replacement, 1)

    if "        volumeMounts:\n        - name: tmp\n" not in document:
        document = insert_once(
            document,
            "        ports:\n",
            "        volumeMounts:\n        - name: tmp\n          mountPath: /tmp\n",
            f"{name} tmp volume mount",
        )

    if "        resources:\n" not in document:
        request_cpu, request_memory, limit_cpu, limit_memory = RESOURCE_PROFILES[name]
        resources = (
            "        resources:\n"
            "          requests:\n"
            f"            cpu: {request_cpu}\n"
            f"            memory: {request_memory}\n"
            "          limits:\n"
            f"            cpu: '{limit_cpu}'\n"
            f"            memory: {limit_memory}\n"
        )
        document = insert_once(
            document,
            "        readinessProbe:\n",
            resources,
            f"{name} resources",
        )

    explicit_env = []
    for key in REQUIRED_SECRET_KEYS.get(name, []):
        env_marker = f"        - name: {key}\n"
        if env_marker not in document:
            explicit_env.append(
                "        - name: {key}\n"
                "          valueFrom:\n"
                "            secretKeyRef:\n"
                "              name: pawsnearme-backend-secrets\n"
                "              key: {key}\n".format(key=key)
            )
    if explicit_env:
        env_section = "".join(explicit_env)
        if "        env:\n" in document:
            document = document.replace("        env:\n", "        env:\n" + env_section, 1)
        else:
            document = insert_once(
                document,
                "        resources:\n",
                "        env:\n" + env_section,
                f"{name} required secret environment",
            )

    if "      volumes:\n      - name: tmp\n" not in document:
        document = document.rstrip() + (
            "\n      volumes:\n"
            "      - name: tmp\n"
            "        emptyDir:\n"
            "          sizeLimit: 256Mi\n"
        )

    return document + "\n"


def main() -> None:
    source = MANIFEST.read_text()
    documents = source.split("---\n")
    hardened = [harden_deployment(document.rstrip() + "\n") for document in documents]
    output = "---\n".join(document.rstrip() + "\n" for document in hardened)

    deployment_count = sum("kind: Deployment\n" in document for document in hardened)
    resource_count = sum("        resources:\n" in document for document in hardened if "kind: Deployment\n" in document)
    tmp_count = sum("      volumes:\n      - name: tmp\n" in document for document in hardened if "kind: Deployment\n" in document)
    if deployment_count != len(RESOURCE_PROFILES):
        raise RuntimeError(f"Expected {len(RESOURCE_PROFILES)} deployments, found {deployment_count}")
    if resource_count != deployment_count or tmp_count != deployment_count:
        raise RuntimeError(
            f"Incomplete hardening: deployments={deployment_count}, resources={resource_count}, tmp={tmp_count}"
        )

    MANIFEST.write_text(output)
    print(f"Hardened {deployment_count} backend deployments")


if __name__ == "__main__":
    main()
