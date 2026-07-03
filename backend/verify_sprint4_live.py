#!/usr/bin/env python3
"""Live Sprint 4 proof against local provider, order, dispatch, and captain services."""

from __future__ import annotations

import base64
import json
import os
import socket
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from typing import Any

import psycopg2


PROVIDER_BASE_URL = os.environ.get("PROVIDER_BASE_URL", "http://localhost:8081")
ORDER_BASE_URL = os.environ.get("ORDER_BASE_URL", "http://localhost:8084")
DISPATCH_BASE_URL = os.environ.get("DISPATCH_BASE_URL", "http://localhost:8086")
CAPTAIN_BASE_URL = os.environ.get("CAPTAIN_BASE_URL", "http://localhost:8087")
DB_CONN = os.environ.get("DB_CONN", "host=localhost port=5433 dbname=pawsnearme user=postgres password=postgres")
REDIS_HOST = os.environ.get("REDIS_HOST", "localhost")
REDIS_PORT = int(os.environ.get("REDIS_PORT", "6380"))
CAPTAIN_GEO_KEY = "captains:locations"


class HttpFailure(RuntimeError):
    def __init__(self, method: str, url: str, status: int, payload: Any):
        super().__init__(f"{method} {url} failed with {status}: {payload}")
        self.status = status
        self.payload = payload


def generate_mock_jwt(user_id: str, role: str, run_id: str) -> str:
    header = {"alg": "none", "typ": "JWT"}
    payload = {
        "sub": user_id,
        "role": role,
        "app_metadata": {"role": role},
        "user_metadata": {
            "full_name": f"Sprint 4 {role.title()} {run_id}",
            "phone": f"+9198{run_id[:6]}{role[:2]}",
        },
        "email": f"sprint4-{role.lower()}-{run_id}@example.com",
        "exp": int(time.time()) + 3600,
    }
    header_b64 = base64.urlsafe_b64encode(json.dumps(header).encode()).decode().rstrip("=")
    payload_b64 = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
    return f"{header_b64}.{payload_b64}."


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
    token: str | None = None,
    extra_headers: dict[str, str] | None = None,
) -> tuple[int, Any]:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request_headers = {"Accept": "application/json"}
    if payload is not None:
        request_headers["Content-Type"] = "application/json"
    if token:
        request_headers["Authorization"] = f"Bearer {token}"
    if extra_headers:
        request_headers.update(extra_headers)

    request = urllib.request.Request(url, data=body, headers=request_headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            return response.status, decode_response(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raise HttpFailure(method, url, exc.code, decode_response(exc.read().decode("utf-8"))) from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"{method} {url} failed: {exc.reason}") from exc


def require(condition: bool, label: str, details: Any = None) -> None:
    if not condition:
        raise AssertionError(f"{label} failed" + (f": {details}" if details is not None else ""))
    print(f"  PASS  {label}")


def redis_command(*parts: str) -> list[str]:
    payload = f"*{len(parts)}\r\n" + "".join(f"${len(part.encode('utf-8'))}\r\n{part}\r\n" for part in parts)
    with socket.create_connection((REDIS_HOST, REDIS_PORT), timeout=5) as sock:
        sock.sendall(payload.encode("utf-8"))
        response = sock.recv(4096).decode("utf-8", errors="replace")

    if response.startswith("-"):
        raise RuntimeError(f"Redis command failed: {response.strip()}")
    return [line for line in response.split("\r\n") if line]


def clear_captain_locations() -> None:
    redis_command("DEL", CAPTAIN_GEO_KEY)
    require(True, "captain Redis geo index reset for isolated proof")


def sync_profile(token: str, user_id: str, role: str) -> None:
    _ = token
    status, payload = request_json(
        "POST",
        f"{PROVIDER_BASE_URL}/api/v1/profiles/sync",
        extra_headers={
            "X-User-Id": user_id,
            "X-User-Role": role,
            "X-User-Email": f"sprint4-{role.lower()}-{user_id[:8]}@example.com",
            "X-User-Full-Name": f"Sprint 4 {role.title()}",
            "X-User-Phone": f"+9198{user_id.replace('-', '')[:8]}",
        },
    )
    require(status == 200 and payload["userId"] == user_id and payload["role"] == role, f"{role.lower()} profile synced")


def cleanup_and_seed(run_id: str, ids: dict[str, str]) -> None:
    with psycopg2.connect(DB_CONN) as conn:
        conn.autocommit = True
        with conn.cursor() as cur:
            cur.execute("DELETE FROM captains.captain_earnings WHERE captain_id IN (%s, %s);", (ids["captain1"], ids["captain2"]))
            cur.execute("DELETE FROM dispatch.dispatch_offers WHERE captain_id IN (%s, %s);", (ids["captain1"], ids["captain2"]))
            cur.execute(
                "DELETE FROM dispatch.dispatch_jobs WHERE order_id IN (SELECT order_id FROM orders.orders WHERE customer_id = %s);",
                (ids["customer"],),
            )
            cur.execute("DELETE FROM orders.order_items WHERE offering_id = %s;", (ids["offering"],))
            cur.execute("DELETE FROM orders.order_status_history WHERE changed_by_user_id IN (%s, %s, %s, %s);", (ids["customer"], ids["merchant"], ids["captain1"], ids["captain2"]))
            cur.execute("DELETE FROM orders.invoices WHERE order_id IN (SELECT order_id FROM orders.orders WHERE customer_id = %s);", (ids["customer"],))
            cur.execute("DELETE FROM orders.orders WHERE customer_id = %s;", (ids["customer"],))
            cur.execute("DELETE FROM catalog.offerings WHERE offering_id = %s;", (ids["offering"],))
            cur.execute("DELETE FROM providers.providers WHERE provider_id = %s;", (ids["provider"],))
            cur.execute("DELETE FROM captains.captain_profiles WHERE captain_id IN (%s, %s);", (ids["captain1"], ids["captain2"]))
            cur.execute("DELETE FROM identity.addresses WHERE user_id = %s;", (ids["customer"],))
            cur.execute("DELETE FROM identity.user_roles WHERE user_id IN (%s, %s, %s, %s);", (ids["customer"], ids["merchant"], ids["captain1"], ids["captain2"]))
            cur.execute("DELETE FROM identity.profiles WHERE user_id IN (%s, %s, %s, %s);", (ids["customer"], ids["merchant"], ids["captain1"], ids["captain2"]))

            cur.execute(
                """
                INSERT INTO providers.providers
                    (provider_id, owner_user_id, provider_type, fulfillment_type, name, address_line, city, pincode, geo_location, status)
                VALUES
                    (%s, %s, 'PET_STORE', 'DELIVERY', %s, 'Sprint 4 Dispatch Market', 'Delhi', '110001',
                     ST_GeographyFromText('SRID=4326;POINT(77.2177 28.6304)'), 'ACTIVE');
                """,
                (ids["provider"], ids["merchant"], f"Sprint 4 Pet Store {run_id}"),
            )
            cur.execute(
                """
                INSERT INTO catalog.offerings
                    (offering_id, provider_id, name, description, category, price, status, stock_quantity, sku)
                VALUES
                    (%s, %s, 'Sprint 4 Dog Food', 'Dispatch proof product', 'FOOD', 499.00, 'ACTIVE', 10, %s);
                """,
                (ids["offering"], ids["provider"], f"S4-{run_id}"),
            )


def create_address(customer_id: str) -> str:
    status, payload = request_json(
        "POST",
        f"{PROVIDER_BASE_URL}/api/v1/addresses",
        {
            "label": "Home",
            "line1": "Sprint 4 Delivery Address",
            "line2": None,
            "city": "Delhi",
            "state": "Delhi",
            "pincode": "110022",
            "geoLat": 28.5684,
            "geoLng": 77.1703,
            "isDefault": True,
        },
        extra_headers={"X-User-Id": customer_id},
    )
    require(status == 201 and payload.get("addressId"), "customer default address created")
    return payload["addressId"]


def onboard_captain(captain_id: str, label: str) -> None:
    status, payload = request_json(
        "POST",
        f"{CAPTAIN_BASE_URL}/api/v1/captains/profiles",
        {"captainId": None, "vehicleType": "BIKE", "vehicleNumber": f"S4-{label}", "licenseDocUrl": None},
        extra_headers={"X-User-Id": captain_id},
    )
    require(status == 200 and payload["status"] == "ACTIVE", f"{label} captain onboarded")


def go_online(captain_id: str, label: str, lng: float, lat: float) -> None:
    status, payload = request_json(
        "PUT",
        f"{CAPTAIN_BASE_URL}/api/v1/captains/status",
        {"captainId": None, "online": True, "longitude": lng, "latitude": lat},
        extra_headers={"X-User-Id": captain_id},
    )
    require(status == 200 and payload["status"] == "ONLINE", f"{label} captain online")


def poll_offer(captain_id: str, captain_label: str, expected_order_id: str, timeout_seconds: int = 20) -> dict[str, Any]:
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        status, payload = request_json(
            "GET",
            f"{DISPATCH_BASE_URL}/api/v1/dispatch/offers",
            extra_headers={"X-User-Id": captain_id},
        )
        if status == 200:
            for offer in payload:
                if offer["orderId"] == expected_order_id:
                    print(f"  INFO  {captain_label} offer {offer['offerId']}")
                    return offer
        time.sleep(1)
    raise AssertionError(f"{captain_label} did not receive offer for order {expected_order_id}")


def expire_offer(offer_id: str) -> None:
    with psycopg2.connect(DB_CONN) as conn:
        conn.autocommit = True
        with conn.cursor() as cur:
            cur.execute("UPDATE dispatch.dispatch_offers SET offered_at = now() - interval '35 seconds' WHERE offer_id = %s;", (offer_id,))


def main() -> None:
    run_id = uuid.uuid4().hex[:8]
    ids = {
        "customer": str(uuid.uuid4()),
        "merchant": str(uuid.uuid4()),
        "captain1": str(uuid.uuid4()),
        "captain2": str(uuid.uuid4()),
        "provider": str(uuid.uuid4()),
        "offering": str(uuid.uuid4()),
    }
    print(f"Sprint 4 live proof run {run_id}")

    tokens = {
        "customer": generate_mock_jwt(ids["customer"], "CUSTOMER", run_id),
        "merchant": generate_mock_jwt(ids["merchant"], "MERCHANT", run_id),
        "captain1": generate_mock_jwt(ids["captain1"], "CAPTAIN", run_id),
        "captain2": generate_mock_jwt(ids["captain2"], "CAPTAIN", run_id),
    }

    clear_captain_locations()
    cleanup_and_seed(run_id, ids)
    sync_profile(tokens["customer"], ids["customer"], "CUSTOMER")
    sync_profile(tokens["merchant"], ids["merchant"], "MERCHANT")
    sync_profile(tokens["captain1"], ids["captain1"], "CAPTAIN")
    sync_profile(tokens["captain2"], ids["captain2"], "CAPTAIN")

    address_id = create_address(ids["customer"])
    onboard_captain(ids["captain1"], "first")
    onboard_captain(ids["captain2"], "second")
    go_online(ids["captain1"], "first", 77.2177, 28.6304)
    go_online(ids["captain2"], "second", 77.2180, 28.6306)

    status, order = request_json(
        "POST",
        f"{ORDER_BASE_URL}/api/v1/orders",
        {
            "customerId": ids["customer"],
            "providerId": ids["provider"],
            "deliveryAddressId": address_id,
            "items": [{"offeringId": ids["offering"], "quantity": 1}],
            "deliveryFee": 150.00,
            "discountAmount": 0.00,
        },
        extra_headers={"X-User-Id": ids["customer"]},
    )
    require(status == 201 and order["status"] == "PLACED", "customer order placed")
    order_id = order["orderId"]

    request_json(
        "PUT",
        f"{ORDER_BASE_URL}/api/v1/orders/{order_id}/status?status=ACCEPTED",
        extra_headers={"X-User-Id": ids["merchant"]},
    )
    status, ready = request_json(
        "PUT",
        f"{ORDER_BASE_URL}/api/v1/orders/{order_id}/status?status=READY_FOR_PICKUP",
        extra_headers={"X-User-Id": ids["merchant"]},
    )
    require(status == 200 and ready["status"] == "READY_FOR_PICKUP", "merchant marked order ready")

    first_offer = poll_offer(ids["captain1"], "first captain", order_id)
    expire_offer(first_offer["offerId"])
    print("  INFO  waiting for scheduler to expire first offer")
    time.sleep(7)
    second_offer = poll_offer(ids["captain2"], "second captain", order_id)
    require(second_offer["offerRank"] == 2, "expired offer reassigned to second captain", second_offer)

    status, accepted = request_json(
        "POST",
        f"{DISPATCH_BASE_URL}/api/v1/dispatch/offers/{second_offer['offerId']}/respond?response=ACCEPTED",
        {},
        extra_headers={"X-User-Id": ids["captain2"]},
    )
    require(status == 200 and accepted["response"] == "ACCEPTED", "second captain accepted offer")

    status, assigned = request_json("GET", f"{ORDER_BASE_URL}/api/v1/orders/{order_id}")
    require(status == 200 and assigned["status"] == "ASSIGNED" and assigned["captainId"] == ids["captain2"], "order assigned to accepting captain")

    job_id = second_offer["jobId"]
    status, _ = request_json(
        "POST",
        f"{DISPATCH_BASE_URL}/api/v1/dispatch/jobs/{job_id}/pickup",
        {"proofCode": "1234"},
        extra_headers={"X-User-Id": ids["captain2"]},
    )
    require(status == 200, "captain marked pickup through dispatch")
    status, picked = request_json("GET", f"{ORDER_BASE_URL}/api/v1/orders/{order_id}")
    require(status == 200 and picked["status"] == "PICKED_UP", "order moved to picked up")

    status, delivered_job = request_json(
        "POST",
        f"{DISPATCH_BASE_URL}/api/v1/dispatch/jobs/{job_id}/deliver",
        {"proofCode": "5678"},
        extra_headers={"X-User-Id": ids["captain2"]},
    )
    require(status == 200 and delivered_job["status"] == "COMPLETED", "captain delivered through dispatch")
    status, delivered = request_json("GET", f"{ORDER_BASE_URL}/api/v1/orders/{order_id}")
    require(status == 200 and delivered["status"] == "DELIVERED", "order moved to delivered")

    deadline = time.time() + 15
    earnings: list[dict[str, Any]] = []
    while time.time() < deadline:
        status, payload = request_json("GET", f"{CAPTAIN_BASE_URL}/api/v1/captains/{ids['captain2']}/earnings")
        earnings = payload if status == 200 else []
        if any(row["orderId"] == order_id for row in earnings):
            break
        time.sleep(1)
    require(any(row["orderId"] == order_id for row in earnings), "captain earning recorded after delivery", earnings)

    status, failed_jobs = request_json("GET", f"{DISPATCH_BASE_URL}/api/v1/dispatch/jobs?status=FAILED")
    require(status == 200 and isinstance(failed_jobs, list), "ops failed-job list endpoint available")

    print("\nSprint 4 live proof passed")
    print(json.dumps({"run_id": run_id, "order_id": order_id, "job_id": job_id, "captain_id": ids["captain2"]}, indent=2))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"\nSprint 4 live proof failed: {exc}", file=sys.stderr)
        sys.exit(1)
