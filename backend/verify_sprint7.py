# /// script
# dependencies = [
#   "psycopg2-binary",
#   "requests",
# ]
# ///
"""
Sprint 7 Integration Tests: Payouts, Ratings & promotions (Discount War Prevention)
Usage:
  python3 backend/verify_sprint7.py
"""

import sys
import time
import uuid
import psycopg2
import requests

# ─── Config ───────────────────────────────────────────────────────────────────

DB_URL      = "host=localhost port=5433 dbname=pawsnearme user=postgres password=postgres"
GATEWAY     = "http://localhost:8080"
PAYMENT_SVC = "http://localhost:8090"
PROVIDER_SVC= "http://localhost:8081"
REVIEW_SVC  = "http://localhost:8089"

PASS = "\033[92m✓ PASS\033[0m"
FAIL = "\033[91m✗ FAIL\033[0m"
SKIP = "\033[93m⊘ SKIP\033[0m"

passed = 0
failed = 0
skipped = 0

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

# ─── Helper: check if service is UP ───────────────────────────────────────────

def service_up(base_url: str, path: str = "/actuator/health") -> bool:
    try:
        r = requests.get(base_url + path, timeout=3)
        return r.status_code < 500
    except Exception:
        return False

# ─── 1. DB Schema Tests ────────────────────────────────────────────────────────

section("1. Database Schema Validation")

try:
    conn = psycopg2.connect(DB_URL)
    conn.autocommit = True
    cur = conn.cursor()

    # Check payments schema exists
    cur.execute("SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'payments'")
    test("payments schema exists", cur.fetchone() is not None)

    # Check transactions table
    cur.execute("""
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = 'payments' AND table_name = 'transactions'
    """)
    col_count = cur.fetchone()[0]
    test("payments.transactions table has columns", col_count >= 10,
         f"found {col_count} columns, expected ≥10")

    # Check payouts table
    cur.execute("""
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = 'payments' AND table_name = 'payouts'
    """)
    col_count = cur.fetchone()[0]
    test("payments.payouts table has columns", col_count >= 8,
         f"found {col_count} columns, expected ≥8")

    # Check promotions table
    cur.execute("""
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = 'payments' AND table_name = 'promotions'
    """)
    col_count = cur.fetchone()[0]
    test("payments.promotions table has columns", col_count >= 13,
         f"found {col_count} columns, expected ≥13")

    # Check applicable_category column exists in payments.promotions
    cur.execute("""
        SELECT column_name FROM information_schema.columns
        WHERE table_schema = 'payments' AND table_name = 'promotions' AND column_name = 'applicable_category'
    """)
    test("promotions table has 'applicable_category' column", cur.fetchone() is not None)

    cur.close()
    conn.close()

except Exception as e:
    print(f"  {FAIL}  DB connection failed: {e}")
    failed += 1


# ─── 2. Payment Service (Promotions & Payouts) Tests ─────────────────────────

section("2. Payment Service (port 8090)")

if not service_up(PAYMENT_SVC, "/actuator/health"):
    skip("All payment-service tests", "service not running on port 8090")
    skipped += 7
else:
    merchant_user_id = str(uuid.uuid4())
    merchant_provider_id = str(uuid.uuid4())
    try:
        conn = psycopg2.connect(DB_URL)
        conn.autocommit = True
        cur = conn.cursor()
        cur.execute("""
            INSERT INTO providers.providers (
                provider_id, owner_user_id, provider_type, fulfillment_type, name,
                address_line, city, pincode, geo_location, status, rating_avg, rating_count
            )
            VALUES (
                %s, %s, 'PET_STORE', 'DELIVERY', 'Sprint 7 Promo Store',
                '12 Coupon Road', 'Hyderabad', '500001',
                ST_GeomFromText('POINT(78.4867 17.3850)', 4326), 'ACTIVE', 0.00, 0
            )
            ON CONFLICT (provider_id) DO NOTHING
        """, (merchant_provider_id, merchant_user_id))
        cur.close()
        conn.close()
    except Exception as e:
        print(f"  {FAIL}  Could not seed merchant-owned provider: {e}")
        failed += 1

    merchant_headers = {"X-User-Role": "MERCHANT", "X-User-Id": merchant_user_id}
    admin_headers = {"X-User-Role": "ADMIN", "X-User-Id": str(uuid.uuid4())}

    # A. Test Flat promotion: flat discounts cannot exceed 30% of min order value
    payload_invalid_flat = {
        "code": "FLAT_INVALID",
        "discountType": "FLAT",
        "discountValue": 50.00,
        "minOrderValue": 100.00,
        "validFrom": "2026-06-01T00:00:00Z",
        "validUntil": "2026-12-31T23:59:59Z",
        "providerId": merchant_provider_id
    }
    r = requests.post(f"{PAYMENT_SVC}/api/v1/payments/promotions", json=payload_invalid_flat, headers=merchant_headers)
    test("Creating flat discount > 30% min_order_value is rejected (400)", r.status_code == 400, f"got {r.status_code}: {r.text}")

    # B. Test Percentage promotion: percentage discount cannot exceed 30%
    payload_invalid_pct = {
        "code": "PERCENT_INVALID",
        "discountType": "PERCENTAGE",
        "discountValue": 35.00,
        "validFrom": "2026-06-01T00:00:00Z",
        "validUntil": "2026-12-31T23:59:59Z",
        "providerId": merchant_provider_id
    }
    r = requests.post(f"{PAYMENT_SVC}/api/v1/payments/promotions", json=payload_invalid_pct, headers=merchant_headers)
    test("Creating percentage discount > 30% is rejected (400)", r.status_code == 400, f"got {r.status_code}: {r.text}")

    # C. Test Platform-wide promotion requires ADMIN
    payload_platform = {
        "code": "PLATFORM_MOCK",
        "discountType": "PERCENTAGE",
        "discountValue": 10.00,
        "validFrom": "2026-06-01T00:00:00Z",
        "validUntil": "2026-12-31T23:59:59Z"
    }
    r = requests.post(f"{PAYMENT_SVC}/api/v1/payments/promotions", json=payload_platform, headers=merchant_headers)
    test("Creating platform-wide coupon by MERCHANT is forbidden (400/403)", r.status_code in [400, 403], f"got {r.status_code}: {r.text}")

    # C2. Merchant cannot create coupon for another provider
    payload_other_provider = {
        "code": f"OTHER_{uuid.uuid4().hex[:6].upper()}",
        "discountType": "PERCENTAGE",
        "discountValue": 10.00,
        "validFrom": "2026-06-01T00:00:00Z",
        "validUntil": "2026-12-31T23:59:59Z",
        "providerId": str(uuid.uuid4())
    }
    r = requests.post(f"{PAYMENT_SVC}/api/v1/payments/promotions", json=payload_other_provider, headers=merchant_headers)
    test("Merchant coupon for unowned provider is rejected (400)", r.status_code == 400, f"got {r.status_code}: {r.text}")

    # D. Test Valid promotion creation
    promo_code = f"PROMO_{uuid.uuid4().hex[:6].upper()}"
    provider_id = merchant_provider_id
    payload_valid = {
        "code": promo_code,
        "discountType": "FLAT",
        "discountValue": 20.00,
        "minOrderValue": 100.00,
        "validFrom": "2026-06-01T00:00:00Z",
        "validUntil": "2026-12-31T23:59:59Z",
        "applicableCategory": "Drools",
        "providerId": provider_id
    }
    r = requests.post(f"{PAYMENT_SVC}/api/v1/payments/promotions", json=payload_valid, headers=merchant_headers)
    test("Creating valid promotion returns 201", r.status_code == 201, f"got {r.status_code}: {r.text}")

    # D2. Admin can create platform-wide coupon
    admin_code = f"PLATFORM_{uuid.uuid4().hex[:6].upper()}"
    payload_admin_platform = {
        "code": admin_code,
        "discountType": "PERCENTAGE",
        "discountValue": 10.00,
        "validFrom": "2026-06-01T00:00:00Z",
        "validUntil": "2026-12-31T23:59:59Z"
    }
    r = requests.post(f"{PAYMENT_SVC}/api/v1/payments/promotions", json=payload_admin_platform, headers=admin_headers)
    test("Admin can create platform-wide coupon (201)", r.status_code == 201, f"got {r.status_code}: {r.text}")

    # E. Test coupon validation
    r_val = requests.get(f"{PAYMENT_SVC}/api/v1/payments/promotions/validate", params={
        "code": promo_code,
        "orderValue": 120.00,
        "providerId": provider_id,
        "category": "Drools"
    })
    test("Validating coupon with correct criteria returns 200", r_val.status_code == 200, f"got {r_val.status_code}: {r_val.text}")

    # F. Test coupon validation with wrong category
    r_val_cat = requests.get(f"{PAYMENT_SVC}/api/v1/payments/promotions/validate", params={
        "code": promo_code,
        "orderValue": 120.00,
        "providerId": provider_id,
        "category": "FOOD"
    })
    test("Validating coupon with wrong category is rejected (400)", r_val_cat.status_code == 400, f"got {r_val_cat.status_code}")

    # G. Payout batch creates merchant and captain payouts and links captain earning
    try:
        conn = psycopg2.connect(DB_URL)
        conn.autocommit = True
        cur = conn.cursor()
        payout_customer_id = str(uuid.uuid4())
        payout_order_id = str(uuid.uuid4())
        payout_appointment_id = str(uuid.uuid4())
        payout_captain_id = str(uuid.uuid4())
        payout_earning_id = str(uuid.uuid4())
        payout_start = "2026-07-01"
        payout_end = "2026-07-07"

        cur.execute("""
            INSERT INTO captains.captain_profiles (captain_id, status, vehicle_type, vehicle_number)
            VALUES (%s, 'ACTIVE', 'BIKE', 'TS07S7777')
            ON CONFLICT (captain_id) DO NOTHING
        """, (payout_captain_id,))
        cur.execute("""
            INSERT INTO orders.orders (
                order_id, customer_id, provider_id, captain_id, delivery_address_id, status,
                subtotal_amount, delivery_fee, discount_amount, total_amount, placed_at, delivered_at
            )
            VALUES (%s, %s, %s, %s, %s, 'DELIVERED', 400.00, 40.00, 0.00, 440.00, '2026-07-02T09:00:00Z', '2026-07-02T10:00:00Z')
            ON CONFLICT (order_id) DO NOTHING
        """, (payout_order_id, payout_customer_id, merchant_provider_id, payout_captain_id, str(uuid.uuid4())))
        cur.execute("""
            INSERT INTO appointments.appointments (
                appointment_id, customer_id, provider_id, offering_id, slot_id, pet_id, status,
                price_amount, pay_at_clinic, booked_at, completed_at
            )
            VALUES (%s, %s, %s, %s, %s, %s, 'COMPLETED', 600.00, false, '2026-07-03T09:00:00Z', '2026-07-03T10:00:00Z')
            ON CONFLICT (appointment_id) DO NOTHING
        """, (payout_appointment_id, payout_customer_id, merchant_provider_id, str(uuid.uuid4()), str(uuid.uuid4()), str(uuid.uuid4())))
        cur.execute("""
            INSERT INTO captains.captain_earnings (earning_id, captain_id, order_id, amount, earned_at, payout_id)
            VALUES (%s, %s, %s, 150.00, '2026-07-02T10:15:00Z', NULL)
            ON CONFLICT (earning_id) DO NOTHING
        """, (payout_earning_id, payout_captain_id, payout_order_id))

        r_payout = requests.post(
            f"{PAYMENT_SVC}/api/v1/payments/payouts/calculate",
            params={"start": payout_start, "end": payout_end},
            headers=admin_headers,
            timeout=10
        )
        test("Admin payout batch returns 200", r_payout.status_code == 200, f"got {r_payout.status_code}: {r_payout.text}")
        payout_body = r_payout.json() if r_payout.status_code == 200 else []
        test("Payout batch creates merchant and captain payouts", isinstance(payout_body, list) and len(payout_body) >= 2, f"got {payout_body}")

        cur.execute("SELECT payout_id FROM captains.captain_earnings WHERE earning_id = %s", (payout_earning_id,))
        linked_payout = cur.fetchone()[0]
        test("Captain earning linked to payout record", linked_payout is not None)

        r_payout_again = requests.post(
            f"{PAYMENT_SVC}/api/v1/payments/payouts/calculate",
            params={"start": payout_start, "end": payout_end},
            headers=admin_headers,
            timeout=10
        )
        cur.execute("""
            SELECT COUNT(*)
            FROM payments.payouts
            WHERE period_start = %s AND period_end = %s AND payee_user_id IN (%s, %s)
        """, (payout_start, payout_end, merchant_user_id, payout_captain_id))
        payout_count = cur.fetchone()[0]
        test("Re-running payout batch does not duplicate payee-period payouts", r_payout_again.status_code == 200 and payout_count == 2, f"count {payout_count}")

        cur.close()
        conn.close()
    except Exception as e:
        print(f"  {FAIL}  Payout batch integration test failed: {e}")
        failed += 1


# ─── 3. Event-Driven Ratings Updates Tests ────────────────────────────────────

section("3. Incremental Ratings Updates via Kafka")

if not service_up(PROVIDER_SVC, "/actuator/health") or not service_up(REVIEW_SVC, "/api/v1/reviews/customer/" + str(uuid.uuid4())):
    skip("Ratings update test", "provider-service or review-service not running")
else:
    try:
        # Get an active provider from DB
        conn = psycopg2.connect(DB_URL)
        conn.autocommit = True
        cur = conn.cursor()
        cur.execute("SELECT provider_id, owner_user_id, rating_avg, rating_count FROM providers.providers WHERE status = 'ACTIVE' LIMIT 1")
        prov = cur.fetchone()
        
        if prov is None:
            # Let's create an active provider in DB if none exists
            owner_id = str(uuid.uuid4())
            prov_id = str(uuid.uuid4())
            # Insert into auth.users (which triggers profile and roles creation)
            cur.execute("""
                INSERT INTO auth.users (id, email, raw_user_meta_data)
                VALUES (%s, 'test@merchant.com', '{"role": "MERCHANT", "full_name": "Test Merchant"}'::jsonb)
            """, (owner_id,))
            # Insert provider
            cur.execute("""
                INSERT INTO providers.providers (provider_id, owner_user_id, provider_type, fulfillment_type, name, address_line, city, pincode, geo_location, status, rating_avg, rating_count)
                VALUES (%s, %s, 'PET_STORE', 'DELIVERY', 'Test Store', '123 Street', 'Hyderabad', '500001', ST_GeomFromText('POINT(78.4867 17.3850)', 4326), 'ACTIVE', 0.00, 0)
            """, (prov_id, owner_id))
            
            prov = (prov_id, owner_id, 0.0, 0)
            print("  Created a mock ACTIVE provider for testing")

        prov_id, owner_id, rating_avg, rating_count = prov
        print(f"  Provider {prov_id} current rating: {rating_avg} (count: {rating_count})")

        # Submit a new review to review-service
        review_id = str(uuid.uuid4())
        target_id = str(uuid.uuid4())
        payload = {
            "customerId": str(uuid.uuid4()),
            "providerId": prov_id,
            "targetType": "ORDER",
            "targetId": target_id,
            "rating": 5,
            "comment": "Super clean and prompt delivery!"
        }
        r_rev = requests.post(f"{REVIEW_SVC}/api/v1/reviews", json=payload, timeout=5)
        test("Submit review returns 201", r_rev.status_code == 201, f"got {r_rev.status_code}")

        # Wait for Kafka event listener to process
        print("  Waiting for Kafka propagation...")
        updated = None
        expected_count = rating_count + 1
        for _ in range(10):
            time.sleep(1)
            cur.execute("SELECT rating_avg, rating_count FROM providers.providers WHERE provider_id = %s", (prov_id,))
            updated = cur.fetchone()
            if updated and updated[1] >= expected_count:
                break

        new_avg, new_count = updated
        print(f"  Provider {prov_id} updated rating: {new_avg} (count: {new_count})")

        test("Provider rating count incremented", new_count == expected_count)
        expected_avg = (float(rating_avg) * rating_count + 5.0) / expected_count
        test("Provider rating average updated correctly", abs(float(new_avg) - expected_avg) < 0.05, f"expected {expected_avg}, got {new_avg}")

        cur.close()
        conn.close()
    except Exception as e:
        print(f"  {FAIL}  Ratings update integration test failed: {e}")
        failed += 1


# ─── Summary ──────────────────────────────────────────────────────────────────

print(f"\n{'═'*60}")
print(f"  Sprint 7 Results:  {passed} passed  |  {failed} failed  |  {skipped} skipped")
print(f"{'═'*60}\n")

if failed > 0:
    sys.exit(1)
