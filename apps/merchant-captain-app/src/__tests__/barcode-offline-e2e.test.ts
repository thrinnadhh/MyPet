import assert from 'node:assert/strict';
import test from 'node:test';

import { ApiError } from '../contracts/api-error';
import {
  createMerchantBarcodeService,
  OfflineBarcodeMissError,
  type MerchantBarcodeDependencies,
} from '../services/merchant-barcode-core';
import type { MerchantOffering } from '../services/merchant-inventory';

const STORE_A = '11111111-1111-1111-1111-111111111111';
const STORE_B = '22222222-2222-2222-2222-222222222222';
const UPC = '123456789012';
const EAN_ALIAS = `0${UPC}`;

const product: MerchantOffering = {
  offeringId: '33333333-3333-3333-3333-333333333333',
  providerId: STORE_A,
  name: 'Barcode E2E Adult Dog Food',
  description: 'Complete nutrition used by the barcode scanner E2E test.',
  category: 'Food & Nutrition',
  price: 499,
  imageUrl: 'https://example.invalid/barcode-e2e-product.jpg',
  status: 'ACTIVE',
  stockQuantity: 5,
  sku: 'BARCODE-E2E-001',
  durationMinutes: null,
  barcode: UPC,
};

function apiError(status: number, message: string): ApiError {
  return new ApiError(status, {
    code: `HTTP_${status}`,
    message,
    fieldErrors: {},
  });
}

function harness(overrides: Partial<MerchantBarcodeDependencies> = {}) {
  const cache = new Map<string, string>();
  let connected = true;
  let networkOffering: MerchantOffering = product;
  let networkError: unknown;
  let providerOfferings: MerchantOffering[] = [product];

  const dependencies: MerchantBarcodeDependencies = {
    getNetworkState: async () => ({ isConnected: connected }),
    getCacheItem: async (key) => cache.get(key) ?? null,
    setCacheItem: async (key, value) => {
      cache.set(key, value);
    },
    fetchOfferingByBarcode: async () => {
      if (networkError) throw networkError;
      return networkOffering;
    },
    fetchOfferings: async () => providerOfferings,
    now: () => new Date('2026-08-05T00:00:00.000Z'),
    ...overrides,
  };

  return {
    service: createMerchantBarcodeService(dependencies),
    setConnected(value: boolean) {
      connected = value;
    },
    setNetworkError(value: unknown) {
      networkError = value;
    },
    setNetworkOffering(value: MerchantOffering) {
      networkOffering = value;
    },
    setProviderOfferings(value: MerchantOffering[]) {
      providerOfferings = value;
    },
  };
}

test('online catalog refresh supports an offline EAN alias scan with complete product details', async () => {
  const testHarness = harness();

  assert.equal(await testHarness.service.refreshProviderBarcodeCatalog(STORE_A), 1);
  testHarness.setConnected(false);

  const result = await testHarness.service.resolveMerchantBarcode(STORE_A, EAN_ALIAS);

  assert.equal(result.source, 'cache');
  assert.equal(result.cachedAt, '2026-08-05T00:00:00.000Z');
  assert.deepEqual(
    {
      id: result.offering.offeringId,
      name: result.offering.name,
      description: result.offering.description,
      category: result.offering.category,
      price: result.offering.price,
      imageUrl: result.offering.imageUrl,
      stock: result.offering.stockQuantity,
      sku: result.offering.sku,
      barcode: result.offering.barcode,
    },
    {
      id: product.offeringId,
      name: product.name,
      description: product.description,
      category: product.category,
      price: 499,
      imageUrl: product.imageUrl,
      stock: 5,
      sku: product.sku,
      barcode: UPC,
    },
  );
});

test('a retryable live lookup failure falls back to the previously refreshed offline catalog', async () => {
  const testHarness = harness();
  await testHarness.service.refreshProviderBarcodeCatalog(STORE_A);
  testHarness.setNetworkError(apiError(503, 'Catalog unavailable'));

  const result = await testHarness.service.resolveMerchantBarcode(STORE_A, UPC);

  assert.equal(result.source, 'cache');
  assert.equal(result.offering.offeringId, product.offeringId);
});

test('a deterministic not-found response does not return a stale cached product', async () => {
  const testHarness = harness();
  await testHarness.service.refreshProviderBarcodeCatalog(STORE_A);
  testHarness.setNetworkError(apiError(404, 'Product not found'));

  await assert.rejects(
    () => testHarness.service.resolveMerchantBarcode(STORE_A, UPC),
    (error: unknown) => error instanceof ApiError && error.status === 404,
  );
});

test('offline catalog data remains isolated to the merchant provider', async () => {
  const testHarness = harness();
  await testHarness.service.refreshProviderBarcodeCatalog(STORE_A);
  testHarness.setConnected(false);

  await assert.rejects(
    () => testHarness.service.resolveMerchantBarcode(STORE_B, UPC),
    (error: unknown) => error instanceof OfflineBarcodeMissError,
  );
});

test('catalog refresh excludes services and products without complete barcode stock identity', async () => {
  const testHarness = harness();
  testHarness.setProviderOfferings([
    product,
    { ...product, offeringId: '44444444-4444-4444-4444-444444444444', barcode: null },
    { ...product, offeringId: '55555555-5555-5555-5555-555555555555', stockQuantity: null },
  ]);

  assert.equal(await testHarness.service.refreshProviderBarcodeCatalog(STORE_A), 1);
});

test('a successful online scan refreshes the cached product details', async () => {
  const testHarness = harness();
  testHarness.setNetworkOffering({ ...product, stockQuantity: 9, price: 525 });

  const online = await testHarness.service.resolveMerchantBarcode(STORE_A, EAN_ALIAS);
  assert.equal(online.source, 'network');
  assert.equal(online.offering.stockQuantity, 9);
  assert.equal(online.offering.price, 525);

  testHarness.setConnected(false);
  const offline = await testHarness.service.resolveMerchantBarcode(STORE_A, UPC);
  assert.equal(offline.source, 'cache');
  assert.equal(offline.offering.stockQuantity, 9);
  assert.equal(offline.offering.price, 525);
});
