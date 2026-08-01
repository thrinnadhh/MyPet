import assert from 'node:assert/strict';
import test from 'node:test';

import {
  ApiError,
  apiErrorKind,
  normalizeApiErrorPayload,
  parseRetryAfter,
} from '../contracts/api-error';
import {
  formatCurrency,
  formatDeliveryStatus,
  formatDistance,
  formatOrderStatus,
  formatStatusLabel,
} from '../utils/formatters';

test('operational API errors retain code, trace and field details', () => {
  const payload = normalizeApiErrorPayload(
    409,
    'Conflict',
    {
      error: {
        code: 'STALE_ORDER_STATE',
        message: 'Refresh the order before updating it.',
        fieldErrors: { status: ['The order has already changed.'] },
      },
    },
    'request-44',
  );

  assert.equal(payload.code, 'STALE_ORDER_STATE');
  assert.equal(payload.message, 'Refresh the order before updating it.');
  assert.equal(payload.traceId, 'request-44');
  assert.deepEqual(payload.fieldErrors, { status: ['The order has already changed.'] });
});

test('operational API errors expose retry and presentation categories', () => {
  const conflict = new ApiError(409, {
    code: 'CONFLICT',
    message: 'State changed',
    fieldErrors: {},
  });
  const server = new ApiError(503, {
    code: 'UNAVAILABLE',
    message: 'Try again',
    fieldErrors: {},
  });

  assert.equal(apiErrorKind(conflict), 'conflict');
  assert.equal(apiErrorKind(server), 'server');
  assert.equal(parseRetryAfter('8'), 8);
});

test('operational formatting uses one commerce and lifecycle vocabulary', () => {
  assert.match(formatCurrency(9876.5), /9,876\.5/);
  assert.equal(formatDistance(2100), '2.1 km');
  assert.equal(formatOrderStatus('CAPTAIN_ASSIGNED'), 'Captain assigned');
  assert.equal(formatDeliveryStatus('PICKUP_PENDING'), 'Pickup pending');
  assert.equal(formatStatusLabel('TRANSFER_REVERSED'), 'Transfer Reversed');
});
