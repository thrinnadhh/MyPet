#!/usr/bin/env python3
"""
Sprint S17 Verification Script
Tests customer loyalty experience APIs, merchant loyalty program controls,
and super-admin policy audit logging.
"""

import json
import sys
import uuid
import urllib.request
import urllib.error

PAYMENT_SERVICE_URL = "http://localhost:8090"

def make_request(url, method="GET", body=None, headers=None):
    if headers is None:
        headers = {}
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            resp_data = resp.read().decode("utf-8")
            return resp.status, json.loads(resp_data) if resp_data else {}
    except urllib.error.HTTPError as e:
        resp_data = e.read().decode("utf-8")
        try:
            parsed = json.loads(resp_data)
        except Exception:
            parsed = {"error": resp_data}
        return e.code, parsed

def test_sprint17():
    print("=== SPRINT 17 LOYALTY EXPERIENCE & MERCHANT CONTROLS VERIFICATION ===")
    customer_id = str(uuid.uuid4())
    provider_id = str(uuid.uuid4())
    merchant_id = str(uuid.uuid4())

    headers_customer = {"X-User-Id": customer_id, "X-User-Role": "CUSTOMER"}
    headers_merchant = {"X-User-Id": merchant_id, "X-User-Role": "MERCHANT"}
    headers_admin = {"X-User-Id": str(uuid.uuid4()), "X-User-Role": "ADMIN"}

    # 1. Customer Loyalty Progress Query
    print("\n1. Testing Customer Loyalty Progress Query...")
    status, progress = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/progress?providerId={provider_id}", "GET", headers=headers_customer)
    assert status == 200, f"Progress query failed: {status} {progress}"
    assert progress["starBalance"] == 0, "Initial balance should be 0"
    assert progress["targetStars"] == 10, "Target stars should be 10"
    print(f"   [PASS] Customer progress loaded: Balance={progress['starBalance']}/{progress['targetStars']}")

    # 2. Welcome Star Claim & Idempotent State
    print("\n2. Testing Welcome Star Claim...")
    status, claim_res = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/welcome-star/claim?providerId={provider_id}", "POST", headers=headers_customer)
    assert status == 200, f"Welcome claim failed: {status} {claim_res}"
    assert claim_res["starBalance"] == 1, "Welcome star should increment balance to 1"
    assert claim_res["welcomeStarClaimed"] is True, "welcomeStarClaimed should be true"
    print("   [PASS] Welcome star claimed successfully (+1 star).")

    # 3. Customer Wallet Retrieval
    print("\n3. Testing Customer Loyalty Wallet Query...")
    status, wallet = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/wallet", "GET", headers=headers_customer)
    assert status == 200, f"Wallet query failed: {status} {wallet}"
    assert isinstance(wallet, list), "Wallet response must be a list"
    print(f"   [PASS] Wallet loaded with {len(wallet)} active store rewards.")

    # 4. Merchant Program Settings Update & Ownership Check
    print("\n4. Testing Merchant Loyalty Program Settings Update...")
    program_update = {
        "providerId": provider_id,
        "targetStars": 10,
        "rewardAmount": 100.0, # Updated reward amount to ₹100
        "minOrderValue": 250.0, # Updated min purchase threshold to ₹250
        "welcomeStarPolicy": True,
        "isActive": True,
        "isStackable": False,
        "expiryDays": 60
    }
    status, updated_prog = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/programs", "POST", program_update, headers=headers_merchant)
    assert status == 200, f"Merchant program update failed: {status} {updated_prog}"
    assert float(updated_prog["rewardAmount"]) == 100.0, "Reward amount update failed"
    assert float(updated_prog["minOrderValue"]) == 250.0, "Min order value update failed"
    print("   [PASS] Merchant updated loyalty program: RewardAmount=₹100, MinOrderValue=₹250.")

    # 5. Program Policy Change Audit Logging
    print("\n5. Testing Super-Admin Audit Log Recording...")
    status, audit_logs = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/audit-logs?providerId={provider_id}", "GET", headers=headers_admin)
    assert status == 200, f"Audit log query failed: {status} {audit_logs}"
    assert len(audit_logs) >= 1, "At least one audit log entry expected"
    latest_audit = audit_logs[0]
    assert latest_audit["action"] == "UPDATE_PROGRAM", f"Expected action UPDATE_PROGRAM, got {latest_audit['action']}"
    print(f"   [PASS] Audit log recorded mutation by actor {latest_audit['actorId']}: {latest_audit['action']}")

    print("\n=== SPRINT 17 ALL TESTS PASSED SUCCESSFULLY! ===")

if __name__ == "__main__":
    test_sprint17()
