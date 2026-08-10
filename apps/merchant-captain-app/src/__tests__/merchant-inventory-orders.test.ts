import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

import {
  isMerchantOrderInQueue,
  merchantOrderActions,
} from '../contracts/merchant-order-lifecycle';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('merchant order actions follow the canonical server-authorized lifecycle', () => {
  assert.deepEqual(merchantOrderActions('PLACED'), [
    { status: 'ACCEPTED', label: 'Accept order' },
    { status: 'REJECTED', label: 'Reject order', destructive: true },
  ]);
  assert.deepEqual(merchantOrderActions('ACCEPTED'), [
    { status: 'PREPARING', label: 'Start preparing' },
    { status: 'CANCELLED', label: 'Cancel order', destructive: true },
  ]);
  assert.deepEqual(merchantOrderActions('PREPARING'), [
    { status: 'READY_FOR_PICKUP', label: 'Mark ready for pickup' },
  ]);
  assert.deepEqual(merchantOrderActions('READY_FOR_PICKUP'), []);
  assert.deepEqual(merchantOrderActions('DELIVERED'), []);
});

test('merchant new queue exposes COD and paid online PLACED orders only', () => {
  assert.equal(isMerchantOrderInQueue('PLACED', 'COD_PENDING', 'NEW'), true);
  assert.equal(isMerchantOrderInQueue('PLACED', 'SUCCESS', 'NEW'), true);
  assert.equal(isMerchantOrderInQueue('PLACED', 'PENDING', 'NEW'), false);
  assert.equal(isMerchantOrderInQueue('PLACED', 'FAILED', 'NEW'), false);
  assert.equal(isMerchantOrderInQueue('ACCEPTED', 'SUCCESS', 'NEW'), false);
});

test('merchant queues map one-to-one to canonical lifecycle phases', () => {
  assert.equal(isMerchantOrderInQueue('ACCEPTED', 'SUCCESS', 'ACCEPTED'), true);
  assert.equal(isMerchantOrderInQueue('PREPARING', 'SUCCESS', 'PREPARING'), true);
  assert.equal(isMerchantOrderInQueue('READY_FOR_PICKUP', 'SUCCESS', 'READY'), true);
  assert.equal(isMerchantOrderInQueue('ASSIGNED', 'SUCCESS', 'DELIVERY'), true);
  assert.equal(isMerchantOrderInQueue('PICKED_UP', 'SUCCESS', 'DELIVERY'), true);
  assert.equal(isMerchantOrderInQueue('DELIVERED', 'SUCCESS', 'PAST'), true);
  assert.equal(isMerchantOrderInQueue('COMPLETED', 'SUCCESS', 'PAST'), true);
  assert.equal(isMerchantOrderInQueue('CANCELLED', 'SUCCESS', 'PAST'), true);
  assert.equal(isMerchantOrderInQueue('REJECTED', 'SUCCESS', 'PAST'), true);
});

test('merchant order workspace uses provider-scoped reads and specific-order alert navigation', () => {
  const service = source('src/services/merchant-orders.ts');
  const screen = source('src/app/orders.tsx');
  const detail = source('src/app/orders/[id].tsx');
  const layout = source('src/app/_layout.tsx');
  const tabs = source('src/components/app-tabs.tsx');
  const home = source('src/app/index.tsx');
  const alert = source('src/components/order-incoming-alert.tsx');

  assert.match(service, /\/api\/v1\/orders\/provider\//);
  assert.match(service, /\/api\/v1\/orders\/\$\{encodeURIComponent\(orderId\)\}/);
  assert.match(service, /\/status\?/);
  assert.match(screen, /isMerchantOrderInQueue/);
  assert.match(screen, /formatCurrency/);
  assert.match(screen, /apiErrorKind/);
  assert.match(detail, /Accept order/);
  assert.match(detail, /Reject order/);
  assert.match(layout, /pathname\.startsWith\('\/orders'\)/);
  assert.match(tabs, /name:\s*'orders'/);
  assert.match(tabs, /href:\s*'\/orders'/);
  assert.match(tabs, /visible:\s*isProvider/);
  assert.match(home, /router\.push\(`\/orders\/\$\{encodeURIComponent\(orderId\)\}`/);
  assert.match(alert, /View order/);
  assert.doesNotMatch(alert, /Pack order/);
});

test('inventory is INR-based, connected and free of legacy offline catalog fixtures', () => {
  const screen = source('src/app/inventory.tsx');
  const service = source('src/services/merchant-inventory.ts');

  assert.match(screen, /Price \(₹\)/);
  assert.match(screen, /formatCurrency/);
  assert.match(screen, /apiErrorMessage/);
  assert.match(screen, /Low stock/);
  assert.doesNotMatch(screen, /Price \(\$\)/);
  assert.doesNotMatch(screen, /OFFLINE_MOCK_OFFERINGS/);
  assert.doesNotMatch(screen, /DEMO_PROVIDERS/);
  assert.match(service, /\/api\/v1\/catalog\/offerings/);
  assert.match(service, /createMerchantOffering/);
  assert.match(service, /updateMerchantOffering/);
  assert.match(service, /deleteMerchantOffering/);
});
