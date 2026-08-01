import assert from 'node:assert/strict';
import test from 'node:test';

import { palette, radii, roleAccent, spacing, touchTarget } from '../design/tokens';

test('operational design system uses the MyPet brand foundation', () => {
  assert.equal(palette.royalBlue, '#004AC6');
  assert.equal(palette.amber, '#FEA619');
  assert.equal(palette.emerald, '#10B981');
  assert.equal(touchTarget, 48);
});

test('spacing and radius values stay on the approved design grid', () => {
  for (const value of Object.values(spacing)) {
    assert.equal(value % 4, 0);
  }
  assert.equal(radii.compact, 8);
  assert.equal(radii.card, 16);
  assert.equal(radii.feature, 24);
});

test('role accents distinguish workflows without replacing the core brand', () => {
  const merchant = roleAccent('merchant', 'light');
  const captain = roleAccent('captain', 'light');
  const admin = roleAccent('admin', 'light');

  assert.notEqual(merchant.accent, captain.accent);
  assert.equal(admin.accent, palette.royalBlue);
});
