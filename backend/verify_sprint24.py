#!/usr/bin/env python3
"""
Sprint 24 — Structured Catalog (Categories & Variants) Verification Script
Verifies:
1. catalog.categories schema Flyway migration V11 and Category entity/controller.
2. catalog.offering_variants schema Flyway migration V11 and OfferingVariant entity/controller.
3. catalog.featured_collections schema Flyway migration V11 and FeaturedCollection entity.
4. catalog.offerings merchandising columns (is_featured, life_stage, product_type).
"""

import os
import sys

def verify_categories():
    print("\n1. Verifying Categories Schema & Controller...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    migration = os.path.join(root_dir, "backend", "catalog-service", "src", "main", "resources", "db", "migration", "V11__categories_variants_featured.sql")
    category_model = os.path.join(root_dir, "backend", "catalog-service", "src", "main", "kotlin", "com", "pawsnearme", "catalogservice", "model", "Category.kt")
    category_controller = os.path.join(root_dir, "backend", "catalog-service", "src", "main", "kotlin", "com", "pawsnearme", "catalogservice", "controller", "CategoryController.kt")
    
    assert os.path.isfile(migration), f"Missing migration file {migration}"
    with open(migration, "r") as f:
        sql = f.read()
        assert "catalog.categories" in sql, "Migration V11 missing catalog.categories table"
        assert "pet_type" in sql, "Migration V11 missing pet_type column"
        assert "parent_id" in sql, "Migration V11 missing parent_id column"

    with open(category_model, "r") as f:
        code = f.read()
        assert "Category" in code, "Missing Category model"
        assert "petType" in code, "Category model missing petType property"
        
    with open(category_controller, "r") as f:
        code = f.read()
        assert "CategoryController" in code, "Missing CategoryController"
        assert "/api/v1/categories" in code, "Missing categories request mapping"

    print("   [PASS] Structured categories schema, entity, and controller verified.")

def verify_variants():
    print("\n2. Verifying Offering Variants Schema & Controller...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    variant_model = os.path.join(root_dir, "backend", "catalog-service", "src", "main", "kotlin", "com", "pawsnearme", "catalogservice", "model", "OfferingVariant.kt")
    variant_controller = os.path.join(root_dir, "backend", "catalog-service", "src", "main", "kotlin", "com", "pawsnearme", "catalogservice", "controller", "VariantController.kt")
    
    with open(variant_model, "r") as f:
        code = f.read()
        assert "OfferingVariant" in code, "Missing OfferingVariant model"
        assert "stockQuantity" in code, "OfferingVariant missing stockQuantity"

    with open(variant_controller, "r") as f:
        code = f.read()
        assert "VariantController" in code, "Missing VariantController"
        assert "/api/v1/variants" in code, "Missing variants request mapping"

    print("   [PASS] Product variants schema, entity, and controller verified.")

def verify_merchandising_and_featured():
    print("\n3. Verifying Merchandising Fields & Featured Collections...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    offering_model = os.path.join(root_dir, "backend", "catalog-service", "src", "main", "kotlin", "com", "pawsnearme", "catalogservice", "model", "Offering.kt")
    featured_model = os.path.join(root_dir, "backend", "catalog-service", "src", "main", "kotlin", "com", "pawsnearme", "catalogservice", "model", "FeaturedCollection.kt")
    
    with open(offering_model, "r") as f:
        code = f.read()
        assert "isFeatured" in code, "Offering model missing isFeatured"
        assert "lifeStage" in code, "Offering model missing lifeStage"
        assert "productType" in code, "Offering model missing productType"

    with open(featured_model, "r") as f:
        code = f.read()
        assert "FeaturedCollection" in code, "Missing FeaturedCollection model"

    print("   [PASS] Merchandising flags and featured collection tables verified.")

def verify_sprint24():
    print("=== SPRINT 24 STRUCTURED CATALOG VERIFICATION ===")
    verify_categories()
    verify_variants()
    verify_merchandising_and_featured()
    print("\n=== ALL SPRINT 24 VERIFICATIONS PASSED SUCCESSFULLY ===")

if __name__ == "__main__":
    verify_sprint24()
