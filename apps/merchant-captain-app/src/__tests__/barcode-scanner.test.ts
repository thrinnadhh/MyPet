import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

import {
  barcodeLookupCandidates,
  barcodeValidationMessage,
  normalizeBarcode,
} from '../utils/barcode';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('barcode normalization makes UPC-A and leading-zero EAN-13 portable', () => {
  assert.equal(normalizeBarcode(' 0 123456789012 '), '123456789012');
  assert.equal(normalizeBarcode(' ab  12-cd '), 'AB 12-CD');
  assert.deepEqual(barcodeLookupCandidates('123456789012'), [
    '123456789012',
    '0123456789012',
  ]);
  assert.equal(barcodeValidationMessage('AB'), 'Barcode must contain between 3 and 50 characters.');
  assert.equal(barcodeValidationMessage('ABC123'), undefined);
});

test('native app config enables camera barcode scanning without audio recording', () => {
  const appConfig = JSON.parse(source('app.json')) as {
    expo: { plugins: Array<string | [string, Record<string, unknown>]> };
  };
  const cameraPlugin = appConfig.expo.plugins.find(
    (plugin): plugin is [string, Record<string, unknown>] => Array.isArray(plugin) && plugin[0] === 'expo-camera',
  );
  assert.ok(cameraPlugin, 'expo-camera config plugin must be registered');
  assert.equal(cameraPlugin[1].recordAudioAndroid, false);
  assert.equal(cameraPlugin[1].barcodeScannerEnabled, true);
  assert.match(String(cameraPlugin[1].cameraPermission), /scan product barcodes/i);
});

test('inventory and POS share one permission-aware camera scanner', () => {
  const scanner = source('src/components/barcode-scanner-modal.tsx');
  const inventory = source('src/app/inventory.tsx');
  const billing = source('src/app/billing.tsx');

  assert.match(scanner, /useCameraPermissions/);
  assert.match(scanner, /barcodeScannerSettings/);
  assert.match(scanner, /enableTorch/);
  assert.match(scanner, /ean13/);
  assert.match(scanner, /upc_a/);
  assert.match(scanner, /code128/);
  assert.match(inventory, /<BarcodeScannerModal/);
  assert.match(inventory, /Scan inventory barcode/);
  assert.match(inventory, /barcodeValidationMessage/);
  assert.match(billing, /<BarcodeScannerModal/);
  assert.match(billing, /resolveMerchantBarcode/);
  assert.doesNotMatch(billing, /<CameraView/);
  assert.doesNotMatch(billing, /useCameraPermissions/);
});

test('merchant barcode lookup is authenticated, provider-scoped and offline-capable', () => {
  const service = source('src/services/merchant-barcode.ts');
  const billingQueue = source('src/hooks/useBillingQueue.ts');

  assert.match(service, /NetInfo\.fetch/);
  assert.match(service, /AsyncStorage/);
  assert.match(service, /by-barcode\?storeId=/);
  assert.match(service, /OfflineBarcodeMissError/);
  assert.match(service, /refreshProviderBarcodeCatalog/);
  assert.doesNotMatch(service, /X-User-Id/);
  assert.doesNotMatch(service, /X-User-Role/);
  assert.doesNotMatch(billingQueue, /X-User-Id/);
  assert.doesNotMatch(billingQueue, /X-User-Role/);
  assert.match(billingQueue, /availableStock/);
  assert.match(billingQueue, /response\.status === 408/);
  assert.match(billingQueue, /kind: 'rejected'/);
});

test('server owns barcode identity, price, store scope and stock mutation', () => {
  const service = source('../../backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/service/CatalogService.kt');
  const controller = source('../../backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/controller/Controllers.kt');
  const migration = source('../../backend/catalog-service/src/main/resources/db/migration/V9__normalize_product_barcodes.sql');

  assert.match(service, /BarcodeSupport\.requireBarcode/);
  assert.match(service, /offering\.providerId != storeId/);
  assert.match(service, /serverUnitPrice = offering\.price/);
  assert.match(service, /Scanned barcode does not match/);
  assert.match(service, /decrementStockIfAvailable/);
  assert.match(controller, /@GetMapping\("\/offerings\/by-barcode"\)/);
  assert.match(controller, /role != "MERCHANT" && role != "ADMIN"/);
  assert.match(migration, /idx_offerings_provider_barcode/);
  assert.match(migration, /Duplicate provider barcode values remain after canonicalization/);
  assert.match(migration, /char_length\(barcode\) BETWEEN 3 AND 50/);
});
