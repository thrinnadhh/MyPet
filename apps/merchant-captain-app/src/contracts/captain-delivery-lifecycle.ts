export type CaptainJobStatus =
  | 'PENDING_ASSIGNMENT'
  | 'OFFERED'
  | 'ACCEPTED'
  | 'PICKED_UP'
  | 'REJECTED'
  | 'TIMED_OUT'
  | 'COMPLETED'
  | 'FAILED';

export interface CaptainDeliveryJob {
  jobId: string;
  orderId: string;
  status: CaptainJobStatus;
  attemptCount: number;
  createdAt: string;
  resolvedAt?: string | null;
  assignedAt?: string | null;
}

export function isActiveCaptainJob(job: CaptainDeliveryJob): boolean {
  return job.status === 'ACCEPTED' || job.status === 'PICKED_UP';
}

export function deliveryStepForStatus(status: CaptainJobStatus): 1 | 3 {
  return status === 'PICKED_UP' ? 3 : 1;
}
