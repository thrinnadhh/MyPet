#!/usr/bin/env python3
"""Docker-backed barcode inventory upload → scan lookup → POS billing E2E test."""

from __future__ import annotations

import base64
import hashlib
import json
import os
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
PROJECT_NAME = os.environ.get("COMPOSE_PROJECT_NAME", "mypet-e2e")
ENV_FILE = os.environ.get("MYPET_ENV_FILE")
REPORT = Path(os.environ.get("MYPET_SMOKE_REPORT", ROOT / "build/reports/full-stack-smoke.md"))
GATEWAY = os.environ.get("MYPET_GATEWAY_URL", "http://localhost:8080")

if not ENV_FILE:
    raise SystemExit("MYPET_ENV_FILE must be set by scripts/test-all.sh")

COMPOSE = [
    "docker",
    "compose",
    "-p",
    PROJECT_NAME,
    "--env-file",
    ENV_FILE,
    "-f",
    str(ROOT / "infra/docker-compose.yml"),
    "-f",
    str(ROOT / "infra/docker-compose.replicas.yml"),
    "-f",
    str(ROOT / "infra/docker-compose.local.yml"),
]


@dataclass(frozen=True)
class Actor:
    user_id: str
    role: str
    token: str


def append_report(text: str) -> None:
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    with REPORT.open("a", encoding="utf-8") as handle:
        handle.write(text)


def passed(message: str) -> None:
    print(f"PASS [barcode] {message}")
    append_report(f"- ✅ {message}\n")


def require(condition: bool, message: str, details: Any = None) -> None:
    if not condition:
        suffix = "" if details is None else f": {details}"
        raise AssertionError(f"{message}{suffix}")


def compose(*args: str) -> str:
    result = subprocess.run(
        [*COMPOSE, *args],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"Compose command failed ({result.returncode}): {' '.join(args)}\n{result.stderr}"
        )
    return result.stdout.strip()


def sql(statement: str) -> str:
    return compose(
        "exec",
        "-T",
        "postgres",
        "psql",
        "-U",
        "postgres",
        "-d",
        "pawsnearme",
        "-v",
        "ON_ERROR_STOP=1",
        "-Atc",
        statement,
    )


def encode_jwt_part(value: dict[str, Any]) -> str:
    raw = json.dumps(value, separators=(",", ":")).encode("utf-8")
    return base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")


def make_actor(role: str) -> Actor:
    user_id = str(uuid.uuid4())
    now = int(time.time())
    claims = {
        "sub": user_id,
        "iat": now,
        "exp": now + 3600,
        "email": f"barcode-e2e-{role.lower()}-{user_id[:8]}@mypet.local",
        "app_metadata": {"role": role},
        "user_metadata": {
            "full_name": f"Barcode E2E {role.title()}",
            "phone": f"+9199{user_id.replace('-', '')[:8]}",
        },
    }
    token = f"{encode_jwt_part({'alg': 'none', 'typ': 'JWT'})}.{encode_jwt_part(claims)}."
    return Actor(user_id=user_id, role=role, token=token)


def decode_body(raw: bytes) -> Any:
    text = raw.decode("utf-8")
    if not text:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return text


def request(
    method: str,
    path: str,
    actor: Actor | None = None,
    payload: Any = None,
    expected: tuple[int, ...] = (200,),
) -> tuple[int, Any]:
    url = path if path.startswith("http") else f"{GATEWAY}{path}"
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    if actor is not None:
        headers["Authorization"] = f"Bearer {actor.token}"

    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            status = response.status
            decoded = decode_body(response.read())
    except urllib.error.HTTPError as exc:
        status = exc.code
        decoded = decode_body(exc.read())
    except urllib.error.URLError as exc:
        raise RuntimeError(f"{method} {url} failed: {exc.reason}") from exc

    if status not in expected:
        raise AssertionError(
            f"{method} {path} expected {expected}, received {status}: {decoded}"
        )
    return status, decoded


def create_provider(owner: Actor, name: str, suffix: str) -> dict[str, Any]:
    _, result = request(
        "POST",
        "/api/v1/providers",
        owner,
        {
            "ownerUserId": owner.user_id,
            "providerType": "PET_STORE",
            "fulfillmentType": "DELIVERY",
            "name": name,
            "description": "Ephemeral provider for barcode E2E verification",
            "licenseNumber": f"BARCODE-{suffix}-{uuid.uuid4().hex[:6]}",
            "licenseDocUrl": None,
            "addressLine": "Barcode E2E Test Road",
            "city": "Tirupati",
            "pincode": "517501",
            "longitude": 79.4192,
            "latitude": 13.6288,
        },
        expected=(200,),
    )
    require(isinstance(result, dict), "provider response must be an object", result)
    require(result["ownerUserId"] == owner.user_id, "provider owner mismatch", result)
    require(result["providerType"] == "PET_STORE", "provider type mismatch", result)
    require(result["fulfillmentType"] == "DELIVERY", "provider fulfillment mismatch", result)
    return result


def error_text(value: Any) -> str:
    if isinstance(value, str):
        return value.lower()
    return json.dumps(value, sort_keys=True).lower()


def main() -> None:
    merchant = make_actor("MERCHANT")
    customer = make_actor("CUSTOMER")
    spoofed_staff_id = str(uuid.uuid4())
    suffix = uuid.uuid4().hex[:10]
    upc = f"{int(hashlib.sha256(suffix.encode()).hexdigest()[:14], 16) % 10**12:012d}"
    ean_alias = f"0{upc}"
    sku = f"BARCODE-E2E-{suffix.upper()}"
    idempotency_key = f"barcode-e2e-{suffix}"

    append_report("\n## Barcode scanner end-to-end flow\n")

    _, profile = request("POST", "/api/v1/profiles/sync", merchant, expected=(200,))
    require(profile["userId"] == merchant.user_id, "profile user mismatch", profile)
    require(profile["role"] == "MERCHANT", "profile role mismatch", profile)
    passed("Merchant identity was synchronized through the gateway")

    provider = create_provider(merchant, f"Barcode E2E Store {suffix}", suffix)
    provider_id = provider["providerId"]
    passed("Merchant created a delivery store for barcode inventory")

    second_provider = create_provider(merchant, f"Barcode Isolation Store {suffix}", suffix)
    second_provider_id = second_provider["providerId"]
    passed("A second merchant store was created for provider-isolation checks")

    offering_payload = {
        "providerId": provider_id,
        "name": "Barcode E2E Adult Dog Food",
        "description": "Complete nutrition uploaded by scanning a product barcode.",
        "category": "Food & Nutrition",
        "price": 499.00,
        "imageUrl": "https://example.invalid/barcode-e2e-product.jpg",
        "status": "ACTIVE",
        "stockQuantity": 5,
        "sku": sku,
        "durationMinutes": None,
        "barcode": ean_alias,
    }
    _, offering = request(
        "POST",
        "/api/v1/catalog/offerings",
        merchant,
        offering_payload,
        expected=(201,),
    )
    product_id = offering["offeringId"]
    require(offering["providerId"] == provider_id, "offering provider mismatch", offering)
    require(offering["barcode"] == upc, "EAN alias was not canonicalized", offering)
    require(offering["name"] == "Barcode E2E Adult Dog Food", "offering name mismatch", offering)
    require(float(offering["price"]) == 499.0, "offering price mismatch", offering)
    require(offering["stockQuantity"] == 5, "offering stock mismatch", offering)
    require(offering["sku"] == sku, "offering SKU mismatch", offering)
    passed("Inventory upload canonicalized the EAN alias and persisted complete product details")

    _, lookup = request(
        "GET",
        f"/api/v1/catalog/offerings/by-barcode?storeId={provider_id}&barcode={urllib.parse.quote(ean_alias)}",
        merchant,
        expected=(200,),
    )
    require(lookup["offeringId"] == product_id, "barcode resolved the wrong product", lookup)
    require(lookup["barcode"] == upc, "lookup did not return canonical barcode", lookup)
    require(lookup["description"].startswith("Complete nutrition"), "description missing", lookup)
    require(lookup["category"] == "Food & Nutrition", "category mismatch", lookup)
    require(lookup["imageUrl"].endswith("barcode-e2e-product.jpg"), "image URL mismatch", lookup)
    require(lookup["stockQuantity"] == 5, "lookup stock mismatch", lookup)
    passed("Camera-equivalent EAN scan resolved complete live product details")

    request(
        "GET",
        f"/api/v1/catalog/offerings/by-barcode?storeId={provider_id}&barcode={upc}",
        customer,
        expected=(403,),
    )
    passed("Customer identity cannot access merchant barcode lookup")

    _, isolated = request(
        "GET",
        f"/api/v1/catalog/offerings/by-barcode?storeId={second_provider_id}&barcode={upc}",
        merchant,
        expected=(400, 404),
    )
    require("not found" in error_text(isolated), "provider isolation failure was unclear", isolated)
    passed("Barcode lookup is isolated to the selected merchant provider")

    _, duplicate = request(
        "POST",
        "/api/v1/catalog/offerings",
        merchant,
        offering_payload,
        expected=(400, 409),
    )
    require(
        any(word in error_text(duplicate) for word in ("barcode", "already belongs", "conflict")),
        "duplicate barcode response did not explain the conflict",
        duplicate,
    )
    passed("Duplicate UPC/EAN aliases are rejected within the provider inventory")

    bill_payload = {
        "storeId": provider_id,
        "staffId": spoofed_staff_id,
        "status": "FINALIZED",
        "subtotal": 0.01,
        "totalDiscount": 0.00,
        "tax": 0.00,
        "grandTotal": 0.01,
        "idempotencyKey": idempotency_key,
        "items": [
            {
                "productId": product_id,
                "barcodeScanned": ean_alias,
                "quantity": 2,
                "unitPrice": 0.01,
                "discountAmount": 0.00,
                "discountType": "NONE",
            }
        ],
    }
    _, bill = request(
        "POST",
        "/api/v1/catalog/bills",
        merchant,
        bill_payload,
        expected=(201,),
    )
    bill_id = bill["bill"]["id"]
    require(bill["bill"]["staffId"] == merchant.user_id, "server did not replace spoofed staff ID", bill)
    require(bill["bill"]["status"] == "SYNCED", "bill was not synchronized", bill)
    require(abs(float(bill["bill"]["subtotal"]) - 998.0) < 0.001, "server subtotal mismatch", bill)
    require(abs(float(bill["bill"]["tax"]) - 179.64) < 0.001, "server tax mismatch", bill)
    require(abs(float(bill["bill"]["grandTotal"]) - 1177.64) < 0.001, "server total mismatch", bill)
    require(len(bill["successfulItems"]) == 1 and not bill["failedItems"], "bill item result mismatch", bill)
    require(float(bill["successfulItems"][0]["unitPrice"]) == 499.0, "client price was trusted", bill)
    require(bill["successfulItems"][0]["barcodeScanned"] == upc, "bill barcode was not canonical", bill)
    passed("POS checkout ignored spoofed client price and staff identity")

    _, post_bill = request(
        "GET",
        f"/api/v1/catalog/offerings/by-barcode?storeId={provider_id}&barcode={upc}",
        merchant,
        expected=(200,),
    )
    require(post_bill["stockQuantity"] == 3, "stock was not atomically deducted", post_bill)
    passed("Immediate rescan returned fresh stock after bill deduction")

    _, retry = request(
        "POST",
        "/api/v1/catalog/bills",
        merchant,
        bill_payload,
        expected=(201,),
    )
    require(retry["bill"]["id"] == bill_id, "idempotent retry created another bill", retry)
    _, retry_lookup = request(
        "GET",
        f"/api/v1/catalog/offerings/by-barcode?storeId={provider_id}&barcode={ean_alias}",
        merchant,
        expected=(200,),
    )
    require(retry_lookup["stockQuantity"] == 3, "idempotent retry deducted stock twice", retry_lookup)
    passed("Idempotent bill retry returned the original bill without a second stock deduction")

    oversell_payload = {
        **bill_payload,
        "staffId": merchant.user_id,
        "idempotencyKey": f"barcode-oversell-{suffix}",
        "subtotal": 1996.00,
        "tax": 359.28,
        "grandTotal": 2355.28,
        "items": [{**bill_payload["items"][0], "barcodeScanned": upc, "quantity": 4, "unitPrice": 499.00}],
    }
    _, oversell = request(
        "POST",
        "/api/v1/catalog/bills",
        merchant,
        oversell_payload,
        expected=(400,),
    )
    require("stock" in error_text(oversell) or "finalized" in error_text(oversell), "oversell error was unclear", oversell)
    _, after_oversell = request(
        "GET",
        f"/api/v1/catalog/offerings/by-barcode?storeId={provider_id}&barcode={upc}",
        merchant,
        expected=(200,),
    )
    require(after_oversell["stockQuantity"] == 3, "oversell changed stock", after_oversell)
    passed("Oversell checkout was rejected without changing inventory")

    mismatch_payload = {
        **bill_payload,
        "staffId": merchant.user_id,
        "idempotencyKey": f"barcode-mismatch-{suffix}",
        "subtotal": 499.00,
        "tax": 89.82,
        "grandTotal": 588.82,
        "items": [{**bill_payload["items"][0], "barcodeScanned": "999999999999", "quantity": 1, "unitPrice": 499.00}],
    }
    _, mismatch = request(
        "POST",
        "/api/v1/catalog/bills",
        merchant,
        mismatch_payload,
        expected=(400,),
    )
    require("barcode" in error_text(mismatch) or "match" in error_text(mismatch), "barcode mismatch error was unclear", mismatch)
    passed("A scanned barcode cannot be paired with a different product ID")

    persisted = sql(
        "SELECT o.barcode || '|' || o.stock_quantity || '|' || "
        "bi.barcode_scanned || '|' || bi.unit_price "
        "FROM catalog.offerings o "
        "JOIN billing.bill_items bi ON bi.product_id = o.offering_id "
        "JOIN billing.bills b ON b.id = bi.bill_id "
        f"WHERE o.offering_id = '{product_id}'::uuid "
        f"AND b.id = '{bill_id}'::uuid;"
    )
    require(persisted == f"{upc}|3|{upc}|499.00", "unexpected persisted barcode/bill state", persisted)
    passed("Canonical barcode, authoritative price and remaining stock persisted in PostgreSQL")

    append_report("- ✅ Barcode inventory upload → scan lookup → POS checkout flow passed\n")


if __name__ == "__main__":
    main()
