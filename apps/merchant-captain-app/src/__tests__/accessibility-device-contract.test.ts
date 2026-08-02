import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

import { touchTarget } from '../design/tokens';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('operational targets remain at least 48 pixels', () => {
  assert.ok(touchTarget >= 48);
});

test('shared operational shell supports responsive width keyboard and refresh', () => {
  const shell = source('src/components/foundation/screen-shell.tsx');
  assert.match(shell, /maxWidth/);
  assert.match(shell, /KeyboardAvoidingView/);
  assert.match(shell, /refreshControl/);
});

test('captain location config declares physical-device permission behavior', () => {
  const appJson = source('app.json');
  const location = source('src/services/captain-location.ts');
  assert.match(appJson, /isAndroidBackgroundLocationEnabled/);
  assert.match(appJson, /isIosBackgroundLocationEnabled/);
  assert.match(location, /permission-blocked/);
  assert.match(location, /Location\.hasServicesEnabledAsync/);
  assert.match(location, /stopLocationUpdatesAsync/);
});

test('admin and delivery critical states remain role and screen-reader visible', () => {
  const layout = source('src/app/_layout.tsx');
  const delivery = source('src/app/delivery.tsx');
  const adminCases = source('src/app/admin/cases.tsx');
  assert.match(layout, /kind="unauthorized"/);
  assert.match(delivery, /accessibility/);
  assert.match(adminCases, /Administrator access required/);
});
