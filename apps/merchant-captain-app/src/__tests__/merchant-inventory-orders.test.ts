import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

import { merchantOrderActions } from '../services/merchant-orders';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

test('merchant order actions follow the server-authorized lifecycle', () => {
  assert.deepEqual(merchantOrderActions('PLACED'), [
    { status: 'ACCEPTED', label: 'Accept order' },
    { status: 'REJECTED', label: 'Reject order', destructive: true },
  ]);
  assert.deepEqual(merchantOrderActions('ACCEPTED'), [
    { status: 'PREPARING', label: 'Start packing' },
    { status: 'CANCELLED', label: 'Cancel order', destructive: true },
  ]);
  assert.deepEqual(merchantOrderActions('PREPARING'), [
    { status: 'READY_FOR_PICKUP', label: 'Mark ready for pickup' },
  ]);
  assert.deepEqual(merchantOrderActions('READY_FOR_PICKUP'), []);
  assert.deepEqual(merchantOrderActions('DELIVERED'), []);
});

test('merchant order workspace uses provider-scoped reads and server transitions', () => {
  const service = source('src/services/merchant-orders.ts');
  const screen = source('src/app/orders.tsx');
  const layout = source('src/app/_layout.tsx');
  const tabs = source('src/components/app-tabs.tsx');
  const home = source('src/app/index.tsx');

  assert.match(service, /\/api\/v1\/orders\/provider\//);
  assert.match(service, /\/status\?/);
  assert.match(screen, /merchantOrderActions/);
  assert.match(screen, /formatCurrency/);
  assert.match(screen, /apiErrorKind/);
  assert.match(layout, /pathname\.startsWith\('\/orders'\)/);
  assert.match(tabs, /name="orders"/);
  assert.match(home, /router\.push\('\/orders'/);
  assert.doesNotMatch(home, /router\.push\('\/explore'.*incomingOrder/s);
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
