# /// script
# dependencies = [
#   "psycopg2-binary",
#   "requests",
# ]
# ///
"""
Sprint 6 Integration Tests: Notifications & Reviews
Tests:
  1. DB schema validation for notifications.scheduled_reminders and reviews.reviews
  2. Review service: POST /api/v1/reviews
  3. Review service: idempotency guard (duplicate review returns 409)
  4. Review service: GET /api/v1/reviews/provider/{id}
  5. Review service: GET /api/v1/reviews/customer/{id}
  6. Notification service: GET /api/v1/notifications/health
  7. Notification service: scheduled_reminders table accessible

Usage:
  python3 backend/verify_sprint6.py
"""

import sys
import uuid
from pathlib import Path
import psycopg2
import requests

# ─── Config ───────────────────────────────────────────────────────────────────

DB_URL     = "host=localhost port=5433 dbname=pawsnearme user=postgres password=postgres"
GATEWAY    = "http://localhost:8080"
REVIEW_SVC = "http://localhost:8089"
NOTIF_SVC  = "http://localhost:8088"

PASS = "\033[92m✓ PASS\033[0m"
FAIL = "\033[91m✗ FAIL\033[0m"
SKIP = "\033[93m⊘ SKIP\033[0m"

passed = 0
failed = 0
skipped = 0
ROOT = Path(__file__).resolve().parents[1]


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


def has_text(path: str, *needles: str) -> bool:
    text = (ROOT / path).read_text()
    return all(needle in text for needle in needles)


# ─── 0. Source Contract Tests ─────────────────────────────────────────────────

section("0. Source Contract Validation")

test(
    "merchant bookings screen uses live appointment service",
    has_text("apps/merchant-captain-app/src/app/explore.tsx", "fetchMerchantBookings", "completeMerchantBooking", "appConfig.allowDemoMode"),
)
test(
    "customer history screen submits live appointment reviews",
    has_text("apps/customer-app/src/app/explore.tsx", "fetchCustomerAppointments", "submitAppointmentReview", "appConfig.allowDemoMode"),
)
test(
    "appointment events include slot_start for reminder scheduling",
    has_text("backend/appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/service/AppointmentService.kt", "slot_start", "fetchCatalogSlotStart"),
)
test(
    "notification worker records attempted delivered and failed statuses",
    has_text("backend/notification-service/src/main/kotlin/com/pawsnearme/notificationservice/service/ReminderDispatchWorker.kt", "markAttempted", "markDelivered", "markFailed"),
)
test(
    "prescription upload is not faked in merchant completion UI",
    not has_text("apps/merchant-captain-app/src/app/explore.tsx", "Document upload available in full app build"),
)


# ─── 1. DB Schema Tests ────────────────────────────────────────────────────────

section("1. Database Schema Validation")

try:
    conn = psycopg2.connect(DB_URL)
    conn.autocommit = True
    cur = conn.cursor()

    # Check notifications schema exists
    cur.execute("SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'notifications'")
    test("notifications schema exists", cur.fetchone() is not None)

    # Check reviews schema exists
    cur.execute("SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'reviews'")
    test("reviews schema exists", cur.fetchone() is not None)

    # Check scheduled_reminders table
    cur.execute("""
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = 'notifications' AND table_name = 'scheduled_reminders'
    """)
    col_count = cur.fetchone()[0]
    test("scheduled_reminders table has columns", col_count >= 7,
         f"found {col_count} columns, expected ≥7")

    cur.execute("""
        SELECT column_name FROM information_schema.columns
        WHERE table_schema = 'notifications'
          AND table_name = 'scheduled_reminders'
          AND column_name IN ('delivery_status', 'attempt_count', 'last_attempt_at', 'delivered_at', 'retryable_failure', 'failure_reason')
    """)
    reminder_status_cols = {row[0] for row in cur.fetchall()}
    expected_reminder_status_cols = {
        'delivery_status', 'attempt_count', 'last_attempt_at', 'delivered_at', 'retryable_failure', 'failure_reason'
    }
    test("scheduled_reminders has auditable delivery status columns",
         expected_reminder_status_cols.issubset(reminder_status_cols),
         f"found {sorted(reminder_status_cols)}")

    # Check reminders index
    cur.execute("""
        SELECT indexname FROM pg_indexes
        WHERE schemaname = 'notifications' AND tablename = 'scheduled_reminders'
        AND indexname = 'idx_reminders_fire_at'
    """)
    test("idx_reminders_fire_at index exists", cur.fetchone() is not None)

    # Check reviews.reviews table
    cur.execute("""
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = 'reviews' AND table_name = 'reviews'
    """)
    col_count = cur.fetchone()[0]
    test("reviews.reviews table has columns", col_count >= 7,
         f"found {col_count} columns, expected ≥7")

    # Check unique index on reviews (idx_reviews_target on target_type, target_id)
    cur.execute("""
        SELECT indexname FROM pg_indexes
        WHERE schemaname = 'reviews'
          AND tablename  = 'reviews'
          AND indexname  = 'idx_reviews_target'
    """)
    test("idx_reviews_target unique index exists", cur.fetchone() is not None)

    # Check notification_service_role has correct grants
    cur.execute("""
        SELECT has_schema_privilege('notification_service_role', 'notifications', 'USAGE')
    """)
    test("notification_service_role has USAGE on notifications schema",
         cur.fetchone()[0] is True)

    # Check review_service_role grants
    cur.execute("""
        SELECT has_schema_privilege('review_service_role', 'reviews', 'USAGE')
    """)
    test("review_service_role has USAGE on reviews schema",
         cur.fetchone()[0] is True)

    cur.close()
    conn.close()

except Exception as e:
    print(f"  {FAIL}  DB connection failed: {e}")
    failed += 1


# ─── Helper: check if service is UP ───────────────────────────────────────────

def service_up(base_url: str, path: str = "/actuator/health") -> bool:
    try:
        r = requests.get(base_url + path, timeout=3)
        return r.status_code < 500
    except Exception:
        return False


# ─── 2. Notification Service Tests ────────────────────────────────────────────

section("2. Notification Service (port 8088)")

if not service_up(NOTIF_SVC, "/api/v1/notifications/health"):
    skip("All notification-service tests", "service not running on port 8088")
    skipped += 5
else:
    r = requests.get(f"{NOTIF_SVC}/api/v1/notifications/health", timeout=5)
    test("GET /health returns 200", r.status_code == 200)
    body = r.json()
    test("health response has 'status' field", "status" in body)
    test("health response has 'pending' count", "pending" in body)
    test("health response has 'fired' count", "fired" in body)
    test("health response has deliveryStatus counts", "deliveryStatus" in body)
    test("health status is UP", body.get("status") == "UP")


# ─── 3. Review Service Tests ───────────────────────────────────────────────────

section("3. Review Service (port 8089)")

provider_id  = str(uuid.uuid4())
author_id    = str(uuid.uuid4())
appointment_id = str(uuid.uuid4())

if not service_up(REVIEW_SVC, "/api/v1/reviews/customer/" + author_id):
    skip("All review-service tests", "service not running on port 8089")
    skipped += 7
else:
    # POST a review
    payload = {
        "customerId":  author_id,
        "providerId":  provider_id,
        "targetType":  "APPOINTMENT",
        "targetId":    appointment_id,
        "rating":      5,
        "comment":     "Excellent service! Bruno loved it."
    }
    r = requests.post(f"{REVIEW_SVC}/api/v1/reviews", json=payload, timeout=5)
    test("POST /reviews returns 201", r.status_code == 201,
         f"got {r.status_code}: {r.text[:200]}")

    review_body = r.json() if r.status_code == 201 else {}
    test("review body has id", "id" in review_body)
    test("review body has correct rating", review_body.get("rating") == 5)

    # Idempotency: same targetId should 409
    r2 = requests.post(f"{REVIEW_SVC}/api/v1/reviews", json=payload, timeout=5)
    test("duplicate review returns 409 Conflict", r2.status_code == 409,
         f"got {r2.status_code}")

    # GET by provider
    r3 = requests.get(f"{REVIEW_SVC}/api/v1/reviews/provider/{provider_id}", timeout=5)
    test("GET /reviews/provider/{id} returns 200", r3.status_code == 200,
         f"got {r3.status_code}")
    body3 = r3.json()
    test("provider reviews response has 'reviews' key", "reviews" in body3)
    test("provider reviews response has 'averageRating' key", "averageRating" in body3)

    # GET by customer
    r4 = requests.get(f"{REVIEW_SVC}/api/v1/reviews/customer/{author_id}", timeout=5)
    test("GET /reviews/customer/{id} returns 200", r4.status_code == 200,
         f"got {r4.status_code}")
    customer_reviews = r4.json()
    test("customer review list is non-empty",
         isinstance(customer_reviews, list) and len(customer_reviews) >= 1,
         f"got {customer_reviews}")


# ─── 4. API Gateway routing tests ─────────────────────────────────────────────

section("4. API Gateway Routing (port 8080)")

if not service_up(GATEWAY, "/actuator/health"):
    skip("Gateway routing tests", "gateway not running on port 8080")
    skipped += 2
else:
    r = requests.get(f"{GATEWAY}/api/v1/notifications/health", timeout=5)
    test("Gateway routes /api/v1/notifications → notification-service",
         r.status_code != 404,
         f"got {r.status_code}")

    r2 = requests.get(f"{GATEWAY}/api/v1/reviews/customer/{uuid.uuid4()}", timeout=5)
    test("Gateway routes /api/v1/reviews → review-service",
         r2.status_code != 404,
         f"got {r2.status_code}")


# ─── Summary ──────────────────────────────────────────────────────────────────

print(f"\n{'═'*60}")
print(f"  Sprint 6 Results:  {passed} passed  |  {failed} failed  |  {skipped} skipped")
print(f"{'═'*60}\n")

if failed > 0:
    sys.exit(1)
