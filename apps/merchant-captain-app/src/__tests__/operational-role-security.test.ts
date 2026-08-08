import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

import { requestedSelfServiceRole, trustedOperationalRole } from '../utils/operational-role';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('operational UI trusts app metadata and never user metadata as authorization', () => {
  assert.equal(trustedOperationalRole({ app_metadata: { role: 'MERCHANT' } } as never), 'MERCHANT');
  assert.equal(trustedOperationalRole({ app_metadata: { role: 'PROVIDER' } } as never), 'MERCHANT');
  assert.equal(trustedOperationalRole({ app_metadata: { role: 'CAPTAIN' } } as never), 'CAPTAIN');
  assert.equal(trustedOperationalRole({ app_metadata: { role: 'ADMIN' } } as never), 'ADMIN');
  assert.equal(trustedOperationalRole({ app_metadata: {} } as never), null);
  assert.equal(trustedOperationalRole({ app_metadata: { role: 'authenticated' } } as never), null);
});

test('legacy user metadata is only accepted as a self-service role request', () => {
  assert.equal(requestedSelfServiceRole({ user_metadata: { role: 'MERCHANT' } } as never), 'MERCHANT');
  assert.equal(requestedSelfServiceRole({ user_metadata: { requested_operational_role: 'CAPTAIN' } } as never), 'CAPTAIN');
  assert.equal(requestedSelfServiceRole({ user_metadata: { role: 'ADMIN' } } as never), null);
  assert.equal(requestedSelfServiceRole({ user_metadata: { role: 'CUSTOMER' } } as never), null);
});

test('signup and auth context enforce the server-side role promotion boundary', () => {
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
