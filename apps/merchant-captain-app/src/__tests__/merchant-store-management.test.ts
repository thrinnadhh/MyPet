import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('merchant store screen uses server-persisted owned-provider profile updates', () => {
  const screen = source('src/app/store.tsx');
  const service = source('src/services/merchant-store.ts');

  assert.match(service, /\/api\/v1\/providers\/me\/profiles/);
  assert.match(service, /\/profile/);
  assert.match(service, /apiClient\.patch/);
  assert.match(service, /contactPhone/);
  assert.match(service, /contactEmail/);
  assert.match(service, /opensAt/);
  assert.match(service, /closesAt/);
  assert.match(service, /weeklyOffDays/);
  assert.match(screen, /updateMerchantStore/);
  assert.match(screen, /Business contact/);
  assert.match(screen, /Standard operating hours/);
  assert.match(screen, /Weekly closed days/);
  assert.match(screen, /Trust-sensitive fields are locked/);
  assert.match(screen, /Provider type, fulfilment type, approval status, commission and licence identity cannot be changed/);
  assert.match(screen, /requestForegroundPermissionsAsync/);
  assert.match(screen, /validOptionalPhone/);
  assert.match(screen, /validOptionalEmail/);
  assert.match(screen, /validBusinessTime/);
});

test('store and subscriptions are merchant-guarded navigation destinations', () => {
  const layout = source('src/app/_layout.tsx');
  const defaultTabs = source('src/components/app-tabs.tsx');
  const nativeTabs = source('src/components/app-tabs.native.tsx');
  const webTabs = source('src/components/app-tabs.web.tsx');

  assert.match(layout, /pathname\.startsWith\('\/store'\)/);
  assert.match(layout, /pathname\.startsWith\('\/subscriptions'\)/);
  for (const tabs of [defaultTabs, nativeTabs, webTabs]) {
    assert.match(tabs, /store/);
    assert.match(tabs, /subscriptions/);
  }
});
