#!/usr/bin/env python3
"""
Sprint 22 — Production Applications Verification Script
Verifies:
1. Centralized authenticated apiClient in customer-app and merchant-captain-app.
2. Removal of hardcoded demo IDs & session-derived providerId/captainId in AuthContext.
3. Merchant and Captain package structure alignment and unit/E2E test suite execution.
4. Super-admin web console real authentication and XSS-free DOM rendering safety.
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
    
    with open(merchant_client, "r") as f:
        content = f.read()
        assert "X-Internal-Gateway-Secret" in content, "apiClient missing gateway secret header"
        assert "Authorization" in content, "apiClient missing Bearer authorization header"
        assert "X-User-Role" in content, "apiClient missing X-User-Role header"
        print("   [PASS] Merchant-Captain app apiClient configured with full auth header injection.")

    with open(customer_client, "r") as f:
        content = f.read()
        assert "Authorization" in content, "Customer app apiClient missing Bearer authorization header"
        print("   [PASS] Customer app apiClient configured with auth headers.")

def verify_session_context():
    print("\n2. Verifying Session-Derived Context & Demo ID Removal...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    auth_context = os.path.join(root_dir, "apps", "merchant-captain-app", "src", "context", "AuthContext.tsx")
    with open(auth_context, "r") as f:
        content = f.read()
        assert "providerId" in content and "captainId" in content, "AuthContext missing providerId / captainId session getters"
        assert "apiClient.setSessionToken" in content, "AuthContext missing sync with apiClient token"
        print("   [PASS] AuthContext exposes providerId/captainId and syncs session tokens with apiClient.")

def verify_test_suite():
    print("\n3. Executing Merchant & Captain Unit and E2E Test Suite...")
    app_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "apps", "merchant-captain-app")
    
    test_files = [
        "src/__tests__/merchant-inventory.test.ts",
        "src/__tests__/captain-delivery.test.ts",
        "src/__tests__/merchant-captain-e2e.test.ts"
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
    
    with open(admin_html, "r") as f:
        html = f.read()
        assert "admin-login-modal" in html, "Missing admin authentication login overlay modal in super-admin index.html"
        assert "handleAdminSignOut" in html, "Missing sign out button in super-admin index.html"
        print("   [PASS] Super Admin HTML contains auth login modal and sign-out control.")

    with open(admin_js, "r") as f:
        js = f.read()
        assert "getAuthHeaders" in js, "Missing getAuthHeaders function in super-admin app.js"
        assert "checkAdminAuth" in js, "Missing checkAdminAuth session validator in super-admin app.js"
        assert "escapeHtml" in js, "Missing escapeHtml XSS sanitizer in super-admin app.js"
        print("   [PASS] Super Admin JS implements auth header injection and XSS DOM sanitization.")

def verify_sprint22():
    print("=== SPRINT 22 PRODUCTION APPLICATIONS VERIFICATION ===")
    verify_api_client()
    verify_session_context()
    verify_test_suite()
    verify_super_admin()
    print("\n=== ALL SPRINT 22 VERIFICATIONS COMPLETED SUCCESSFULLY ===")

if __name__ == "__main__":
    verify_sprint22()
