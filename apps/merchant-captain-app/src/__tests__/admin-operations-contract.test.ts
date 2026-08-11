import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

import { validateServiceAreaDraft } from '../contracts/admin-operations';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('admin authority is consolidated into the server-backed web control plane', () => {
  const mobileScreen = source('src/app/admin.tsx');
  const legacyService = source('src/services/admin-operations.ts');
  const webConsole = source('../super-admin-web/secure-admin.js');

  assert.match(mobileScreen, /compatibility route/i);
  assert.match(mobileScreen, /Use the Admin web console/);
  assert.match(mobileScreen, /role !== 'ADMIN'/);
  assert.doesNotMatch(mobileScreen, /fetchAdminOperationsSnapshot|fetchAdminServiceAreas|fetchAdminAuditLogs/);
  assert.doesNotMatch(mobileScreen, /DEMO_PROVIDERS|DEMO_CAPTAINS|DEMO_DISPUTES|Demo approval/);

  assert.match(webConsole, /\/api\/v1\/orders\/admin\/operations\/snapshot/);
  assert.match(webConsole, /ordersPlaced/);
  assert.match(webConsole, /merchantPending/);
  assert.match(webConsole, /dispatchFailures/);
  assert.match(webConsole, /paymentFailures/);
  assert.match(webConsole, /openSupportCases/);
  assert.match(webConsole, /getRole\(session\.user\) === 'ADMIN'/);
  assert.doesNotMatch(webConsole, /SUPER_ADMIN/);

  // Legacy service wrappers may remain for compatibility, but they target the
  // same canonical backend Admin API rather than defining another lifecycle.
  assert.match(legacyService, /\/api\/v1\/orders\/admin\/operations\/snapshot/);
  assert.match(legacyService, /\/service-areas/);
  assert.match(legacyService, /\/audit-logs/);
});

test('service area changes require valid pincode radius and administrative reason', () => {
  assert.deepEqual(
    validateServiceAreaDraft({
      pincode: '517501',
      city: 'Tirupati',
      enabled: true,
      deliveryEnabled: true,
      serviceRadiusKm: 8,
      reason: 'Controlled pilot',
    }),
    {},
  );
  const errors = validateServiceAreaDraft({
    pincode: '123',
    city: '',
    enabled: true,
    deliveryEnabled: true,
    serviceRadiusKm: 0,
    reason: '',
  });
  assert.equal(Boolean(errors.pincode), true);
  assert.equal(Boolean(errors.city), true);
  assert.equal(Boolean(errors.serviceRadiusKm), true);
  assert.equal(Boolean(errors.reason), true);
});

test('backend persists audit and service-area records with trace identity', () => {
  const migration = source('../../backend/order-service/src/main/resources/db/migration/V1000__p2b_admin_operations.sql');
  const controller = source('../../backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/controller/AdminOperationsController.kt');
  const service = source('../../backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/AdminOperationsService.kt');
  assert.match(migration, /admin_audit_logs/);
  assert.match(migration, /service_area_configs/);
  assert.match(controller, /X-Request-Id/);
  assert.match(controller, /Administrator role required/);
  assert.match(service, /auditRepository\.save/);
  assert.match(service, /reason = reason/);
});
