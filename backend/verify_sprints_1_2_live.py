#!/usr/bin/env python3
"""Live Sprint 1-2 proof against local provider/catalog/discovery services."""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from typing import Any


PROVIDER_BASE_URL = os.environ.get("PROVIDER_BASE_URL", "http://localhost:8081")
CATALOG_BASE_URL = os.environ.get("CATALOG_BASE_URL", "http://localhost:8082")
DISCOVERY_BASE_URL = os.environ.get("DISCOVERY_BASE_URL", "http://localhost:8083")


class HttpFailure(RuntimeError):
    def __init__(self, method: str, url: str, status: int, payload: Any):
        super().__init__(f"{method} {url} failed with {status}: {payload}")
        self.status = status
        self.payload = payload


def decode_response(text: str) -> Any:
    if not text:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return text


def request_json(
    method: str,
    url: str,
    payload: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
) -> tuple[int, Any]:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request_headers = {"Accept": "application/json"}
    if payload is not None:
        request_headers["Content-Type"] = "application/json"
    if headers:
        request_headers.update(headers)

    request = urllib.request.Request(url, data=body, headers=request_headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, decode_response(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        payload_text = exc.read().decode("utf-8")
        raise HttpFailure(method, url, exc.code, decode_response(payload_text)) from exc


def request_multipart(url: str, fields: dict[str, str], file_field: str, filename: str, content: bytes) -> tuple[int, Any]:
    boundary = f"----mypet-sprint-proof-{uuid.uuid4().hex}"
    parts: list[bytes] = []
    for name, value in fields.items():
        parts.extend(
            [
                f"--{boundary}\r\n".encode("utf-8"),
                f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("utf-8"),
                value.encode("utf-8"),
                b"\r\n",
            ]
        )
    parts.extend(
        [
            f"--{boundary}\r\n".encode("utf-8"),
            f'Content-Disposition: form-data; name="{file_field}"; filename="{filename}"\r\n'.encode("utf-8"),
            b"Content-Type: text/plain\r\n\r\n",
            content,
            b"\r\n",
            f"--{boundary}--\r\n".encode("utf-8"),
        ]
    )
    request = urllib.request.Request(
        url,
        data=b"".join(parts),
        headers={
            "Accept": "application/json",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, decode_response(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        payload_text = exc.read().decode("utf-8")
        raise HttpFailure("POST", url, exc.code, decode_response(payload_text)) from exc


def require(condition: bool, label: str, details: Any = None) -> None:
    if not condition:
        raise AssertionError(f"{label} failed" + (f": {details}" if details is not None else ""))
    print(f"  PASS  {label}")


def create_profile(user_id: str, role: str, run_id: str, phone_suffix: str) -> Any:
    status, payload = request_json(
        "POST",
        f"{PROVIDER_BASE_URL}/api/v1/profiles/sync",
        headers={
            "X-User-Id": user_id,
            "X-User-Role": role,
            "X-User-Email": f"sprint12-{role.lower()}-{run_id}@example.com",
            "X-User-Full-Name": f"Sprint Proof {role.title()} {run_id}",
            "X-User-Phone": f"+9199{run_id[:6]}{phone_suffix}",
        },
    )
    require(status == 200 and payload["userId"] == user_id and payload["role"] == role, f"{role.lower()} profile created")
    return payload


def create_address(customer_id: str) -> Any:
    address_body = {
        "label": "Home",
        "line1": "Sprint Proof Street",
        "line2": None,
        "city": "Bangalore",
        "state": "Karnataka",
        "pincode": "560038",
        "geoLat": 12.9719,
        "geoLng": 77.6404,
        "isDefault": True,
    }
    try:
        request_json("POST", f"{PROVIDER_BASE_URL}/api/v1/addresses", address_body)
        raise AssertionError("address create without user header unexpectedly succeeded")
    except HttpFailure as exc:
        require(exc.status == 401, "address create rejects missing authenticated user")

    status, payload = request_json(
        "POST",
        f"{PROVIDER_BASE_URL}/api/v1/addresses",
        address_body,
        {"X-User-Id": customer_id},
    )
    is_default = payload.get("isDefault", payload.get("default"))
    require(status == 201 and payload["userId"] == customer_id and is_default is True, "default address created for authenticated user")

    status, default_payload = request_json(
        "GET",
        f"{PROVIDER_BASE_URL}/api/v1/addresses/default",
        headers={"X-User-Id": customer_id},
    )
    require(status == 200 and default_payload["addressId"] == payload["addressId"], "default address fetched for same user")
    return payload


def create_provider(owner_id: str, run_id: str, provider_type: str, fulfillment_type: str, lat_offset: float) -> Any:
    status, payload = request_json(
        "POST",
        f"{PROVIDER_BASE_URL}/api/v1/providers",
        {
            "ownerUserId": owner_id,
            "providerType": provider_type,
            "fulfillmentType": fulfillment_type,
            "name": f"Sprint 1-2 {provider_type.title().replace('_', ' ')} {run_id}",
            "description": f"Live Sprint 1-2 proof {provider_type}",
            "licenseNumber": f"S12-{run_id}-{provider_type[:3]}",
            "licenseDocUrl": None,
            "addressLine": "Sprint Proof Market",
            "city": "Bangalore",
            "pincode": "560038",
            "longitude": 77.111111 + lat_offset,
            "latitude": 12.111111 + lat_offset,
        },
    )
    require(status == 200 and payload["status"] == "DRAFT", f"{provider_type} provider created as draft")
    return payload


def upload_and_approve_provider(provider_id: str) -> Any:
    query = urllib.parse.urlencode({"filename": f"{provider_id}.txt"})
    status, upload_payload = request_json("POST", f"{PROVIDER_BASE_URL}/api/v1/providers/upload-url?{query}")
    require(status == 200 and upload_payload.get("uploadUrl") and upload_payload.get("fileUrl"), "document upload URL created")

    parsed = urllib.parse.urlparse(upload_payload["uploadUrl"])
    upload_filename = urllib.parse.parse_qs(parsed.query)["filename"][0]
    upload_status, upload_file_payload = request_multipart(
        upload_payload["uploadUrl"],
        {"filename": upload_filename},
        "file",
        f"{provider_id}.txt",
        b"Sprint 1 provider document proof\n",
    )
    require(upload_status == 200 and upload_file_payload["status"] == "SUCCESS", "document uploaded through local pre-signed flow")

    status, document_payload = request_json(
        "POST",
        f"{PROVIDER_BASE_URL}/api/v1/providers/{provider_id}/documents",
        {"docType": "LICENSE", "docUrl": upload_payload["fileUrl"]},
    )
    require(status == 200 and document_payload["providerId"] == provider_id, "provider document row attached")

    status, submitted = request_json("POST", f"{PROVIDER_BASE_URL}/api/v1/providers/{provider_id}/submit")
    require(status == 200 and submitted["status"] == "PENDING_APPROVAL", "provider submitted for approval")

    try:
        request_json(
            "POST",
            f"{PROVIDER_BASE_URL}/api/v1/providers/{provider_id}/approve",
            headers={"X-User-Role": "MERCHANT"},
        )
        raise AssertionError("non-admin approval unexpectedly succeeded")
    except HttpFailure as exc:
        require(exc.status == 403, "non-admin provider approval rejected")

    status, approved = request_json(
        "POST",
        f"{PROVIDER_BASE_URL}/api/v1/providers/{provider_id}/approve",
        headers={"X-User-Role": "ADMIN", "X-User-Id": str(uuid.uuid4())},
    )
    require(status == 200 and approved["status"] == "ACTIVE", "admin provider approval activated provider")
    return approved


def create_offering(provider_id: str, fulfillment_type: str, name: str, run_id: str) -> Any:
    payload: dict[str, Any] = {
        "providerId": provider_id,
        "name": name,
        "description": f"Live Sprint 2 offering {run_id}",
        "category": "FOOD" if fulfillment_type == "DELIVERY" else "CARE",
        "price": 199.0 if fulfillment_type == "DELIVERY" else 499.0,
        "imageUrl": None,
        "status": "ACTIVE",
        "stockQuantity": 12 if fulfillment_type == "DELIVERY" else None,
        "sku": f"S12-{run_id}-{name[:4]}",
        "durationMinutes": None if fulfillment_type == "DELIVERY" else 30,
        "barcode": f"BAR-{run_id}-{name[:4]}" if fulfillment_type == "DELIVERY" else None,
    }
    status, created = request_json("POST", f"{CATALOG_BASE_URL}/api/v1/catalog/offerings", payload)
    require(status == 201 and created["providerId"] == provider_id, f"{name} offering created")
    return created


def verify_catalog_validation(provider_id: str) -> None:
    try:
        request_json(
            "POST",
            f"{CATALOG_BASE_URL}/api/v1/catalog/offerings",
            {
                "providerId": provider_id,
                "name": "Invalid Delivery Product",
                "description": "Missing stock should fail",
                "category": "FOOD",
                "price": 100.0,
                "imageUrl": None,
                "status": "ACTIVE",
                "stockQuantity": None,
                "sku": f"INVALID-{uuid.uuid4().hex[:8]}",
                "durationMinutes": None,
                "barcode": None,
            },
        )
        raise AssertionError("invalid delivery offering unexpectedly succeeded")
    except HttpFailure as exc:
        require(exc.status >= 400, "catalog rejects delivery offering without stock")


def verify_discovery(provider: Any) -> None:
    params = urllib.parse.urlencode(
        {
            "longitude": provider["longitude"],
            "latitude": provider["latitude"],
            "radius": 1.0,
            "type": provider["providerType"],
        }
    )
    status, providers = request_json("GET", f"{DISCOVERY_BASE_URL}/api/v1/discovery/providers?{params}")
    found = [entry for entry in providers if entry["providerId"] == provider["providerId"]]
    require(status == 200 and len(found) == 1, f"discovery returns active {provider['providerType']} provider")


def main() -> int:
    run_id = uuid.uuid4().hex[:8]
    customer_id = str(uuid.uuid4())
    merchant_id = str(uuid.uuid4())
    admin_id = str(uuid.uuid4())

    print("Sprint 1-2 live proof")
    print(f"  Run ID: {run_id}")

    create_profile(customer_id, "CUSTOMER", run_id, "01")
    create_profile(merchant_id, "MERCHANT", run_id, "02")
    create_profile(admin_id, "ADMIN", run_id, "03")
    create_address(customer_id)

    store = create_provider(merchant_id, run_id, "PET_STORE", "DELIVERY", 0.001)
    approved_store = upload_and_approve_provider(store["providerId"])

    vet = create_provider(merchant_id, run_id, "VET_HOSPITAL", "APPOINTMENT", 0.002)
    approved_vet = upload_and_approve_provider(vet["providerId"])

    groom = create_provider(merchant_id, run_id, "GROOMING_CENTER", "APPOINTMENT", 0.003)
    approved_groom = upload_and_approve_provider(groom["providerId"])

    store_offering = create_offering(approved_store["providerId"], "DELIVERY", f"Store Food {run_id}", run_id)
    vet_offering = create_offering(approved_vet["providerId"], "APPOINTMENT", f"Vet Consult {run_id}", run_id)
    groom_offering = create_offering(approved_groom["providerId"], "APPOINTMENT", f"Groom Visit {run_id}", run_id)
    verify_catalog_validation(approved_store["providerId"])

    for provider in (approved_store, approved_vet, approved_groom):
        verify_discovery(provider)

    summary = {
        "ok": True,
        "runId": run_id,
        "customerId": customer_id,
        "merchantId": merchant_id,
        "adminId": admin_id,
        "providers": {
            "shop": approved_store["providerId"],
            "vet": approved_vet["providerId"],
            "groom": approved_groom["providerId"],
        },
        "offerings": {
            "shop": store_offering["offeringId"],
            "vet": vet_offering["offeringId"],
            "groom": groom_offering["offeringId"],
        },
    }
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:  # noqa: BLE001 - verifier should print the failing proof step.
        print(json.dumps({"ok": False, "error": str(exc)}, indent=2), file=sys.stderr)
        sys.exit(1)
