import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('merchant production config requires real privacy and account-deletion HTTPS resources', () => {
  const config = source('src/utils/app-config.ts');

  assert.match(config, /EXPO_PUBLIC_PRIVACY_POLICY_URL/);
  assert.match(config, /EXPO_PUBLIC_ACCOUNT_DELETION_URL/);
  assert.match(config, /privacyPolicyUrl/);
  assert.match(config, /accountDeletionUrl/);
  assert.match(config, /must be a valid HTTPS URL in Merchant production builds/);
});

test('merchant legal screen provides prominent in-app privacy and deletion paths', () => {
  const legal = source('src/app/legal.tsx');

  assert.match(legal, /Open Privacy Policy/);
  assert.match(legal, /Delete Account \/ Request Data Deletion/);
  assert.match(legal, /appConfig\.privacyPolicyUrl/);
  assert.match(legal, /appConfig\.accountDeletionUrl/);
  assert.match(legal, /openBrowserAsync/);
  assert.match(legal, /Request deletion of MyPet Merchant account and data/);
});
