import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('merchant dashboard uses live service metrics in production', () => {
  const screen = source('src/app/index.tsx');
  const service = source('src/services/merchant-dashboard.ts');

  assert.match(screen, /fetchMerchantDashboardMetrics/);
  assert.match(screen, /setInterval\(\(\) => void load\(\), 30_000\)/);
  assert.match(screen, /Today's fulfilled revenue/);
  assert.doesNotMatch(screen, /value=\{t\(action\.valueKey\)\}/);
  assert.match(service, /fetchMerchantOrders/);
  assert.match(service, /fetchMerchantOfferings/);
  assert.match(service, /fetchMerchantBookings/);
});

test('merchant onboarding does not pretend unsaved KYC fields were persisted', () => {
  const screen = source('src/app/onboarding.tsx');

  assert.match(screen, /requestForegroundPermissionsAsync/);
  assert.match(screen, /Capture or enter valid business coordinates/);
  assert.doesNotMatch(screen, /77\.5946/);
  assert.doesNotMatch(screen, /12\.9716/);
  assert.doesNotMatch(screen, /GSTIN/);
  assert.doesNotMatch(screen, /Bank Account Number/);
  assert.doesNotMatch(screen, /IFSC Code/);
  assert.match(screen, /Additional KYC, GST and settlement details are collected only in flows that persist them server-side/);
});

test('merchant orders render server item snapshots and refresh authoritative state', () => {
  const screen = source('src/app/orders.tsx');
  const service = source('src/services/merchant-orders.ts');

  assert.match(service, /items:\s*MerchantOrderItem\[\]/);
  assert.match(service, /discountAmount/);
  assert.match(service, /couponCode/);
  assert.match(screen, /order\.items\.map/);
  assert.match(screen, /Authoritative total/);
  assert.match(screen, /setInterval\(\(\) => void load\(true\), 10_000\)/);
  assert.match(screen, /await transitionMerchantOrder/);
  assert.match(screen, /await load\(true\)/);
});

test('merchant appointments never fabricate customer or pet names from UUIDs', () => {
  const service = source('src/services/merchant-appointments.ts');

  assert.doesNotMatch(service, /Customer \$\{compactId/);
  assert.doesNotMatch(service, /Pet \$\{compactId/);
  assert.match(service, /appointment\.customerName\?\.trim\(\) \|\| 'Customer identity unavailable'/);
  assert.match(service, /appointment\.petName\?\.trim\(\) \|\| 'Pet identity unavailable'/);
  assert.match(service, /identityResolved/);
});
