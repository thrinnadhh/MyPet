import urllib.request
import json
import sys
import psycopg2
import uuid
from datetime import datetime, timedelta, timezone

def post_json(url, data, headers=None):
    if headers is None:
        headers = {}
    headers["Content-Type"] = "application/json"
    req = urllib.request.Request(
        url,
        data=json.dumps(data).encode("utf-8"),
        headers=headers,
        method="POST"
    )
    try:
        with urllib.request.urlopen(req) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        try:
            return e.code, json.loads(body)
        except Exception:
            return e.code, {"error": body}
    except Exception as e:
        return 500, {"error": str(e)}

def db_execute(query, params=None):
    conn = psycopg2.connect("host=localhost port=5433 dbname=pawsnearme user=postgres password=postgres")
    conn.autocommit = True
    cur = conn.cursor()
    cur.execute(query, params)
    results = None
    try:
        if cur.description:
            results = cur.fetchall()
    except Exception:
        pass
    cur.close()
    conn.close()
    return results

def verify_sprint19():
    print("--- Sprint 19 Verification ---")
    
    # 1. Setup mock data in the DB
    job_id = str(uuid.uuid4())
    order_id = str(uuid.uuid4())
    captain_id = str(uuid.uuid4())
    offer_id = str(uuid.uuid4())
    
    print(f"Inserting mock job {job_id} and offer {offer_id}...")
    
    # Clear any previous mock references to avoid constraint errors
    db_execute("DELETE FROM dispatch.dispatch_offers WHERE job_id IN (SELECT job_id FROM dispatch.dispatch_jobs WHERE order_id = %s);", (order_id,))
    db_execute("DELETE FROM dispatch.dispatch_jobs WHERE order_id = %s;", (order_id,))
    
    # Insert job
    db_execute("""
        INSERT INTO dispatch.dispatch_jobs (job_id, order_id, status, attempt_count, max_attempts, created_at)
        VALUES (%s, %s, 'OFFERED', 1, 3, NOW());
    """, (job_id, order_id))
    
    # Insert offer with expired offered_at (60s ago)
    expired_time = datetime.now(timezone.utc) - timedelta(seconds=60)
    db_execute("""
        INSERT INTO dispatch.dispatch_offers (offer_id, job_id, captain_id, offered_at, offer_rank, version)
        VALUES (%s, %s, %s, %s, 1, 0);
    """, (offer_id, job_id, captain_id, expired_time))

    gateway_url = "http://localhost:8086"
    
    # CASE 1: Captain responds to offer successfully before poller check
    print("\n[Case 1] Captain rejects offer before poller runs...")
    status, res = post_json(
        f"{gateway_url}/api/v1/dispatch/offers/{offer_id}/respond?response=REJECTED",
        data={},
        headers={"X-User-Id": str(captain_id)}
    )
    print(f"Captain reject response status: {status}")
    print(f"Captain reject response body: {res}")
    assert status == 200, "Captain should successfully reject the offer"
    
    # Check version column got incremented (optimistic lock update)
    rows = db_execute("SELECT version, response FROM dispatch.dispatch_offers WHERE offer_id = %s;", (offer_id,))
    version, response = rows[0]
    print(f"DB Offer status: version={version}, response={response}")
    assert version > 0, "Version should be incremented"
    assert response == "REJECTED", "Response should be REJECTED"
    
    # Trigger poller timeout check. It should skip the timed out transition since response is already set
    print("Triggering timeout check...")
    status, res = post_json(f"{gateway_url}/api/v1/dispatch/admin/check-timeouts", data={})
    print(f"Timeout check status: {status}")
    
    # Assert job status is still FAILED (since triggerNextOffer set it to FAILED due to missing coordinates)
    job_rows = db_execute("SELECT status FROM dispatch.dispatch_jobs WHERE job_id = %s;", (job_id,))
    job_status = job_rows[0][0]
    print(f"Job Status after timeout check: {job_status}")
    assert job_status == "FAILED", "Job status should remain FAILED"

    # CASE 2: Reset and let timeout check run first, then verify Captain accept fails cleanly
    print("\n[Case 2] Timeout runs first, then Captain accepts late...")
    db_execute("UPDATE dispatch.dispatch_offers SET response = NULL, responded_at = NULL, version = 0 WHERE offer_id = %s;", (offer_id,))
    db_execute("UPDATE dispatch.dispatch_jobs SET status = 'OFFERED' WHERE job_id = %s;", (job_id,))
    
    # Trigger timeout check
    print("Triggering timeout check...")
    status, res = post_json(f"{gateway_url}/api/v1/dispatch/admin/check-timeouts", data={})
    print(f"Timeout check status: {status}")
    
    # Verify offer is timed out
    rows = db_execute("SELECT version, response FROM dispatch.dispatch_offers WHERE offer_id = %s;", (offer_id,))
    version, response = rows[0]
    print(f"DB Offer status after timeout: version={version}, response={response}")
    assert response == "TIMED_OUT", "Response should be TIMED_OUT"
    
    # Try to accept it now. It should throw the friendly exception
    status, res = post_json(
        f"{gateway_url}/api/v1/dispatch/offers/{offer_id}/respond?response=ACCEPTED",
        data={},
        headers={"X-User-Id": str(captain_id)}
    )
    print(f"Late Captain accept response status (Expected 409): {status}")
    print(f"Late Captain accept response body: {res}")
    assert status == 409 or status == 400 or status == 500, f"Expected error status, got: {status}"
    
    error_msg = res.get("error", "") or res.get("message", "")
    print(f"Captured error message: {error_msg}")
    assert "already resolved" in error_msg or "already responded" in error_msg, "Should give friendly conflict message"

    print("\nSprint 19 Verification Successful! Optimistic locking operates correctly.")

if __name__ == "__main__":
    verify_sprint19()
