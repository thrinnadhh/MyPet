import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';

import {
  deliveryStepForStatus,
  isActiveCaptainJob,
  type CaptainDeliveryJob,
} from '../services/captain-deliveries';

function source(path: string): string {
  return readFileSync(join(process.cwd(), path), 'utf8');
}

const job = (status: CaptainDeliveryJob['status']): CaptainDeliveryJob => ({
  jobId: 'job-1',
  orderId: 'order-1',
  status,
  attemptCount: 1,
  createdAt: new Date(0).toISOString(),
});

test('accepted and picked-up jobs are resumable at the correct step', () => {
  assert.equal(isActiveCaptainJob(job('ACCEPTED')), true);
  assert.equal(isActiveCaptainJob(job('PICKED_UP')), true);
  assert.equal(isActiveCaptainJob(job('COMPLETED')), false);
  assert.equal(deliveryStepForStatus('ACCEPTED'), 1);
  assert.equal(deliveryStepForStatus('PICKED_UP'), 3);
});

test('captain app restores server jobs and never expects OTPs in job history', () => {
  const service = source('src/services/captain-deliveries.ts');
  const screen = source('src/app/delivery.tsx');

  assert.match(service, /\/api\/v1\/dispatch\/jobs\/me/);
  assert.doesNotMatch(service, /pickupOtp|deliveryOtp/);
  assert.match(screen, /fetchCaptainJobs/);
  assert.match(screen, /startActiveTracking/);
  assert.match(screen, /Delivery history/);
  assert.match(screen, /Background tracking begins after acceptance/);
});

test('captain proof sequence uses picked-up state before delivery completion', () => {
  const backendService = source('../../backend/dispatch-service/src/main/kotlin/com/pawsnearme/dispatchservice/service/DispatchService.kt');
  const controller = source('../../backend/dispatch-service/src/main/kotlin/com/pawsnearme/dispatchservice/controller/DispatchController.kt');

  assert.match(backendService, /job\.status = JobStatus\.PICKED_UP/);
  assert.match(backendService, /setOf\(JobStatus\.PICKED_UP\)/);
  assert.match(controller, /DispatchJobView/);
  assert.doesNotMatch(controller, /val pickupOtp|val deliveryOtp/);
  assert.match(controller, /\/jobs\/me/);
});
