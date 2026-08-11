#!/usr/bin/env python3
"""
Sprint 26 — Admin Revenue Analytics & Shop Directory Verification Script
Verifies:
1. AdminAnalyticsController endpoint /api/v1/orders/admin/analytics/summary in order-service.
2. Super-admin web console Analytics & Shops navigation tab in index.html.
3. Super-admin web console fetchAdminAnalytics & fetchAllProvidersDirectory functions in app.js.
"""

import os
import sys

def verify_admin_analytics_api():
    print("\n1. Verifying Admin Revenue Analytics Controller...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    analytics_controller = os.path.join(root_dir, "backend", "order-service", "src", "main", "kotlin", "com", "pawsnearme", "orderservice", "controller", "AdminAnalyticsController.kt")
    assert os.path.isfile(analytics_controller), f"Missing controller file {analytics_controller}"
    
    with open(analytics_controller, "r") as f:
        code = f.read()
        assert "AdminAnalyticsController" in code, "Missing AdminAnalyticsController class"
        assert "/api/v1/orders/admin/analytics" in code, "Missing analytics summary endpoint path"
        assert "summary" in code, "Missing summary endpoint mapping"
        assert "totalGmv" in code, "Missing totalGmv calculation in response"
        assert "averageOrderValue" in code, "Missing averageOrderValue in response"

    print("   [PASS] Admin Revenue Analytics API endpoint verified.")

def verify_super_admin_ui():
    print("\n2. Verifying Super Admin Web Console Analytics & Shop Directory...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    admin_html = os.path.join(root_dir, "backend", "..", "apps", "super-admin-web", "index.html")
    admin_js = os.path.join(root_dir, "backend", "..", "apps", "super-admin-web", "app.js")
    
    with open(admin_html, "r") as f:
        html = f.read()
        assert "switchTab('analytics')" in html, "Missing analytics tab switch button in index.html"
        assert "analytics-panel" in html, "Missing analytics-panel container in index.html"
        assert "shops-directory-list" in html, "Missing shops-directory-list container in index.html"

    with open(admin_js, "r") as f:
        js = f.read()
        assert "fetchAdminAnalytics" in js, "Missing fetchAdminAnalytics in app.js"
        assert "fetchAllProvidersDirectory" in js, "Missing fetchAllProvidersDirectory in app.js"
        assert "filterShopDirectory" in js, "Missing filterShopDirectory in app.js"

    print("   [PASS] Super Admin Web Console Analytics UI & Shop Directory verified.")

def verify_sprint26():
    print("=== SPRINT 26 ADMIN ANALYTICS & SHOP DIRECTORY VERIFICATION ===")
    verify_admin_analytics_api()
    verify_super_admin_ui()
    print("\n=== ALL SPRINT 26 VERIFICATIONS PASSED SUCCESSFULLY ===")

if __name__ == "__main__":
    verify_sprint26()
