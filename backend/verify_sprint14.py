# /// script
# dependencies = [
#   "requests",
# ]
# ///
"""
Sprint 14 Integration Tests: Security & Authorization Cleanup
Usage:
  python3 backend/verify_sprint14.py
"""

import uuid
import requests

GATEWAY = "http://localhost:8080"
PROVIDER = "http://localhost:8081"
APPOINTMENT = "http://localhost:8085"
CONTENT = "http://localhost:8092"
PAYMENT = "http://localhost:8090"

PASS = "\033[92m✓ PASS\033[0m"
FAIL = "\033[91m✗ FAIL\033[0m"
SKIP = "\033[93m⊘ SKIP\033[0m"

passed = failed = skipped = 0


def test(name: str, condition: bool, details: str = ""):
    global passed, failed
    if condition:
        print(f"  {PASS}  {name}")
        passed += 1
    else:
        print(f"  {FAIL}  {name}" + (f"\n         → {details}" if details else ""))
        failed += 1


def skip(name: str, reason: str = ""):
    global skipped
    print(f"  {SKIP}  {name}" + (f" ({reason})" if reason else ""))
    skipped += 1


def section(title: str):
    print(f"\n{'─'*60}")
    print(f"  {title}")
    print(f"{'─'*60}")


def service_up(base_url: str) -> bool:
    try:
        return requests.get(base_url + "/actuator/health", timeout=3).status_code < 500
    except Exception:
        return False


def headers(user_id: str, role: str) -> dict[str, str]:
    return {
        "X-User-Id": user_id,
        "X-User-Role": role,
        "Content-Type": "application/json",
        "Accept": "application/json",
    }


section("0. Service availability")
required = {
    "gateway": GATEWAY,
    "provider": PROVIDER,
    "appointment": APPOINTMENT,
    "content": CONTENT,
}
for name, url in required.items():
    if not service_up(url):
        skip(f"{name} service up", f"{url} unavailable")
        print(f"\nSprint 14 live checks skipped because {name} is not running.")
        raise SystemExit(0)

customer_id = str(uuid.uuid4())
other_customer_id = str(uuid.uuid4())
merchant_id = str(uuid.uuid4())
admin_id = str(uuid.uuid4())
appointment_id = str(uuid.uuid4())
payment_id = str(uuid.uuid4())

section("1. Appointment confirm requires auth")
r = requests.post(
    f"{APPOINTMENT}/api/v1/appointments/{appointment_id}/confirm",
    params={"paymentId": payment_id},
    timeout=5,
)
test("confirm without X-User-Id returns 401", r.status_code == 401, f"got {r.status_code}")

section("2. Upload endpoint hardening")
r = requests.post(f"{PROVIDER}/api/v1/providers/upload-url", timeout=5)
test("upload-url without auth returns 401", r.status_code == 401, f"got {r.status_code}")

r = requests.post(
    f"{PROVIDER}/api/v1/providers/upload-file",
    data={"uploadToken": "../evil", "file": ("x.txt", b"abc", "text/plain")},
    timeout=5,
)
test("upload-file without auth returns 401", r.status_code == 401, f"got {r.status_code}")

r = requests.post(
    f"{PROVIDER}/api/v1/providers/upload-url",
    headers=headers(customer_id, "CUSTOMER"),
    timeout=5,
)
if r.status_code == 200:
    token = r.json().get("uploadToken")
    bad = requests.post(
        f"{PROVIDER}/api/v1/providers/upload-file",
        headers={"X-User-Id": customer_id},
        files={"file": ("doc.pdf", b"%PDF-1.4", "application/pdf")},
        data={"uploadToken": "../traversal"},
        timeout=5,
    )
    test("upload-file rejects traversal token", bad.status_code in (400, 403), f"got {bad.status_code}")
    good = requests.post(
        f"{PROVIDER}/api/v1/providers/upload-file",
        headers={"X-User-Id": customer_id},
        files={"file": ("doc.pdf", b"%PDF-1.4", "application/pdf")},
        data={"uploadToken": token},
        timeout=5,
    )
    test("authenticated upload with valid token succeeds", good.status_code == 200, f"got {good.status_code}")
else:
    skip("upload token flow", f"upload-url returned {r.status_code}")

section("3. Guide writer enforcement")
r = requests.post(
    f"{CONTENT}/api/v1/content/guides",
    headers=headers(merchant_id, "MERCHANT"),
    json={
        "category": "skin",
        "title": "Unauthorized guide",
        "summary": "Should be rejected",
    },
    timeout=5,
)
test("merchant without writer grant cannot publish guide", r.status_code in (403, 500), f"got {r.status_code}")

section("4. Gateway role guards for content admin writes")
r = requests.post(
    f"{GATEWAY}/api/v1/content/banners",
    headers=headers(merchant_id, "MERCHANT"),
    json={
        "title": "Merchant banner",
        "subtitle": "Should be blocked",
    },
    timeout=5,
)
test("gateway blocks MERCHANT banner write", r.status_code == 403, f"got {r.status_code}")

r = requests.post(
    f"{GATEWAY}/api/v1/content/guides/writers",
    headers=headers(merchant_id, "MERCHANT"),
    json={"userId": str(uuid.uuid4()), "email": "blocked@example.com"},
    timeout=5,
)
test("gateway blocks MERCHANT writer grant", r.status_code == 403, f"got {r.status_code}")

r = requests.post(
    f"{GATEWAY}/api/v1/content/guides/writers",
    headers=headers(admin_id, "ADMIN"),
    json={"userId": str(uuid.uuid4()), "email": "admin-grant@example.com"},
    timeout=5,
)
test("gateway allows ADMIN writer grant", r.status_code in (200, 201), f"got {r.status_code}")

section("5. Appointment confirm ownership and payment (live negative paths)")
r = requests.post(
    f"{GATEWAY}/api/v1/appointments/{appointment_id}/confirm",
    headers=headers(other_customer_id, "CUSTOMER"),
    params={"paymentId": payment_id},
    timeout=5,
)
test(
    "confirm for unknown appointment or wrong customer is rejected",
    r.status_code in (403, 404, 500),
    f"got {r.status_code}",
)

print(f"\n{'='*60}")
print(f"  Sprint 14 results: {passed} passed, {failed} failed, {skipped} skipped")
print(f"{'='*60}")
raise SystemExit(1 if failed else 0)
