#!/usr/bin/env python3
"""
Sprint 22 — Production Applications Verification Script
Verifies:
1. Centralized authenticated apiClient in customer-app and merchant-captain-app.
2. Mobile clients use bearer authentication without spoofable gateway identity/trust headers.
3. Merchant and Captain package structure alignment and unit/E2E test suite execution.
4. Super-admin web console real authentication, exact-origin token routing and safe DOM rendering.
"""

import os
import sys
import subprocess


def verify_api_client():
    print("\n1. Verifying Centralized Authenticated API Client...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    merchant_client = os.path.join(root_dir, "apps", "merchant-captain-app", "src", "services", "api-client.ts")
    customer_client = os.path.join(root_dir, "apps", "customer-app", "src", "services", "api-client.ts")

    assert os.path.isfile(merchant_client), "Missing apiClient in merchant-captain-app"
    assert os.path.isfile(customer_client), "Missing apiClient in customer-app"

    for label, client_path in (("Merchant/Captain", merchant_client), ("Customer", customer_client)):
        with open(client_path, "r", encoding="utf-8") as handle:
            content = handle.read()
            assert "Authorization" in content, f"{label} apiClient missing Bearer authorization header"
            assert "X-Internal-Gateway-Secret" not in content, f"{label} client must never ship the gateway trust secret"
            assert "X-User-Role" not in content, f"{label} client must not self-assert authorization role"
            assert "X-User-Id" not in content, f"{label} client must not self-assert authenticated user identity"
            print(f"   [PASS] {label} apiClient uses bearer-only application authentication.")


def verify_session_context():
    print("\n2. Verifying Session-Derived Context & Demo ID Removal...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    auth_context = os.path.join(root_dir, "apps", "merchant-captain-app", "src", "context", "AuthContext.tsx")
    with open(auth_context, "r", encoding="utf-8") as handle:
        content = handle.read()
        assert "providerId" in content and "captainId" in content, "AuthContext missing providerId / captainId session getters"
        assert "apiClient.setSessionToken" in content, "AuthContext missing sync with apiClient token"
        print("   [PASS] AuthContext exposes providerId/captainId and syncs session tokens with apiClient.")


def verify_test_suite():
    print("\n3. Executing Merchant & Captain Unit and E2E Test Suite...")
    app_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "apps", "merchant-captain-app")

    test_files = [
        "src/__tests__/merchant-inventory.test.ts",
        "src/__tests__/captain-delivery.test.ts",
        "src/__tests__/merchant-captain-e2e.test.ts",
    ]
    res = subprocess.run(["npx", "tsx", "--test"] + test_files, cwd=app_dir, capture_output=True, text=True)
    if res.returncode == 0:
        print("   [PASS] All merchant & captain unit and E2E test suites passed successfully!")
    else:
        print(f"   [FAIL] Test suite failed:\n{res.stderr or res.stdout}")
        sys.exit(1)


def verify_super_admin():
    print("\n4. Verifying Super Admin Real Auth & Safe DOM Rendering...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    admin_html = os.path.join(root_dir, "apps", "super-admin-web", "index.html")
    admin_js = os.path.join(root_dir, "apps", "super-admin-web", "app.js")
    secure_js = os.path.join(root_dir, "apps", "super-admin-web", "secure-admin.js")

    with open(admin_html, "r", encoding="utf-8") as handle:
        html = handle.read()
        assert "admin-login-modal" in html, "Missing admin authentication login overlay modal in super-admin index.html"
        assert "handleAdminSignOut" in html, "Missing sign out button in super-admin index.html"
        assert "LOCAL_SANDBOX" not in html and "11 ONLINE" not in html and "12.5%" not in html, (
            "Super Admin console must not ship fabricated operational indicators"
        )
        print("   [PASS] Super Admin HTML contains real auth controls and no fabricated production metrics.")

    with open(admin_js, "r", encoding="utf-8") as handle:
        js = handle.read()
        assert "innerHTML =" not in js and "innerHTML=" not in js, "Admin API data must not be rendered through innerHTML"
        assert "getAuthHeaders" not in js, "App logic should rely on the secured fetch boundary rather than self-asserted auth headers"
        assert "/api/v1/profiles/admin?page=" in js, "Admin user management must use paginated backend retrieval"
        assert "Simulated" not in js, "Simulated provider actions are not production evidence"
        print("   [PASS] Super Admin app uses safe DOM rendering and bounded user retrieval.")

    with open(secure_js, "r", encoding="utf-8") as handle:
        secure = handle.read()
        assert "app_metadata?.role" in secure, "Admin role must come from app_metadata"
        assert "candidate.origin !== configuredApiUrl.origin" in secure, "Admin bearer routing must validate exact API origin"
        assert "persistSession: false" in secure, "Admin session persistence policy unexpectedly changed"
        assert "X-Internal-Gateway-Secret" in secure, "Admin fetch boundary must explicitly strip internal trust headers"
        print("   [PASS] Super Admin auth validates app metadata and exact API origin before bearer attachment.")


def verify_sprint22():
    print("=== SPRINT 22 PRODUCTION APPLICATIONS VERIFICATION ===")
    verify_api_client()
    verify_session_context()
    verify_test_suite()
    verify_super_admin()
    print("\n=== ALL SPRINT 22 VERIFICATIONS COMPLETED SUCCESSFULLY ===")


if __name__ == "__main__":
    verify_sprint22()
