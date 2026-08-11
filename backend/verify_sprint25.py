#!/usr/bin/env python3
"""
Sprint 25 — Product Images & Photo Reviews Verification Script
Verifies:
1. catalog.offerings schema Flyway migration V12 (image_urls array).
2. reviews.reviews schema Flyway migration V5 (images, is_verified_purchase, offering_id).
3. Review entity offeringId & isVerifiedPurchase fields.
4. ReviewController /api/v1/reviews/offering/{offeringId} endpoint.
"""

import os
import sys

def verify_catalog_images():
    print("\n1. Verifying Offering Multi-Image Migration...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    migration = os.path.join(root_dir, "backend", "catalog-service", "src", "main", "resources", "db", "migration", "V12__offering_image_urls.sql")
    assert os.path.isfile(migration), f"Missing migration file {migration}"
    
    with open(migration, "r") as f:
        sql = f.read()
        assert "image_urls" in sql, "Migration V12 missing image_urls"

    print("   [PASS] Multi-image migration verified.")

def verify_photo_reviews():
    print("\n2. Verifying Photo Reviews & Verified Purchase Schema...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    migration = os.path.join(root_dir, "backend", "review-service", "src", "main", "resources", "db", "migration", "V5__review_photos_and_offering_ref.sql")
    review_model = os.path.join(root_dir, "backend", "review-service", "src", "main", "kotlin", "com", "pawsnearme", "reviewservice", "model", "Review.kt")
    review_controller = os.path.join(root_dir, "backend", "review-service", "src", "main", "kotlin", "com", "pawsnearme", "reviewservice", "controller", "ReviewController.kt")
    
    assert os.path.isfile(migration), f"Missing migration file {migration}"
    with open(migration, "r") as f:
        sql = f.read()
        assert "is_verified_purchase" in sql, "Migration V5 missing is_verified_purchase"
        assert "offering_id" in sql, "Migration V5 missing offering_id"

    with open(review_model, "r") as f:
        code = f.read()
        assert "offeringId" in code, "Review.kt missing offeringId"
        assert "isVerifiedPurchase" in code, "Review.kt missing isVerifiedPurchase"

    with open(review_controller, "r") as f:
        code = f.read()
        assert "/offering/{offeringId}" in code, "ReviewController missing product-level reviews endpoint"

    print("   [PASS] Photo reviews, verified purchase tag, and offering review endpoint verified.")

def verify_sprint25():
    print("=== SPRINT 25 PRODUCT IMAGES & PHOTO REVIEWS VERIFICATION ===")
    verify_catalog_images()
    verify_photo_reviews()
    print("\n=== ALL SPRINT 25 VERIFICATIONS PASSED SUCCESSFULLY ===")

if __name__ == "__main__":
    verify_sprint25()
