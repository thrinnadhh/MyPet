import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const appSource = await readFile(new URL('./app.js', import.meta.url), 'utf8');
const secureAdminSource = await readFile(new URL('./secure-admin.js', import.meta.url), 'utf8');
const indexHtml = await readFile(new URL('./index.html', import.meta.url), 'utf8');

test('dynamic admin rendering does not inject API data through innerHTML', () => {
  assert.doesNotMatch(appSource, /innerHTML\s*=/i);
  assert.match(appSource, /textContent\s*=/);
  assert.match(appSource, /replaceChildren\(/);
});

test('provider rejection is a real reasoned API action rather than a simulation', () => {
  assert.doesNotMatch(appSource, /simulat(?:ed|ion)|scaffold reject/i);
  assert.match(appSource, /Enter the reason for rejecting this provider/);
  assert.match(appSource, /JSON\.stringify\(\{ reason \}\)/);
  assert.match(appSource, /\/reject/);
});

test('admin access token remains closure-scoped and never persists in web storage', () => {
  assert.doesNotMatch(appSource, /function\s+getAdminToken\s*\(/);
  assert.doesNotMatch(secureAdminSource, /window\.getAdminToken\s*=/);
  assert.doesNotMatch(secureAdminSource, /localStorage\.setItem\([^)]*token/i);
  assert.doesNotMatch(secureAdminSource, /sessionStorage\.setItem\([^)]*token/i);
  assert.match(secureAdminSource, /persistSession:\s*false/);
});

test('bearer token is only attached after exact configured API origin validation', () => {
  assert.match(secureAdminSource, /candidate\.origin !== configuredApiUrl\.origin/);
  assert.match(secureAdminSource, /candidate\.pathname\.startsWith/);
  assert.doesNotMatch(secureAdminSource, /rewrittenUrl\.startsWith\(configuredApiBaseUrl\)/);
  assert.match(secureAdminSource, /headers\.set\('Authorization', `Bearer \$\{adminSession\.access_token\}`\)/);
});

test('client supplied identity and internal trust headers are stripped', () => {
  for (const header of [
    'X-User-Id',
    'X-User-Role',
    'X-User-Email',
    'X-User-Full-Name',
    'X-User-Phone',
    'X-Admin-Api-Key',
    'X-Internal-Gateway-Secret',
    'X-Internal-Secret',
    'X-Service-Name',
  ]) {
    assert.match(secureAdminSource, new RegExp(header));
  }
  assert.match(secureAdminSource, /forbiddenIdentityHeaders\.forEach/);
});

test('console does not ship fabricated production indicators', () => {
  assert.doesNotMatch(indexHtml, /LOCAL_SANDBOX/i);
  assert.doesNotMatch(indexHtml, /11\s+ONLINE/i);
  assert.doesNotMatch(indexHtml, /12\.5%/);
  assert.match(indexHtml, /metric-active-orders/);
  assert.match(appSource, /\/api\/v1\/orders\/admin\/operations\/snapshot/);
});

test('user management is paginated and generic admin suspension is not exposed', () => {
  assert.match(appSource, /\/api\/v1\/profiles\/admin\?page=/);
  assert.match(appSource, /Protected Admin identity/);
  assert.match(appSource, /USER_PAGE_SIZE\s*=\s*25/);
});

test('admin app has no production localhost API fallback', () => {
  assert.doesNotMatch(appSource, /http:\/\/localhost:8080/);
  assert.doesNotMatch(secureAdminSource, /http:\/\/localhost:8080/);
});
