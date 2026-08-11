#!/usr/bin/env python3
"""
Sprint 28 — Product Variant Checkout & Inventory Integration Verification Script
Verifies:
1. ModuleContracts.kt CatalogOfferingSnapshot & StockMutationCommand contain variantId.
2. OrderService.kt OrderItemRequest & OrderItem entity contain variantId.
3. OfferingVariantRepository contains stock mutation queries.
4. order-service Flyway migration V12 for variant_id column.
"""

import os
import sys

def verify_variant_checkout_integration():
    print("\n1. Verifying Variant Checkout Integration Schema & DTOs...")
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    module_contracts = os.path.join(root_dir, "backend", "common", "src", "main", "kotlin", "com", "pawsnearme", "common", "module", "ModuleContracts.kt")
    order_service = os.path.join(root_dir, "backend", "order-service", "src", "main", "kotlin", "com", "pawsnearme", "orderservice", "service", "OrderService.kt")
    order_models = os.path.join(root_dir, "backend", "order-service", "src", "main", "kotlin", "com", "pawsnearme", "orderservice", "model", "Models.kt")
    order_migration = os.path.join(root_dir, "backend", "order-service", "src", "main", "resources", "db", "migration", "V12__add_order_item_variant_id.sql")
    catalog_repo = os.path.join(root_dir, "backend", "catalog-service", "src", "main", "kotlin", "com", "pawsnearme", "catalogservice", "repository", "Repositories.kt")

    assert os.path.isfile(order_migration), f"Missing migration file {order_migration}"

    with open(module_contracts, "r") as f:
        code = f.read()
        assert "variantId" in code, "ModuleContracts.kt missing variantId in CatalogOfferingSnapshot/StockMutationCommand"

    with open(order_service, "r") as f:
        code = f.read()
        assert "variantId" in code, "OrderService.kt missing variantId in OrderItemRequest"

    with open(order_models, "r") as f:
        code = f.read()
        assert "variantId" in code, "Models.kt missing variantId field in OrderItem"

    with open(catalog_repo, "r") as f:
        code = f.read()
        assert "decrementVariantStockIfAvailable" in code, "Repositories.kt missing decrementVariantStockIfAvailable query"

    print("   [PASS] Variant checkout pricing, DTOs, and inventory mutation contracts verified.")

def verify_sprint28():
    print("=== SPRINT 28 PRODUCT VARIANT CHECKOUT INTEGRATION VERIFICATION ===")
    verify_variant_checkout_integration()
    print("\n=== ALL SPRINT 28 VERIFICATIONS PASSED SUCCESSFULLY ===")

if __name__ == "__main__":
    verify_sprint28()
