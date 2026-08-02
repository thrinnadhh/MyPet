import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const appSource = await readFile(new URL('./app.js', import.meta.url), 'utf8');
const secureAdminSource = await readFile(new URL('./secure-admin.js', import.meta.url), 'utf8');

test('dispute rendering never embeds server data in inline JavaScript', () => {
  const start = appSource.indexOf('async function fetchDisputes');
  const end = appSource.indexOf('function openDisputeModal');
  const disputeRenderer = appSource.slice(start, end);

  assert.ok(start >= 0 && end > start, 'dispute renderer must be present');
  assert.doesNotMatch(disputeRenderer, /onclick\s*=/i);
  assert.doesNotMatch(disputeRenderer, /innerHTML\s*=/);
});

test('admin access token is not exposed through a global helper', () => {
  assert.doesNotMatch(appSource, /function\s+getAdminToken\s*\(/);
  assert.doesNotMatch(secureAdminSource, /window\.getAdminToken\s*=/);
  assert.doesNotMatch(secureAdminSource, /headers\.Authorization\s*=\s*`Bearer/);
  assert.match(secureAdminSource, /persistSession:\s*false/);
});
