import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

import { isRecurringCadence, RECURRING_CADENCES } from '../contracts/recurring-orders';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('recurring order cadence is limited to approved intervals', () => {
  assert.deepEqual(RECURRING_CADENCES, [7, 15, 25, 30, 35]);
  assert.equal(isRecurringCadence(25), true);
  assert.equal(isRecurringCadence(10), false);
});

test('customer subscriptions require confirmation and fresh checkout', () => {
  const screen = source('src/app/subscriptions/index.tsx');
  const service = source('src/services/recurring-orders.ts');
  assert.match(screen, /No silent charging/);
  assert.match(screen, /Revalidate and confirm/);
  assert.match(screen, /router\.push\('\/cart'/);
  assert.match(service, /\/api\/v1\/orders\/subscriptions/);
  assert.doesNotMatch(screen, /automatic charge|auto-charge/i);
});

test('backend scheduler only requests confirmation and never creates an order', () => {
  const backend = source('../../backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/RecurringOrderService.kt');
  const migration = source('../../backend/order-service/src/main/resources/db/migration/V1001__p2b_recurring_orders.sql');
  assert.match(backend, /RecurringOrderConfirmationRequired/);
  assert.match(backend, /automaticCharge" to false/);
  assert.match(backend, /revalidateReorder/);
  assert.doesNotMatch(backend, /orderService\.createOrder/);
  assert.match(migration, /7, 15, 25, 30, 35/);
});
