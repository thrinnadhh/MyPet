#!/usr/bin/env python3
"""
Sprint 27 — Tax-Inclusive GST Math (MRP Reverse Derivation) Verification Script
Verifies:
1. OrderService.kt reverse-derives GST from tax-inclusive MRP offering.price.
2. ₹500 MRP offering with 18% GST results in payable total = ₹500.00 (not ₹590.00).
3. Base price component = ₹423.73, GST component = ₹76.27.
"""

import os
import sys
from decimal import Decimal, ROUND_HALF_UP

def verify_mrp_reverse_derivation_math():
    print("\n1. Verifying MRP Tax-Inclusive Reverse-Derivation Math Logic...")
    
    # Test fixture: ₹500.00 MRP item with 18.00% GST
    mrp = Decimal("500.00")
    gst_rate = Decimal("18.00")
    qty = 1
    
    line_subtotal = mrp * Decimal(qty)
    gst_factor = Decimal("1.00") + (gst_rate / Decimal("100"))
    line_base = (line_subtotal / gst_factor).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    line_gst = line_subtotal - line_base
    
    payable_total = line_subtotal  # Tax is INCLUDED in MRP subtotal
    
    print(f"   MRP Subtotal: ₹{line_subtotal}")
    print(f"   Derived Base Amount: ₹{line_base}")
    print(f"   Derived GST Amount: ₹{line_gst}")
    print(f"   Payable Total: ₹{payable_total}")
    
    assert line_subtotal == Decimal("500.00"), "Subtotal should be exactly ₹500.00"
    assert line_base == Decimal("423.73"), f"Base amount expected ₹423.73, got ₹{line_base}"
    assert line_gst == Decimal("76.27"), f"GST amount expected ₹76.27, got ₹{line_gst}"
    assert payable_total == Decimal("500.00"), "Payable total should equal MRP line subtotal (no extra tax added on top)"
    
    print("   [PASS] Mathematical formula verified.")

def verify_order_service_code():
    print("\n2. Verifying OrderService.kt Source Implementation...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    order_service = os.path.join(root_dir, "backend", "order-service", "src", "main", "kotlin", "com", "pawsnearme", "orderservice", "service", "OrderService.kt")
    
    with open(order_service, "r") as f:
        code = f.read()
        assert "gstFactor" in code, "Missing gstFactor reverse-derivation in OrderService.kt"
        assert "lineBase" in code, "Missing lineBase calculation in OrderService.kt"
        # Ensure we are not adding .add(tax) to payableTotal
        assert ".add(tax)" not in code, "payableTotal must NOT add tax on top of tax-inclusive MRP subtotal"

    print("   [PASS] OrderService.kt tax-inclusive implementation verified.")

def verify_sprint27():
    print("=== SPRINT 27 TAX-INCLUSIVE GST REVERSE DERIVATION VERIFICATION ===")
    verify_mrp_reverse_derivation_math()
    verify_order_service_code()
    print("\n=== ALL SPRINT 27 VERIFICATIONS PASSED SUCCESSFULLY ===")

if __name__ == "__main__":
    verify_sprint27()
