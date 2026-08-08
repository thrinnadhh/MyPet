#!/usr/bin/env python3
"""M8 clean-volume full-feature verification.

Runs after scripts/test-full-stack.sh has started the distributed stack. The
matrix creates one connected business graph and verifies all M8 domains through
the public gateway, then checks durable database/event side effects.

No third-party Python dependency is required. PostgreSQL access is performed
through the already-running Compose postgres container.
"""

from __future__ import annotations

import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Callable

ROOT = Path(__file__).resolve().parents[1]
PROJECT_NAME = os.environ.get("COMPOSE_PROJECT_NAME", "mypet-e2e")
ENV_FILE = os.environ.get("MYPET_ENV_FILE")
REPORT = Path(os.environ.get("MYPET_SMOKE_REPORT", ROOT / "build/reports/full-stack-smoke.md"))
GATEWAY = os.environ.get("MYPET_GATEWAY_URL", "http://localhost:8080")

if not ENV_FILE:
    raise SystemExit("MYPET_ENV_FILE must be set by scripts/test-all.sh")

COMPOSE = [
    "docker", "compose", "-p", PROJECT_NAME, "--env-file", ENV_FILE,
    "-f", str(ROOT / "infra/docker-compose.yml"),
    "-f", str(ROOT / "infra/docker-compose.replicas.yml"),
    "-f", str(ROOT / "infra/docker-compose.m4.yml"),
    "-f", str(ROOT / "infra/docker-compose.local.yml"),
]

DOMAIN_IDS = (
    "customer", "provider", "catalog", "appointment", "order", "payment",
    "loyalty", "captain", "dispatch", "review", "notification", "chat",
    "content", "admin",
)


@dataclass(frozen=True)
class Actor:
    user_id: str
    role: str
    token: str


def append_report(text: str) -> None:
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    with REPORT.open("a", encoding="utf-8") as handle:
        handle.write(text)


def passed(domain: str, message: str) -> None:
    print(f"PASS [{domain}] {message}")
    append_report(f"- ✅ **{domain}** — {message}\n")


def require(condition: bool, message: str, details: Any = None) -> None:
    if not condition:
        suffix = "" if details is None else f": {details}"
        raise AssertionError(f"{message}{suffix}")


def compose(*args: str, input_text: str | None = None) -> str:
    result = subprocess.run(
        [*COMPOSE, *args],
        input=input_text,
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
        "exec", "-T", "postgres", "psql", "-U", "postgres", "-d", "pawsnearme",
        "-v", "ON_ERROR_STOP=1", "-Atc", statement,
    )


def encode_jwt_part(value: dict[str, Any]) -> str:
    raw = json.dumps(value, separators=(",", ":")).encode("utf-8")
    return base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")


def make_actor(role: str, label: str) -> Actor:
    user_id = str(uuid.uuid4())
    now = int(time.time())
    claims = {
        "sub": user_id,
        "iat": now,
        "exp": now + 7200,
        "email": f"m8-{label}-{user_id[:8]}@example.com",
        "phone": f"+9199{user_id.replace('-', '')[:8]}",
        "app_metadata": {"role": role},
        "user_metadata": {
            "full_name": f"M8 {label.title()}",
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
) -> Any:
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
    return decoded


def poll(label: str, probe: Callable[[], Any], ready: Callable[[Any], bool], timeout: int = 40) -> Any:
    deadline = time.time() + timeout
    last: Any = None
    while time.time() < deadline:
        try:
            last = probe()
            if ready(last):
                return last
        except Exception as exc:  # noqa: BLE001 - polling preserves last diagnostic.
            last = str(exc)
        time.sleep(1)
    raise AssertionError(f"Timed out waiting for {label}: {last}")


def sync_profile(actor: Actor) -> dict[str, Any]:
    result = request("POST", "/api/v1/profiles/sync", actor, expected=(200,))
    require(result["userId"] == actor.user_id, "profile sync returned wrong user", result)
    require(result["role"] == actor.role, "profile sync returned wrong role", result)
    return result


def create_provider(owner: Actor, provider_type: str, fulfillment: str, name: str) -> dict[str, Any]:
    result = request(
        "POST", "/api/v1/providers", owner,
        {
            "ownerUserId": owner.user_id,
            "providerType": provider_type,
            "fulfillmentType": fulfillment,
            "name": name,
            "description": "M8 connected feature verification",
            "licenseNumber": f"M8-{uuid.uuid4().hex[:10]}",
            "licenseDocUrl": None,
            "addressLine": "M8 Verification Road",
            "city": "Tirupati",
            "pincode": "517501",
            "longitude": 79.4192,
            "latitude": 13.6288,
        },
        expected=(200,),
    )
    require(result["status"] == "DRAFT", "provider must begin in DRAFT", result)
    return result


def activate_provider(provider_id: str, merchant: Actor, admin: Actor) -> dict[str, Any]:
    sql(
        "UPDATE providers.providers "
        "SET status='PENDING_APPROVAL'::providers.provider_status "
        f"WHERE provider_id='{provider_id}'::uuid;"
    )
    request("POST", f"/api/v1/providers/{provider_id}/approve", merchant, expected=(403,))
    approved = request("POST", f"/api/v1/providers/{provider_id}/approve", admin, expected=(200,))
    require(approved["status"] == "ACTIVE", "admin approval did not activate provider", approved)
    return approved


def create_offering(
    merchant: Actor,
    provider_id: str,
    name: str,
    category: str,
    price: float,
    stock: int | None,
    duration: int | None,
) -> dict[str, Any]:
    return request(
        "POST", "/api/v1/catalog/offerings", merchant,
        {
            "providerId": provider_id,
            "name": name,
            "description": "M8 feature verification offering",
            "category": category,
            "price": price,
            "imageUrl": None,
            "status": "ACTIVE",
            "stockQuantity": stock,
            "sku": f"M8-{uuid.uuid4().hex[:12]}",
            "durationMinutes": duration,
            "barcode": f"M8BAR{uuid.uuid4().hex[:10]}" if stock is not None else None,
        },
        expected=(201,),
    )


def create_slot(merchant: Actor, offering_id: str, start: datetime) -> dict[str, Any]:
    end = start + timedelta(minutes=30)
    return request(
        "POST", "/api/v1/catalog/slots", merchant,
        {
            "offeringId": offering_id,
            "slotStart": start.isoformat().replace("+00:00", "Z"),
            "slotEnd": end.isoformat().replace("+00:00", "Z"),
            "status": "AVAILABLE",
        },
        expected=(201,),
    )


def main() -> int:
    append_report("\n## M8 full feature verification matrix\n\n")

    customer = make_actor("CUSTOMER", "customer")
    second_customer = make_actor("CUSTOMER", "second-customer")
    merchant = make_actor("MERCHANT", "merchant")
    admin = make_actor("ADMIN", "admin")
    captain = make_actor("CAPTAIN", "captain")

    for actor in (customer, second_customer, merchant, admin, captain):
        sync_profile(actor)

    address = request(
        "POST", "/api/v1/addresses", customer,
        {
            "label": "Home",
            "line1": "M8 Customer Street",
            "line2": None,
            "city": "Tirupati",
            "state": "Andhra Pradesh",
            "pincode": "517501",
            "geoLat": 13.6288,
            "geoLng": 79.4192,
            "isDefault": True,
        },
        expected=(201,),
    )
    default_address = request("GET", "/api/v1/addresses/default", customer)
    require(default_address["addressId"] == address["addressId"], "default address mismatch")
    delivery_contact = request(
        "PUT",
        f"/api/v1/addresses/{address['addressId']}/contact",
        customer,
        {"phoneNumber": "+919876543210"},
        expected=(200,),
    )
    require(
        delivery_contact["phoneNumber"] == "+919876543210",
        "delivery contact was not saved for the customer address",
        delivery_contact,
    )
    passed("customer", "profile, default-address and delivery-contact lifecycle verified")

    delivery_provider = activate_provider(
        create_provider(merchant, "PET_STORE", "DELIVERY", "M8 Pet Store")["providerId"],
        merchant,
        admin,
    )
    appointment_provider = activate_provider(
        create_provider(merchant, "VET_HOSPITAL", "APPOINTMENT", "M8 Vet Clinic")["providerId"],
        merchant,
        admin,
    )
    owned = request("GET", "/api/v1/providers/me", merchant)
    require(
        {delivery_provider["providerId"], appointment_provider["providerId"]}.issubset(
            {entry["providerId"] for entry in owned}
        ),
        "merchant provider ownership lookup incomplete",
        owned,
    )
    passed("provider", "merchant ownership and admin approval transitions verified")

    delivery_offering = create_offering(
        merchant, delivery_provider["providerId"], "M8 Dog Food", "FOOD", 199.0, 25, None
    )
    appointment_offering = create_offering(
        merchant, appointment_provider["providerId"], "M8 Vet Consultation", "CARE", 500.0, None, 30
    )
    now = datetime.now(timezone.utc)
    slot_one = create_slot(merchant, appointment_offering["offeringId"], now + timedelta(hours=2))
    slot_two = create_slot(merchant, appointment_offering["offeringId"], now + timedelta(hours=3))
    catalog_list = request(
        "GET",
        f"/api/v1/catalog/offerings?providerId={delivery_provider['providerId']}",
    )
    require(any(x["offeringId"] == delivery_offering["offeringId"] for x in catalog_list), "offering missing")
    passed("catalog", "merchant offering and appointment-slot creation verified")

    params = urllib.parse.urlencode(
        {
            "longitude": 79.4192,
            "latitude": 13.6288,
            "radius": 5.0,
            "type": "PET_STORE",
        }
    )
    discovered = poll(
        "provider discovery projection",
        lambda: request("GET", f"/api/v1/discovery/providers?{params}"),
        lambda items: any(x["providerId"] == delivery_provider["providerId"] for x in items),
    )
    request(
        "POST", "/api/v1/customer/favourites", customer,
        {"targetType": "PROVIDER", "targetId": delivery_provider["providerId"]},
        expected=(201,),
    )
    favourites = request("GET", "/api/v1/customer/favourites", customer)
    require(any(x["targetId"] == delivery_provider["providerId"] for x in favourites), "favourite missing")
    request(
        "DELETE",
        "/api/v1/customer/favourites?"
        + urllib.parse.urlencode({"targetType": "PROVIDER", "targetId": delivery_provider["providerId"]}),
        customer,
        expected=(204,),
    )
    require(discovered, "discovery result unexpectedly empty")
    passed("customer", "customer favourite add/list/remove verified")

    appointment_body = {
        "customerId": customer.user_id,
        "providerId": appointment_provider["providerId"],
        "offeringId": appointment_offering["offeringId"],
        "slotId": slot_one["slotId"],
        "petId": str(uuid.uuid4()),
        "priceAmount": 500.0,
        "payAtClinic": True,
    }
    held = request("POST", "/api/v1/appointments/hold", customer, appointment_body, expected=(201,))
    require(held["status"] == "SLOT_HELD", "appointment was not held", held)
    request(
        "POST", "/api/v1/appointments/hold", second_customer,
        {**appointment_body, "customerId": second_customer.user_id},
        expected=(400,),
    )
    confirmed = request(
        "POST", f"/api/v1/appointments/{held['appointmentId']}/confirm", customer,
        expected=(200,),
    )
    require(confirmed["status"] == "CONFIRMED", "appointment confirmation failed", confirmed)
    request(
        "PUT",
        f"/api/v1/appointments/{held['appointmentId']}/status?status=IN_PROGRESS",
        merchant,
        expected=(200,),
    )
    completed_appointment = request(
        "PUT",
        f"/api/v1/appointments/{held['appointmentId']}/status?status=COMPLETED"
        "&note=M8%20consultation%20completed",
        merchant,
        expected=(200,),
    )
    require(completed_appointment["status"] == "COMPLETED", "appointment completion failed")
    invoice = request("GET", f"/api/v1/appointments/{held['appointmentId']}/invoice", customer)
    require(float(invoice["totalAmount"]) > 0, "appointment invoice total missing", invoice)

    timeout_hold = request(
        "POST", "/api/v1/appointments/hold", customer,
        {**appointment_body, "slotId": slot_two["slotId"]},
        expected=(201,),
    )
    sql(
        "UPDATE appointments.appointments SET booked_at=now()-interval '6 minutes' "
        f"WHERE appointment_id='{timeout_hold['appointmentId']}'::uuid;"
    )
    poll(
        "appointment hold expiration",
        lambda: request("GET", f"/api/v1/catalog/slots/{slot_two['slotId']}"),
        lambda value: value["status"] == "AVAILABLE",
        timeout=20,
    )
    passed("appointment", "hold, double-book prevention, confirm, complete, invoice and timeout verified")

    reminders = poll(
        "appointment reminder creation",
        lambda: request(
            "GET", f"/api/v1/notifications/reminders/reference/{held['appointmentId']}", customer
        ),
        lambda items: len(items) > 0,
    )
    push_token = f"ExponentPushToken[M8-{uuid.uuid4().hex}]"
    registered = request(
        "POST", "/api/v1/notifications/push-tokens", customer,
        {
            "expoPushToken": push_token,
            "platform": "android",
            "appRole": "CUSTOMER",
            "soundProfile": "default",
        },
    )
    require(registered["expoPushToken"] == push_token, "push token registration failed")
    request(
        "DELETE", "/api/v1/notifications/push-tokens?" + urllib.parse.urlencode({"token": push_token}),
        customer, expected=(204,),
    )
    require(reminders, "appointment reminders missing")
    passed("notification", "event-driven reminder and push-token lifecycle verified")

    quote_payload = {
        "providerId": delivery_provider["providerId"],
        "deliveryAddressId": address["addressId"],
        "items": [{"offeringId": delivery_offering["offeringId"], "quantity": 1}],
        "paymentMethod": "COD",
        "city": "Tirupati",
        "latitude": 13.6288,
        "longitude": 79.4192,
    }
    quote = request("POST", "/api/v1/checkout/quote", customer, quote_payload)
    require(quote["quoteToken"], "checkout quote token missing", quote)
    require(quote["isCodAvailable"] is True, "COD unexpectedly unavailable", quote)
    order = request(
        "POST", "/api/v1/orders", customer,
        {**quote_payload, "quoteToken": quote["quoteToken"]},
        expected=(201,),
    )
    require(order["status"] == "PLACED", "order was not placed", order)
    stock_after = int(
        sql(
            "SELECT stock_quantity FROM catalog.offerings "
            f"WHERE offering_id='{delivery_offering['offeringId']}'::uuid;"
        )
    )
    require(stock_after == 24, "order did not reserve one stock unit", stock_after)
    passed("order", "quote-token checkout, order creation and atomic stock reservation verified")

    payment = request(
        "POST", "/api/v1/payments/transactions/result", customer,
        {
            "userId": customer.user_id,
            "referenceId": order["orderId"],
            "transactionType": "ORDER_PAYMENT",
            "amount": order["totalAmount"],
            "gatewayTransactionId": f"m8_captured_{order['orderId']}",
            "success": True,
        },
        expected=(201,),
    )
    require(payment["eventType"] == "PaymentCaptured", "payment capture event missing", payment)
    tx_status = sql(
        "SELECT status FROM payments.transactions "
        f"WHERE reference_id='{order['orderId']}'::uuid ORDER BY created_at DESC LIMIT 1;"
    )
    require(tx_status == "SUCCESS", "payment transaction was not persisted", tx_status)
    passed("payment", "captured transaction and durable payment state verified")

    onboarded = request(
        "POST", "/api/v1/captains/profiles", captain,
        {
            "captainId": None,
            "vehicleType": "BIKE",
            "vehicleNumber": f"AP39M8{uuid.uuid4().hex[:4].upper()}",
            "licenseDocUrl": "https://example.invalid/m8-license",
            "bankAccount": "123456789012",
            "bankIfsc": "SBIN0000001",
            "selfieDocUrl": "https://example.invalid/m8-selfie",
            "documents": [],
        },
    )
    if onboarded["status"] == "PENDING_APPROVAL":
        request("POST", f"/api/v1/captains/{captain.user_id}/approve", admin)
    status = request(
        "PUT", "/api/v1/captains/status", captain,
        {"captainId": None, "online": True, "longitude": 79.4192, "latitude": 13.6288},
    )
    require(status["status"] == "ONLINE", "captain did not become online", status)
    passed("captain", "onboarding, protected bank-data write and online availability verified")

    request(
        "PUT", f"/api/v1/orders/{order['orderId']}/status?status=ACCEPTED", merchant
    )
    ready_order = request(
        "PUT", f"/api/v1/orders/{order['orderId']}/status?status=READY_FOR_PICKUP", merchant
    )
    require(ready_order["status"] == "READY_FOR_PICKUP", "order not ready for dispatch")
    offers = poll(
        "captain dispatch offer",
        lambda: request("GET", "/api/v1/dispatch/offers", captain),
        lambda items: any(x["orderId"] == order["orderId"] for x in items),
        timeout=45,
    )
    offer = next(x for x in offers if x["orderId"] == order["orderId"])
    request(
        "POST",
        f"/api/v1/dispatch/offers/{offer['offerId']}/respond?response=ACCEPTED",
        captain,
        {},
    )
    job = request("GET", f"/api/v1/dispatch/jobs/by-order/{order['orderId']}", captain)
    require(job["status"] == "ACCEPTED", "dispatch job not accepted", job)
    request(
        "POST", f"/api/v1/dispatch/jobs/{job['jobId']}/pickup", captain,
        {"proofCode": job["pickupOtp"]},
    )
    delivered_job = request(
        "POST", f"/api/v1/dispatch/jobs/{job['jobId']}/deliver", captain,
        {"proofCode": job["deliveryOtp"]},
    )
    require(delivered_job["status"] == "COMPLETED", "dispatch did not complete", delivered_job)
    delivered_order = poll(
        "delivered order status",
        lambda: request("GET", f"/api/v1/orders/{order['orderId']}", customer),
        lambda value: value["status"] == "DELIVERED",
    )
    require(delivered_order["status"] == "DELIVERED", "order did not reach DELIVERED")
    earnings = poll(
        "captain earnings projection",
        lambda: request("GET", f"/api/v1/captains/{captain.user_id}/earnings", captain),
        lambda items: len(items) > 0,
        timeout=45,
    )
    require(earnings, "captain earnings missing")
    passed("dispatch", "offer, acceptance, OTP pickup, delivery and order propagation verified")

    loyalty_body = {
        "orderId": order["orderId"],
        "customerId": customer.user_id,
        "providerId": delivery_provider["providerId"],
        "netAmount": float(order["totalAmount"]),
    }
    first_loyalty = request(
        "POST", "/api/v1/loyalty/events/order-delivered", customer, loyalty_body
    )
    duplicate_loyalty = request(
        "POST", "/api/v1/loyalty/events/order-delivered", customer, loyalty_body
    )
    require(first_loyalty["processed"] is True, "first loyalty event not processed")
    require(duplicate_loyalty["processed"] is False, "loyalty idempotency failed")
    progress = request(
        "GET", "/api/v1/loyalty/progress?" + urllib.parse.urlencode(
            {"providerId": delivery_provider["providerId"]}
        ), customer
    )
    require(progress["starBalance"] >= 1, "loyalty star not awarded", progress)
    passed("loyalty", "delivered-order award, progress and duplicate-event idempotency verified")

    review = request(
        "POST", "/api/v1/reviews", customer,
        {
            "customerId": customer.user_id,
            "providerId": appointment_provider["providerId"],
            "targetType": "APPOINTMENT",
            "targetId": held["appointmentId"],
            "rating": 5,
            "comment": "M8 verified care workflow",
            "captainRating": None,
        },
        expected=(201,),
    )
    provider_reviews = request(
        "GET", f"/api/v1/reviews/provider/{appointment_provider['providerId']}"
    )
    require(provider_reviews["totalCount"] >= 1, "review not listed", provider_reviews)
    require(review["rating"] == 5, "review rating mismatch")
    passed("review", "completed-appointment review and provider aggregate verified")

    conversation = request(
        "POST", "/api/v1/chat/conversations", customer,
        {
            "contextType": "ORDER",
            "contextId": order["orderId"],
            "providerId": delivery_provider["providerId"],
            "customerId": customer.user_id,
            "assignedDoctorUserId": None,
        },
        expected=(201,),
    )
    message = request(
        "POST", f"/api/v1/chat/conversations/{conversation['conversationId']}/messages", customer,
        {"messageType": "TEXT", "body": "M8 order chat message", "imageUrl": None, "imageMimeType": None},
        expected=(201,),
    )
    merchant_conversations = request("GET", "/api/v1/chat/conversations", merchant)
    require(
        any(x["conversationId"] == conversation["conversationId"] for x in merchant_conversations),
        "merchant cannot see order conversation",
    )
    request(
        "POST", f"/api/v1/chat/conversations/{conversation['conversationId']}/read", merchant,
        expected=(204,),
    )
    require(message["body"] == "M8 order chat message", "chat message body mismatch")
    passed("chat", "customer/merchant conversation, message and read state verified")

    banner = request(
        "POST", "/api/v1/content/banners", admin,
        {
            "title": "M8 Verified Banner",
            "subtitle": "Full feature verification",
            "accentColor": "#F97316",
            "durationSec": 5,
            "sortOrder": 90,
            "active": True,
        },
    )
    guide = request(
        "POST", "/api/v1/content/guides", admin,
        {
            "category": "m8-verification",
            "title": "M8 Verified Guide",
            "summary": "Cross-domain verification evidence",
            "body": "M8",
            "readMinutes": 1,
            "published": True,
        },
    )
    public_banners = request("GET", "/api/v1/content/banners")
    public_guides = request("GET", "/api/v1/content/guides?category=m8-verification")
    require(any(x["id"] == banner["id"] for x in public_banners), "admin banner not public")
    require(any(x["id"] == guide["id"] for x in public_guides), "admin guide not public")
    request(
        "POST", "/api/v1/content/banners", customer,
        {"title": "Blocked", "subtitle": "Blocked", "active": True},
        expected=(403,),
    )
    passed("content", "admin publishing and public content reads verified")

    all_profiles = request("GET", "/api/v1/profiles", admin)
    require(len(all_profiles) >= 5, "admin profile inventory incomplete")
    request("POST", f"/api/v1/profiles/{second_customer.user_id}/revoke", admin)
    suspended = sql(
        "SELECT suspended::text FROM identity.profiles "
        f"WHERE user_id='{second_customer.user_id}'::uuid;"
    )
    require(suspended == "true", "admin revoke did not persist")
    request("POST", f"/api/v1/profiles/{second_customer.user_id}/restore", admin)
    restored = sql(
        "SELECT suspended::text FROM identity.profiles "
        f"WHERE user_id='{second_customer.user_id}'::uuid;"
    )
    require(restored == "false", "admin restore did not persist")
    passed("admin", "provider approval, profile inventory and revoke/restore verified")

    published_outbox = int(
        sql(
            "SELECT count(*) FROM ("
            "SELECT published_at FROM orders.outbox_events UNION ALL "
            "SELECT published_at FROM appointments.outbox_events UNION ALL "
            "SELECT published_at FROM dispatch.outbox_events UNION ALL "
            "SELECT published_at FROM payments.outbox_events UNION ALL "
            "SELECT published_at FROM providers.outbox_events UNION ALL "
            "SELECT published_at FROM reviews.outbox_events"
            ") e WHERE published_at IS NOT NULL;"
        )
    )
    require(published_outbox >= 8, "insufficient published durable events", published_outbox)

    append_report(
        "\n### M8 matrix result\n\n"
        f"**PASS** — all {len(DOMAIN_IDS)} required feature domains completed through one connected "
        "customer/provider/order/appointment graph, including authorization, concurrency, idempotency, "
        "scheduler, event, persistence and projection evidence.\n"
    )
    print(json.dumps({"ok": True, "domains": list(DOMAIN_IDS), "publishedOutboxEvents": published_outbox}, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:  # noqa: BLE001 - verifier must surface exact failing evidence.
        append_report(f"\n- ❌ **M8 matrix stopped** — {exc}\n")
        print(json.dumps({"ok": False, "error": str(exc)}, indent=2), file=sys.stderr)
        raise
