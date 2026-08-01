import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

const deliverySource = readFileSync(join(process.cwd(), 'src/app/delivery.tsx'), 'utf8');

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

test('captain delivery submits proof codes to dispatch', () => {
  assert.equal(deliverySource.includes('proofCode: cleaned'), true);
  assert.equal(deliverySource.includes("submitProof('pickup'"), true);
  assert.equal(deliverySource.includes("submitProof('deliver'"), true);
});
