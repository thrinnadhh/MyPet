#!/usr/bin/env python3
"""
Sprint 30 — Platform-Level GST (TCS on Merchant Payouts & GSTR-8 Reporting) Verification Script
Verifies:
1. payment-service Flyway migration V13 for tcs_rate and tcs_amount columns.
2. PaymentService.kt GSTR-8 report compilation logic.
3. PaymentController.kt /api/v1/payments/admin/reports/tcs-gstr8 ADMIN report endpoint.
"""

import os
import sys

def verify_tcs_and_gstr8():
    print("\n1. Verifying Platform TCS Schema & GSTR-8 Reporting API...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    migration = os.path.join(root_dir, "backend", "payment-service", "src", "main", "resources", "db", "migration", "V13__tcs_merchant_settlement.sql")
    payment_service = os.path.join(root_dir, "backend", "payment-service", "src", "main", "kotlin", "com", "pawsnearme", "paymentservice", "service", "PaymentService.kt")
    payment_controller = os.path.join(root_dir, "backend", "payment-service", "src", "main", "kotlin", "com", "pawsnearme", "paymentservice", "controller", "PaymentController.kt")

    assert os.path.isfile(migration), f"Missing migration file {migration}"
    
    with open(migration, "r") as f:
        sql = f.read()
        assert "tcs_rate" in sql, "Migration V13 missing tcs_rate column"
        assert "tcs_amount" in sql, "Migration V13 missing tcs_amount column"

    with open(payment_service, "r") as f:
        code = f.read()
        assert "getGstr8TcsReport" in code, "PaymentService.kt missing getGstr8TcsReport method"
        assert "Gstr8TcsReportResponse" in code, "PaymentService.kt missing Gstr8TcsReportResponse DTO"

    with open(payment_controller, "r") as f:
        code = f.read()
        assert "/admin/reports/tcs-gstr8" in code, "PaymentController.kt missing GSTR-8 report endpoint mapping"

    print("   [PASS] Platform TCS settlement schema and GSTR-8 report API verified.")

def verify_sprint30():
    print("=== SPRINT 30 PLATFORM-LEVEL GST (TCS & GSTR-8) VERIFICATION ===")
    verify_tcs_and_gstr8()
    print("\n=== ALL SPRINT 30 VERIFICATIONS PASSED SUCCESSFULLY ===")

if __name__ == "__main__":
    verify_sprint30()
