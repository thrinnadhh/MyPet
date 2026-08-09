import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('merchant notification responses route foreground/background and cold-start taps', () => {
  const hook = source('src/hooks/usePushNotifications.ts');

  assert.match(hook, /addNotificationResponseReceivedListener/);
  assert.match(hook, /getLastNotificationResponseAsync/);
  assert.match(hook, /templateCode\.includes\('APPOINTMENT'\)/);
  assert.match(hook, /return '\/explore'/);
  assert.match(hook, /templateCode\.includes\('RECURRING'\)/);
  assert.match(hook, /return '\/subscriptions'/);
  assert.match(hook, /templateCode\.includes\('PAYOUT'\)/);
  assert.match(hook, /return '\/finance'/);
  assert.match(hook, /templateCode\.includes\('ORDER'\)/);
  assert.match(hook, /return '\/orders'/);
});

test('push token lifecycle unregisters the operational token on sign-out', () => {
  const hook = source('src/hooks/usePushNotifications.ts');

  assert.match(hook, /unregisterPushToken/);
  assert.match(hook, /method: 'DELETE'/);
  assert.match(hook, /previous && !userId && registeredToken\.current/);
});
