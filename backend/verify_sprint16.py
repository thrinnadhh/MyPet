#!/usr/bin/env python3
"""
Sprint S16 Verification Script
Tests store-scoped loyalty ledger, welcome star claims, purchase star credits on DELIVERED orders,
10-star rollover & reward issuance, wallet & reward lifecycle, and refund reversals.
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

def test_sprint16():
    print("=== SPRINT 16 LOYALTY LEDGER & REWARD LIFECYCLE VERIFICATION ===")
    customer_id = str(uuid.uuid4())
    provider_id = str(uuid.uuid4())

    headers_customer = {"X-User-Id": customer_id, "X-User-Role": "CUSTOMER"}
    headers_admin = {"X-User-Id": str(uuid.uuid4()), "X-User-Role": "ADMIN"}

    # 1. Welcome Star Claim & Idempotency
    print("\n1. Testing Welcome Star Claim & Double-Tap Idempotency...")
    status, claim1 = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/welcome-star/claim?providerId={provider_id}", "POST", headers=headers_customer)
    assert status == 200, f"Welcome star claim failed: {status} {claim1}"
    assert claim1["starBalance"] == 1, f"Expected 1 star balance after welcome claim, got {claim1['starBalance']}"
    assert claim1["welcomeStarClaimed"] is True, "welcomeStarClaimed should be true"
    print("   [PASS] First welcome star claim awarded 1 star.")

    status, claim2 = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/welcome-star/claim?providerId={provider_id}", "POST", headers=headers_customer)
    assert status == 200, f"Second welcome claim retry failed: {status}"
    assert claim2["starBalance"] == 1, f"Double-tap claim awarded extra star! Got {claim2['starBalance']}"
    print("   [PASS] Idempotent retry returned same 1-star balance without double credit.")

    # 2. DELIVERED Order Star Credit & Under-Minimum Threshold Filter
    print("\n2. Testing DELIVERED Order Star Credit & Min-Purchase Threshold...")
    # Under minimum order (< ₹199)
    order_low_id = str(uuid.uuid4())
    delivered_low = {
        "orderId": order_low_id,
        "customerId": customer_id,
        "providerId": provider_id,
        "netAmount": 150.0
    }
    status, res_low = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/events/order-delivered", "POST", delivered_low)
    assert status == 200, f"Delivered event failed: {status}"
    
    status, progress = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/progress?providerId={provider_id}", "GET", headers=headers_customer)
    assert progress["starBalance"] == 1, f"Under-minimum order credited a star! Balance: {progress['starBalance']}"
    print("   [PASS] Under-minimum order (< ₹199) ignored as expected.")

    # Eligible order (>= ₹199)
    order1_id = str(uuid.uuid4())
    delivered1 = {
        "orderId": order1_id,
        "customerId": customer_id,
        "providerId": provider_id,
        "netAmount": 299.0
    }
    status, res1 = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/events/order-delivered", "POST", delivered1)
    assert status == 200, f"Delivered event failed: {status}"
    assert res1["processed"] is True, "Event should be processed"

    status, progress = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/progress?providerId={provider_id}", "GET", headers=headers_customer)
    assert progress["starBalance"] == 2, f"Expected 2 star balance, got {progress['starBalance']}"
    print("   [PASS] Eligible delivered order credited +1 star. Total stars: 2.")

    # Duplicate Kafka/Order event delivery
    status, res_dup = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/events/order-delivered", "POST", delivered1)
    assert status == 200, f"Duplicate event call failed: {status}"
    assert res_dup["processed"] is False, "Duplicate event should not re-process"
    status, progress = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/progress?providerId={provider_id}", "GET", headers=headers_customer)
    assert progress["starBalance"] == 2, "Duplicate order event caused double credit"
    print("   [PASS] Duplicate order event ignored (exactly-once processing).")

    # 3. 10-Star Rollover & Reward Issuance
    print("\n3. Testing 10-Star Rollover & Reward Issuance...")
    for i in range(8):
        oid = str(uuid.uuid4())
        make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/events/order-delivered", "POST", {
            "orderId": oid,
            "customerId": customer_id,
            "providerId": provider_id,
            "netAmount": 250.0
        })

    status, progress = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/progress?providerId={provider_id}", "GET", headers=headers_customer)
    assert progress["starBalance"] == 0, f"Expected 0 star balance after 10-star rollover, got {progress['starBalance']}"
    assert progress["cycleCount"] == 1, f"Expected cycle count 1, got {progress['cycleCount']}"
    print("   [PASS] 10-star rollover triggered cleanly! Star balance reset to 0, cycleCount = 1.")

    # 4. Wallet & Reward Reservation / Redemption Lifecycle
    print("\n4. Testing Loyalty Wallet & Reward Lifecycle...")
    status, wallet = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/wallet", "GET", headers=headers_customer)
    assert status == 200, f"Wallet query failed: {status}"
    assert len(wallet) == 1, f"Expected 1 active reward in wallet, got {len(wallet)}"
    reward = wallet[0]
    reward_code = reward["code"]
    print(f"   Issued reward code: {reward_code} for ₹{reward['rewardAmount']}")

    # Reserve reward
    order_checkout_id = str(uuid.uuid4())
    reserve_req = {
        "code": reward_code,
        "providerId": provider_id,
        "orderId": order_checkout_id
    }
    status, reserve_res = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/rewards/reserve", "POST", reserve_req, headers=headers_customer)
    assert status == 200, f"Reward reservation failed: {status}"
    assert reserve_res["status"] == "RESERVED", "Reward status should be RESERVED"
    print("   [PASS] Reward reservation succeeded.")

    # Redeem reward
    status, redeem_res = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/rewards/redeem?code={reward_code}&orderId={order_checkout_id}", "POST", headers=headers_customer)
    assert status == 200, f"Reward redemption failed: {status}"
    assert redeem_res["status"] == "REDEEMED", "Reward status should be REDEEMED"
    print("   [PASS] Reward redemption completed successfully.")

    # 5. Order Refund & Reversal
    print("\n5. Testing Refund Reversal & Ledger Reconciliation...")
    # Add 1 star
    refund_order_id = str(uuid.uuid4())
    make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/events/order-delivered", "POST", {
        "orderId": refund_order_id,
        "customerId": customer_id,
        "providerId": provider_id,
        "netAmount": 300.0
    })
    status, progress = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/progress?providerId={provider_id}", "GET", headers=headers_customer)
    assert progress["starBalance"] == 1, "Balance should be 1 after new delivered order"

    # Refund order
    status, ref_res = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/events/order-refunded", "POST", {
        "orderId": refund_order_id,
        "customerId": customer_id,
        "providerId": provider_id
    })
    assert status == 200, f"Refund event failed: {status}"
    assert ref_res["processed"] is True, "Refund event should be processed"

    status, progress = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/progress?providerId={provider_id}", "GET", headers=headers_customer)
    assert progress["starBalance"] == 0, f"Expected 0 stars after refund reversal, got {progress['starBalance']}"
    print("   [PASS] Refund event successfully reversed 1 star.")

    # Reconcile from ledger
    status, rec_res = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/loyalty/reconcile?providerId={provider_id}", "POST", headers=headers_customer)
    assert status == 200, f"Reconciliation failed: {status}"
    assert rec_res["starBalance"] == 0, f"Reconciled star balance should be 0, got {rec_res['starBalance']}"
    print("   [PASS] Account star balance reconciled 100% with append-only ledger entries!")

    print("\n=== SPRINT 16 LOYALTY SYSTEM ALL TESTS PASSED SUCCESSFULLY! ===")

if __name__ == "__main__":
    test_sprint16()
