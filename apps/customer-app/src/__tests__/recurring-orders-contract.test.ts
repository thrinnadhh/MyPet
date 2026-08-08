import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { isRecurringCadence, RECURRING_CADENCES } from '../contracts/recurring-orders';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

describe('recurring order contract', () => {
  it('limits cadence to the approved intervals', () => {
    expect(RECURRING_CADENCES).toEqual([7, 15, 25, 30, 35]);
    expect(isRecurringCadence(25)).toBe(true);
    expect(isRecurringCadence(10)).toBe(false);
  });

  it('shows generated orders without promising silent prepaid charging', () => {
    const screen = source('src/app/subscriptions/index.tsx');
    const service = source('src/services/recurring-orders.ts');
    expect(screen).toMatch(/One scheduled run creates at most one real order/);
    expect(screen).toMatch(/Payment is never charged silently/);
    expect(screen).toMatch(/Generated order/);
    expect(screen).not.toMatch(/Create recurring reminder/);
    expect(service).toMatch(/\/api\/v1\/orders\/subscriptions/);
    expect(service).toMatch(/occurrences/);
  });

  it('requires an idempotent operational order with durable occurrence reconciliation', () => {
    const backend = source('../../backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/RecurringOrderService.kt');
    const creator = source('../../backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/RecurringOccurrenceOrderCreator.kt');
    const scheduler = source('../../backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/RecurringOrderScheduler.kt');
    const migration = source('../../backend/order-service/src/main/resources/db/migration/V1003__recurring_order_execution.sql');
    expect(backend).toMatch(/RecurringOrderGenerated/);
    expect(backend).toMatch(/occurrenceOrderCreator\.createOrGet/);
    expect(backend).toMatch(/deterministicOccurrenceId/);
    expect(backend).toMatch(/PRICE_CHANGED/);
    expect(creator).toMatch(/Propagation\.REQUIRES_NEW/);
    expect(creator).toMatch(/findByRecurringOccurrenceId/);
    expect(creator).toMatch(/R-\$occurrenceId/);
    expect(scheduler).toMatch(/processDueOrders/);
    expect(scheduler).toMatch(/recurringOrderGeneration/);
    expect(migration).toMatch(/UNIQUE \(subscription_id, scheduled_for\)/);
    expect(migration).toMatch(/recurring_order_subscription_items/);
    expect(migration).toMatch(/uq_orders_recurring_occurrence/);
  });
});