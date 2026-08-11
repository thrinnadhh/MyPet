import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('operational UI trusts app metadata and not user metadata as authorization', () => {
  const role = source('src/utils/operational-role.ts');
  assert.match(role, /user\.app_metadata\?\.role/);
  assert.match(role, /TRUSTED_ROLES/);
  assert.match(role, /requested_operational_role\s*\?\?\s*user\.user_metadata\?\.role/);
  assert.match(role, /SELF_SERVICE_ROLES/);
  assert.match(role, /\['MERCHANT', 'CAPTAIN'\]/);
  assert.doesNotMatch(role, /SELF_SERVICE_ROLES[^\n]*ADMIN/);
});

test('signup and auth context enforce server-side role promotion', () => {
  const login = source('src/app/login.tsx');
  const auth = source('src/context/AuthContext.tsx');
  const layout = source('src/app/_layout.tsx');

  assert.match(login, /requested_operational_role:\s*signupRole/);
  assert.doesNotMatch(login, /\n\s*role:\s*signupRole/);
  assert.match(auth, /trustedOperationalRole\(nextSession\.user\)/);
  assert.match(auth, /claimRequestedOperationalRole/);
  assert.doesNotMatch(auth, /user_metadata\?\.role as string/);
  assert.doesNotMatch(auth, /\|\|\s*'MERCHANT'/);
  assert.match(layout, /roleMissing/);
});

test('role claim edge function never permits admin self-service', () => {
  const fn = source('../../supabase/functions/claim-operational-role/index.ts');
  assert.match(fn, /new Set<SelfServiceRole>\(\["MERCHANT", "CAPTAIN"\]\)/);
  assert.match(fn, /Administrator roles cannot be changed through self-service/);
  assert.match(fn, /auth\.admin\.updateUserById/);
  assert.match(fn, /app_metadata/);
});
