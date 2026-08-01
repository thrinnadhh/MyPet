import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('operational app declares Expo native and background location modules', () => {
  const packageJson = JSON.parse(source('package.json')) as { dependencies: Record<string, string> };
  assert.equal(packageJson.dependencies['expo-location'], '~56.0.22');
  assert.equal(packageJson.dependencies['expo-task-manager'], '~56.0.24');
});

test('Expo config enables native location permissions and foreground service', () => {
  const appJson = source('app.json');
  assert.match(appJson, /"expo-location"/);
  assert.match(appJson, /"isIosBackgroundLocationEnabled": true/);
  assert.match(appJson, /"isAndroidBackgroundLocationEnabled": true/);
  assert.match(appJson, /"isAndroidForegroundServiceEnabled": true/);
});

test('captain location task is registered before router navigation', () => {
  const layout = source('src/app/_layout.tsx');
  const service = source('src/services/captain-location.ts');
  assert.match(layout, /import '@\/services\/captain-location';/);
  assert.match(service, /TaskManager\.defineTask/);
  assert.match(service, /Location\.startLocationUpdatesAsync/);
  assert.match(service, /Location\.stopLocationUpdatesAsync/);
});

test('captain coordinates use the authenticated API gateway contract', () => {
  const service = source('src/services/captain-location.ts');
  assert.match(service, /\$\{session\.apiBaseUrl\}\/api\/v1\/captains\/location/);
  assert.match(service, /Authorization: `Bearer \$\{session\.accessToken\}`/);
  assert.match(service, /'X-User-Id': session\.userId/);
  assert.match(service, /accuracyMeters/);
  assert.match(service, /capturedAt/);
});

test('captain delivery uses device location and never restores browser-only fallback', () => {
  const delivery = source('src/app/delivery.tsx');
  assert.match(delivery, /getCaptainCoordinates/);
  assert.match(delivery, /startCaptainLocationTracking/);
  assert.match(delivery, /stopCaptainLocationTracking/);
  assert.equal(delivery.includes('browserCoordinates'), false);
  assert.equal(delivery.includes('does not yet include the native location module'), false);
});

test('sign out and cleared sessions stop background tracking', () => {
  const auth = source('src/context/AuthContext.tsx');
  assert.match(auth, /await stopCaptainLocationTracking\(\)/);
});
