import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('merchant production profile is a store AAB with demo mode disabled', () => {
  const eas = JSON.parse(source('eas.json')) as {
    build: Record<string, {
      distribution?: string;
      android?: { buildType?: string };
      env?: Record<string, string>;
    }>;
  };
  const production = eas.build.production;

  assert.equal(production.distribution, 'store');
  assert.equal(production.android?.buildType, 'app-bundle');
  assert.equal(production.env?.MYPET_APP_VARIANT, 'merchant');
  assert.equal(production.env?.EXPO_PUBLIC_APP_VARIANT, 'merchant');
  assert.equal(production.env?.EXPO_PUBLIC_ALLOW_DEMO_MODE, 'false');
  assert.equal(production.env?.EXPO_PUBLIC_RELEASE_CHANNEL, 'production');
});

test('merchant variant has a distinct package and blocks captain-only background location', () => {
  const config = source('app.config.js');

  assert.match(config, /name: merchant \? 'MyPet Merchant'/);
  assert.match(config, /'com\.mypet\.merchant'/);
  assert.match(config, /android\.permission\.ACCESS_BACKGROUND_LOCATION/);
  assert.match(config, /android\.permission\.FOREGROUND_SERVICE_LOCATION/);
  assert.match(config, /isAndroidBackgroundLocationEnabled: false/);
  assert.match(config, /isAndroidForegroundServiceEnabled: false/);
  assert.match(config, /isIosBackgroundLocationEnabled: false/);
});

test('merchant runtime fails closed for captain/admin identities and signup intent', () => {
  const role = source('src/utils/operational-role.ts');
  const login = source('src/app/login.tsx');
  const config = source('src/utils/app-config.ts');

  assert.match(role, /appConfig\.isMerchantBuild && requestedRole !== 'MERCHANT'/);
  assert.match(role, /!appConfig\.isMerchantBuild \|\| role === 'MERCHANT'/);
  assert.match(login, /!appConfig\.isMerchantBuild \? \(/);
  assert.match(login, /This Play Store build can create merchant accounts only/);
  assert.match(config, /appVariant/);
  assert.match(config, /isMerchantBuild/);
  assert.match(config, /EXPO_PUBLIC_API_BASE_URL must use HTTPS in production builds/);
});
