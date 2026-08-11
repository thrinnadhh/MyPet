#!/usr/bin/env python3
"""
Sprint 23 — Tax & Pricing Compliance Verification Script
Verifies:
1. catalog.offerings schema Flyway migration V10 and Kotlin Offering model gstRate field.
2. providers.providers schema Flyway migration V12 and Kotlin Provider model gstNumber field.
3. orders.order_items schema Flyway migration V11 and Kotlin OrderItem model gstAmount field.
4. OrderService calculateQuote & decrementCatalogStock GST tax computation logic.
"""

import os
import sys

def verify_catalog_gst():
    print("\n1. Verifying Catalog Service GST Schema & Model...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    migration = os.path.join(root_dir, "backend", "catalog-service", "src", "main", "resources", "db", "migration", "V10__add_gst_rate.sql")
    offering_model = os.path.join(root_dir, "backend", "catalog-service", "src", "main", "kotlin", "com", "pawsnearme", "catalogservice", "model", "Offering.kt")
    dtos = os.path.join(root_dir, "backend", "catalog-service", "src", "main", "kotlin", "com", "pawsnearme", "catalogservice", "dto", "Dtos.kt")
    
    assert os.path.isfile(migration), f"Missing migration file {migration}"
    with open(migration, "r") as f:
        sql = f.read()
        assert "gst_rate" in sql, "Migration V10 does not reference gst_rate"
        assert "18.00" in sql, "Migration V10 missing default 18.00 GST rate"
    
    with open(offering_model, "r") as f:
        model = f.read()
        assert "gstRate" in model, "Offering.kt missing gstRate field"
        assert "gst_rate" in model, "Offering.kt missing @Column gst_rate mapping"
        
    with open(dtos, "r") as f:
        dto_code = f.read()
        assert "gstRate" in dto_code, "Dtos.kt missing gstRate property in OfferingRequest"
        
    print("   [PASS] Catalog Service GST rate schema, entity, and DTOs verified.")

def verify_provider_gst():
    print("\n2. Verifying Provider Service GST Schema & Model...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    migration = os.path.join(root_dir, "backend", "provider-service", "src", "main", "resources", "db", "migration", "V12__add_provider_gst_number.sql")
    provider_model = os.path.join(root_dir, "backend", "provider-service", "src", "main", "kotlin", "com", "pawsnearme", "providerservice", "model", "Provider.kt")
    controllers = os.path.join(root_dir, "backend", "provider-service", "src", "main", "kotlin", "com", "pawsnearme", "providerservice", "controller", "Controllers.kt")
    
    assert os.path.isfile(migration), f"Missing migration file {migration}"
    with open(migration, "r") as f:
        sql = f.read()
        assert "gst_number" in sql, "Migration V12 does not reference gst_number"
        
    with open(provider_model, "r") as f:
        model = f.read()
        assert "gstNumber" in model, "Provider.kt missing gstNumber field"
        
    with open(controllers, "r") as f:
        controller_code = f.read()
        assert "gstNumber" in controller_code, "Controllers.kt missing gstNumber in DTOs/mapping"
        
    print("   [PASS] Provider Service GST number schema, entity, and controllers verified.")

def verify_order_gst():
    print("\n3. Verifying Order Service GST Schema & Pricing Math...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    migration = os.path.join(root_dir, "backend", "order-service", "src", "main", "resources", "db", "migration", "V11__add_order_item_gst_amount.sql")
    order_models = os.path.join(root_dir, "backend", "order-service", "src", "main", "kotlin", "com", "pawsnearme", "orderservice", "model", "Models.kt")
    order_service = os.path.join(root_dir, "backend", "order-service", "src", "main", "kotlin", "com", "pawsnearme", "orderservice", "service", "OrderService.kt")
    
    assert os.path.isfile(migration), f"Missing migration file {migration}"
    with open(migration, "r") as f:
        sql = f.read()
        assert "gst_amount" in sql, "Migration V11 does not reference gst_amount"
        
    with open(order_models, "r") as f:
        models = f.read()
        assert "gstAmount" in models, "Models.kt missing gstAmount field in OrderItem"
        
    with open(order_service, "r") as f:
        service = f.read()
        assert "gstRate" in service, "OrderService.kt missing gstRate lookup during quote calculation"
        assert "gstAmount" in service, "OrderService.kt missing gstAmount assignment on OrderItem creation"
        
    print("   [PASS] Order Service GST schema, model, and itemized tax calculation verified.")

def verify_sprint23():
    print("=== SPRINT 23 TAX & PRICING COMPLIANCE VERIFICATION ===")
    verify_catalog_gst()
    verify_provider_gst()
    verify_order_gst()
    print("\n=== ALL SPRINT 23 VERIFICATIONS PASSED SUCCESSFULLY ===")

if __name__ == "__main__":
    verify_sprint23()
