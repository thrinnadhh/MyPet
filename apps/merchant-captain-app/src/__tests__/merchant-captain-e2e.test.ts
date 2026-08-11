import assert from 'node:assert/strict';
import { describe, test } from 'node:test';

import { canOrderTransition, type OrderStatus } from '../contracts/order-contract.generated';

describe('Merchant & Captain canonical order flow', () => {
  test('E2E contract: merchant fulfillment -> dispatch assignment -> captain delivery -> system completion', () => {
    let status: OrderStatus = 'PLACED';

    assert.equal(canOrderTransition(status, 'ACCEPTED', 'MERCHANT'), true);
    status = 'ACCEPTED';

    assert.equal(canOrderTransition(status, 'PREPARING', 'MERCHANT'), true);
    assert.equal(canOrderTransition(status, 'READY_FOR_PICKUP', 'MERCHANT'), false);
    status = 'PREPARING';

    assert.equal(canOrderTransition(status, 'READY_FOR_PICKUP', 'MERCHANT'), true);
    status = 'READY_FOR_PICKUP';

    assert.equal(canOrderTransition(status, 'ASSIGNED', 'DISPATCH'), true);
    assert.equal(canOrderTransition(status, 'ASSIGNED', 'MERCHANT'), false);
    status = 'ASSIGNED';

    assert.equal(canOrderTransition(status, 'DELIVERED', 'CAPTAIN'), false);
    assert.equal(canOrderTransition(status, 'PICKED_UP', 'CAPTAIN'), true);
    status = 'PICKED_UP';

    assert.equal(canOrderTransition(status, 'DELIVERED', 'CAPTAIN'), true);
    status = 'DELIVERED';

    assert.equal(canOrderTransition(status, 'COMPLETED', 'SYSTEM'), true);
    status = 'COMPLETED';

    assert.equal(status, 'COMPLETED');
  });
});
