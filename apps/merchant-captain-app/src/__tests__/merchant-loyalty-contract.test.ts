import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('merchant loyalty uses authenticated owned providers without spoofed headers', () => {
  const screen = source('src/app/loyalty/index.tsx');
  const service = source('src/services/merchant-loyalty.ts');
  assert.match(screen, /fetchMerchantProviders/);
  assert.match(screen, /role !== 'PROVIDER'/);
  assert.match(service, /apiClient/);
  assert.doesNotMatch(screen, /11111111-1111-1111-1111-111111111111/);
  assert.doesNotMatch(screen, /X-User-Role|X-User-Id|dummyProviderId/);
});

test('merchant can configure all approved ten-star reward values', () => {
  const screen = source('src/app/loyalty/index.tsx');
  assert.match(screen, /\[50, 100, 150, 200\]/);
  assert.match(screen, /targetStars: 10/);
  assert.match(screen, /special coupon issued after 10 stars/i);
});

test('payment backend verifies ownership and never generates a fallback actor', () => {
  const controller = source('../../backend/payment-service/src/main/kotlin/com/pawsnearme/paymentservice/controller/LoyaltyController.kt');
  assert.match(controller, /providerModule\.ownerUserId/);
  assert.match(controller, /Valid authenticated user context is required/);
  assert.match(controller, /Reward amount must be ₹50, ₹100, ₹150 or ₹200/);
  assert.doesNotMatch(controller, /UUID\.randomUUID\(\).*actor/i);
});
