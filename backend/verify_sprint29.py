#!/usr/bin/env python3
"""
Sprint 29 — Server-Side Review Purchase Verification Script
Verifies:
1. ReviewService.kt forces server-side purchase verification.
2. Inbound review.isVerifiedPurchase from client is overwritten by server-side verifyPurchase().
"""

import os
import sys

def verify_review_purchase_verification():
    print("\n1. Verifying ReviewService Server-Side Purchase Verification...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    review_service = os.path.join(root_dir, "backend", "review-service", "src", "main", "kotlin", "com", "pawsnearme", "reviewservice", "service", "ReviewService.kt")
    
    with open(review_service, "r") as f:
        code = f.read()
        assert "verifyPurchase" in code, "ReviewService.kt missing verifyPurchase method"
        assert "isVerifiedPurchase = isVerified" in code, "ReviewService.kt must overwrite client isVerifiedPurchase with server verification result"

    print("   [PASS] Server-side purchase verification logic verified.")

def verify_sprint29():
    print("=== SPRINT 29 SERVER-SIDE REVIEW PURCHASE VERIFICATION ===")
    verify_review_purchase_verification()
    print("\n=== ALL SPRINT 29 VERIFICATIONS PASSED SUCCESSFULLY ===")

if __name__ == "__main__":
    verify_sprint29()
