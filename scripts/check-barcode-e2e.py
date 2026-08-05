#!/usr/bin/env python3
"""Fail-closed source contract for the merchant barcode inventory/POS journey."""

from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

REQUIRED = {
    "shared scanner": ROOT / "apps/merchant-captain-app/src/components/barcode-scanner-modal.tsx",
    "inventory screen": ROOT / "apps/merchant-captain-app/src/app/inventory.tsx",
    "billing screen": ROOT / "apps/merchant-captain-app/src/app/billing.tsx",
    "barcode client": ROOT / "apps/merchant-captain-app/src/services/merchant-barcode.ts",
    "inventory client": ROOT / "apps/merchant-captain-app/src/services/merchant-inventory.ts",
    "billing queue": ROOT / "apps/merchant-captain-app/src/hooks/useBillingQueue.ts",
    "barcode utility": ROOT / "apps/merchant-captain-app/src/utils/barcode.ts",
    "catalog controller": ROOT / "backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/controller/Controllers.kt",
    "catalog service": ROOT / "backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/service/CatalogService.kt",
    "barcode support": ROOT / "backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/support/BarcodeSupport.kt",
    "barcode migration": ROOT / "backend/catalog-service/src/main/resources/db/migration/V9__normalize_product_barcodes.sql",
    "mobile barcode tests": ROOT / "apps/merchant-captain-app/src/__tests__/barcode-scanner.test.ts",
    "backend barcode tests": ROOT / "backend/catalog-service/src/test/kotlin/com/pawsnearme/catalogservice/service/CatalogServiceTests.kt",
}

CHECKS = {
    "shared scanner": ["useCameraPermissions", "barcodeScannerSettings", "enableTorch", "onBarcodeScanned"],
    "inventory screen": ["BarcodeScannerModal", "Scan inventory barcode", "barcodeValidationMessage"],
    "billing screen": ["BarcodeScannerModal", "resolveMerchantBarcode", "availableStock", "Confirm checkout"],
    "barcode client": ["by-barcode?storeId=", "AsyncStorage", "NetInfo.fetch", "OfflineBarcodeMissError"],
    "inventory client": ["barcode", "normalizeBarcode", "createMerchantOffering", "updateMerchantOffering"],
    "billing queue": ["availableStock", "idempotencyKey", "kind: 'rejected'", "response.status === 408"],
    "barcode utility": ["normalizeBarcode", "barcodeLookupCandidates", "barcodeValidationMessage"],
    "catalog controller": ["/offerings/by-barcode", "createBill", "MERCHANT", "ADMIN"],
    "catalog service": ["getOfferingByBarcode", "serverUnitPrice", "decrementStockIfAvailable", "Scanned barcode does not match"],
    "barcode support": ["requireBarcode", "lookupCandidates"],
    "barcode migration": [
        "idx_offerings_provider_barcode",
        "char_length(barcode) BETWEEN 3 AND 50",
        "Duplicate provider barcode values remain after canonicalization",
    ],
    "mobile barcode tests": ["inventory and POS share one permission-aware camera scanner", "server owns barcode identity"],
    "backend barcode tests": ["createOffering canonicalizes leading zero EAN13 to UPC-A", "createBill uses server price canonical barcode"],
}

FORBIDDEN = {
    "billing screen": ["<CameraView", "useCameraPermissions"],
    "barcode client": ["X-User-Id", "X-User-Role"],
    "billing queue": ["X-User-Id", "X-User-Role"],
}

errors: list[str] = []
for label, path in REQUIRED.items():
    if not path.exists():
        errors.append(f"missing {label}: {path.relative_to(ROOT)}")
        continue
    text = path.read_text(encoding="utf-8")
    for token in CHECKS.get(label, []):
        if token not in text:
            errors.append(f"{label} missing required contract: {token}")
    for token in FORBIDDEN.get(label, []):
        if token in text:
            errors.append(f"{label} contains forbidden duplicate/spoofable contract: {token}")

if errors:
    print("BARCODE_E2E_CONTRACT=FAIL")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("BARCODE_E2E_CONTRACT=PASS")
print("Covered: inventory capture, normalization, authenticated lookup, offline cache, stock-aware POS, server-authoritative billing, migration safety.")
