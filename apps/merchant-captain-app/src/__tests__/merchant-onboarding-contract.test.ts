import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

import { submitMerchantOnboarding } from '../services/merchant-onboarding';

test('merchant onboarding creates and submits a provider with bearer authentication', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input);
    calls.push({ url, init });
    if (url.endsWith('/api/v1/providers')) {
      return new Response(JSON.stringify({ providerId: 'provider-123' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }
    return new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } });
  }) as typeof fetch;

  try {
    await submitMerchantOnboarding(
      {
        ownerUserId: 'owner-123',
        providerType: 'PET_STORE',
        fulfillmentType: 'BOTH',
        name: 'Safe Store',
        description: 'Pet supplies',
        licenseNumber: null,
        licenseDocUrl: 'https://api.example/uploads/license.pdf',
        addressLine: '12 Main Road',
        city: 'Tirupati',
        pincode: '517501',
        longitude: 79.4192,
        latitude: 13.6288,
      },
      'access-token-123',
      'https://api.example',
    );
  } finally {
    globalThis.fetch = originalFetch;
  }

  assert.equal(calls.length, 2);
  assert.equal(new Headers(calls[0].init?.headers).get('Authorization'), 'Bearer access-token-123');
  assert.equal(calls[1].url.endsWith('/api/v1/providers/provider-123/submit'), true);
  assert.equal(new Headers(calls[1].init?.headers).get('Authorization'), 'Bearer access-token-123');
});

test('merchant onboarding screen uses a real document picker and never fabricates document URLs', () => {
  const source = readFileSync(new URL('../app/onboarding.tsx', import.meta.url), 'utf8');
  assert.match(source, /expo-document-picker/);
  assert.match(source, /uploadFileFromUri/);
  assert.doesNotMatch(source, /data:application\/pdf/);
  assert.doesNotMatch(source, /supabase\.storage/);
});
