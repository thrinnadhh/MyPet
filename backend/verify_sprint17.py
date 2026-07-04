# /// script
# dependencies = [
#   "requests",
# ]
# ///
"""
Sprint 17 Integration Tests: Scale paths & admin surfaces
Usage:
  python3 backend/verify_sprint17.py
"""

import uuid

import requests

GATEWAY = "http://localhost:8080"
PROVIDER = "http://localhost:8081"

PASS = "\033[92m✓ PASS\033[0m"
FAIL = "\033[91m✗ FAIL\033[0m"
SKIP = "\033[93m⊘ SKIP\033[0m"

passed = failed = skipped = 0
ROOT = __import__("pathlib").Path(__file__).resolve().parents[1]


def has_text(path: str, *needles: str) -> bool:
    file_path = ROOT / path
    if not file_path.exists():
        return False
    text = file_path.read_text(errors="ignore")
    return all(needle in text for needle in needles)


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


section("0. Static wiring checks")
test(
    "k6 script covers order dispatch payment path",
    has_text(
        "load-tests/k6/discovery-appointments-catalog.js",
        "/api/v1/orders",
        "/api/v1/dispatch/jobs/by-order/",
        "/api/v1/payments/orders",
    ),
)
test(
    "super-admin banner auction tab wired",
    has_text(
        "apps/super-admin-web/app.js",
        "fetchBannerAuctionOutcomes",
        "/api/v1/content/banners/auction-outcomes",
    ),
)
test(
    "gateway protects banner bid submission",
    has_text(
        "backend/api-gateway/src/main/resources/application.yml",
        "content-service-banner-bids",
        "/api/v1/content/banners/bids",
        "roles: MERCHANT,ADMIN",
    ),
)
test(
    "gateway protects auction outcomes for admin",
    has_text(
        "backend/api-gateway/src/main/resources/application.yml",
        "content-service-auction-outcomes",
        "/api/v1/content/banners/auction-outcomes",
        "roles: ADMIN",
    ),
)
test(
    "locale endpoints exist on provider service",
    has_text(
        "backend/provider-service/src/main/kotlin/com/pawsnearme/providerservice/controller/PreferenceController.kt",
        '@GetMapping("/profiles/me/locale")',
        '@PatchMapping("/profiles/me/locale")',
    ),
)

gateway_up = service_up(GATEWAY)
provider_up = service_up(PROVIDER)

if not gateway_up:
    skip("gateway live checks", f"{GATEWAY} unavailable")
    print(f"\n{'='*60}")
    print(f"  Sprint 17 results: {passed} passed, {failed} failed, {skipped} skipped")
    print(f"{'='*60}")
    raise SystemExit(1 if failed else 0)

customer_id = str(uuid.uuid4())
merchant_id = str(uuid.uuid4())
admin_id = str(uuid.uuid4())
provider_id = str(uuid.uuid4())

section("1. Banner bid gateway role guard")
r = requests.post(
    f"{GATEWAY}/api/v1/content/banners/bids",
    headers=headers(customer_id, "CUSTOMER"),
    json={
        "providerId": provider_id,
        "slotOrder": 1,
        "bidAmount": 100.0,
        "windowEndsAt": "2099-12-31T23:59:59Z",
    },
    timeout=5,
)
test("CUSTOMER blocked from banner bid submission", r.status_code == 403, f"got {r.status_code}")

r = requests.post(
    f"{GATEWAY}/api/v1/content/banners/bids",
    headers=headers(merchant_id, "MERCHANT"),
    json={
        "providerId": provider_id,
        "slotOrder": 1,
        "bidAmount": 100.0,
        "windowEndsAt": "2099-12-31T23:59:59Z",
    },
    timeout=5,
)
test(
    "MERCHANT passes gateway role guard for banner bid",
    r.status_code != 403,
    f"got {r.status_code}",
)

section("2. Auction outcomes admin guard")
r = requests.get(
    f"{GATEWAY}/api/v1/content/banners/auction-outcomes",
    headers={"X-User-Role": "CUSTOMER"},
    timeout=5,
)
test("CUSTOMER blocked from auction outcomes", r.status_code == 403, f"got {r.status_code}")

r = requests.get(
    f"{GATEWAY}/api/v1/content/banners/auction-outcomes",
    headers={"X-User-Role": "ADMIN"},
    timeout=5,
)
test("ADMIN can read auction outcomes", r.status_code == 200, f"got {r.status_code}")

section("3. Locale GET/PATCH")
if not provider_up:
    skip("locale live round-trip", f"{PROVIDER} unavailable")
else:
    user_id = str(uuid.uuid4())
    r = requests.get(
        f"{GATEWAY}/api/v1/profiles/me/locale",
        headers={"X-User-Id": user_id},
        timeout=5,
    )
    if r.status_code == 404:
        skip("locale GET/PATCH round-trip", "profile not seeded for random user")
    else:
        test("locale GET returns 200 for existing profile", r.status_code == 200, f"got {r.status_code}")
        if r.status_code == 200:
            r_patch = requests.patch(
                f"{GATEWAY}/api/v1/profiles/me/locale",
                headers=headers(user_id, "CUSTOMER"),
                json={"locale": "hi-IN"},
                timeout=5,
            )
            test("locale PATCH updates preferred locale", r_patch.status_code == 200, f"got {r_patch.status_code}")
            if r_patch.status_code == 200:
                test(
                    "locale PATCH response echoes hi-IN",
                    r_patch.json().get("locale") == "hi-IN",
                    str(r_patch.json()),
                )

print(f"\n{'='*60}")
print(f"  Sprint 17 results: {passed} passed, {failed} failed, {skipped} skipped")
print(f"{'='*60}")
raise SystemExit(1 if failed else 0)
