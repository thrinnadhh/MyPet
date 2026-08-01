import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const deliverySource = readFileSync(
  fileURLToPath(new URL('../app/delivery.tsx', import.meta.url)),
  'utf8',
);

test('captain delivery does not embed fixed production OTPs', () => {
  assert.equal(deliverySource.includes("pickupOtp !== '1234'"), false);
  assert.equal(deliverySource.includes("deliveryOtp !== '5678'"), false);
  assert.equal(deliverySource.includes('Dev OTP:'), false);
});

test('captain delivery does not publish the old hardcoded Delhi location', () => {
  assert.equal(deliverySource.includes('28.6139'), false);
  assert.equal(deliverySource.includes('77.2090'), false);
  assert.equal(deliverySource.includes('Mock slight movements'), false);
});

test('captain delivery sends proof codes to the dispatch service', () => {
  assert.match(deliverySource, /proofCode: cleaned/);
  assert.match(deliverySource, /\/pickup|kind/);
  assert.match(deliverySource, /\/deliver|kind/);
});
