import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('operational signup supports merchant and captain roles', () => {
  const login = source('src/app/login.tsx');
  assert.equal(login.includes("type SignupRole = 'MERCHANT' | 'CAPTAIN'"), true);
  assert.equal(login.includes('role: signupRole'), true);
  assert.equal(login.includes("setSignupRole('CAPTAIN')"), true);
});

test('shared operational primitives enforce accessible action semantics', () => {
  const primitives = source('src/components/foundation/primitives.tsx');
  assert.equal(primitives.includes('accessibilityRole="button"'), true);
  assert.equal(primitives.includes('accessibilityState={{ disabled: isDisabled, busy: loading }}'), true);
  assert.equal(primitives.includes('minHeight: touchTarget'), true);
});

test('operational home is role-aware and uses the shared design shell', () => {
  const home = source('src/app/index.tsx');
  assert.equal(home.includes('<ScreenShell'), true);
  assert.equal(home.includes('<RoleBadge role={roleBadge}'), true);
  assert.equal(home.includes('activeRole ==='), true);
});
