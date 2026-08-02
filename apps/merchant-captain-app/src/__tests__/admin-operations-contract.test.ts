import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

import { validateServiceAreaDraft } from '../contracts/admin-operations';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('admin portal is server backed and contains no production demo queues', () => {
  const screen = source('src/app/admin.tsx');
  const service = source('src/services/admin-operations.ts');
  assert.match(screen, /fetchAdminOperationsSnapshot/);
  assert.match(screen, /fetchAdminServiceAreas/);
  assert.match(screen, /fetchAdminAuditLogs/);
  assert.match(screen, /role !== 'ADMIN'/);
  assert.doesNotMatch(screen, /DEMO_PROVIDERS|DEMO_CAPTAINS|DEMO_DISPUTES|Demo approval/);
  assert.match(service, /\/api\/v1\/orders\/admin\/operations\/snapshot/);
  assert.match(service, /\/service-areas/);
  assert.match(service, /\/audit-logs/);
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
