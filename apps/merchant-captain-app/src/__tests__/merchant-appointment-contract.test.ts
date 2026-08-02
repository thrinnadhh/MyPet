import assert from 'node:assert/strict';
import test from 'node:test';

import {
  appointmentActions,
  appointmentMatchesSearch,
  appointmentQueue,
} from '../contracts/merchant-appointment';

test('confirmed appointments expose only merchant terminal actions', () => {
  assert.deepEqual(appointmentActions('CONFIRMED'), ['COMPLETED', 'NO_SHOW', 'CANCELLED']);
  assert.deepEqual(appointmentActions('COMPLETED'), []);
  assert.deepEqual(appointmentActions('NO_SHOW'), []);
});

test('appointments are classified into operational queues', () => {
  const now = new Date('2026-08-02T09:00:00+05:30');
  assert.equal(appointmentQueue('CONFIRMED', '2026-08-02T12:00:00+05:30', now), 'TODAY');
  assert.equal(appointmentQueue('CONFIRMED', '2026-08-03T12:00:00+05:30', now), 'UPCOMING');
  assert.equal(appointmentQueue('COMPLETED', '2026-08-01T12:00:00+05:30', now), 'COMPLETED');
  assert.equal(appointmentQueue('NO_SHOW', '2026-08-01T12:00:00+05:30', now), 'NO_SHOW');
  assert.equal(appointmentQueue('EXPIRED', '2026-08-01T12:00:00+05:30', now), 'CANCELLED');
});

test('search matches customer pet service and identifier fields', () => {
  assert.equal(appointmentMatchesSearch('bruno', ['Customer 12ab', 'Bruno', 'Vaccination']), true);
  assert.equal(appointmentMatchesSearch('groom', ['Customer 12ab', 'Milo', 'Full Grooming']), true);
  assert.equal(appointmentMatchesSearch('missing', ['Customer 12ab', 'Milo']), false);
});
