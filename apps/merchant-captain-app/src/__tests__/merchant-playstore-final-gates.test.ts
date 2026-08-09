import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('catalog media stays provider-scoped while customer image reads remain public', () => {
  const screen = source('src/app/catalog-media.tsx');
  const service = source('src/services/merchant-inventory.ts');
  const layout = source('src/app/_layout.tsx');
  const tabs = source('src/components/app-tabs.tsx');
  const nativeTabs = source('src/components/app-tabs.native.tsx');
  const webTabs = source('src/components/app-tabs.web.tsx');

  assert.match(layout, /pathname\.startsWith\('\/catalog-media'\)/);
  assert.match(tabs, /href:\s*'\/catalog-media'/);
  assert.match(nativeTabs, /name="catalog-media"/);
  assert.match(webTabs, /href:\s*'\/catalog-media'/);
  assert.match(service, /uploadMerchantOfferingImage/);
  assert.match(service, /FormData/);
  assert.match(screen, /Public catalog images only/);
  assert.match(screen, /Verification, KYC and settlement documents/);
  assert.match(screen, /MAX_IMAGE_BYTES\s*=\s*5 \* 1024 \* 1024/);
  assert.match(screen, /image\/jpeg/);
  assert.match(screen, /image\/png/);
  assert.match(screen, /image\/webp/);
});

test('merchant camera has permanent-denial recovery through system settings', () => {
  const scanner = source('src/components/barcode-scanner-modal.tsx');

  assert.match(scanner, /permission\.canAskAgain === false/);
  assert.match(scanner, /Linking\.openSettings/);
  assert.match(scanner, /Open settings/);
  assert.match(scanner, /MyPet Merchant/);
});

test('push lifecycle covers cold start, sign-out cleanup and permanent-denial recovery', () => {
  const notifications = source('src/hooks/usePushNotifications.ts');

  assert.match(notifications, /getLastNotificationResponseAsync/);
  assert.match(notifications, /addNotificationResponseReceivedListener/);
  assert.match(notifications, /unregisterPushToken/);
  assert.match(notifications, /permission\.canAskAgain === false/);
  assert.match(notifications, /Merchant alerts are disabled/);
  assert.match(notifications, /Linking\.openSettings/);
  assert.match(notifications, /templateCode\.includes\('SUBSCRIPTION'\)/);
  assert.match(notifications, /return '\/subscriptions'/);
  assert.match(notifications, /return '\/finance'/);
  assert.match(notifications, /return '\/orders'/);
});

test('merchant finance client requests bounded payout pages', () => {
  const finance = source('src/services/merchant-finance.ts');

  assert.match(finance, /Math\.min\(100/);
  assert.match(finance, /Math\.max\(0/);
  assert.match(finance, /payoutPage=/);
  assert.match(finance, /payoutSize=/);
  assert.match(finance, /payoutTotalRecords/);
});

test('authenticated multipart requests do not force JSON content type', () => {
  const apiClient = source('src/services/api-client.ts');

  assert.match(apiClient, /value instanceof FormData/);
  assert.match(apiClient, /isFormData\(body\) \? \{\} : \{ 'Content-Type': 'application\/json' \}/);
  assert.match(apiClient, /isFormData\(body\)[\s\S]*\? body/);
});
