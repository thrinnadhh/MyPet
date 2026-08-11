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
  customerPhone?: string | null;
  customerPhoneVerified?: boolean;
  merchantName?: string | null;
  pickupAddress?: string | null;
  pickupLatitude?: number | null;
  pickupLongitude?: number | null;
  dropAddress?: string | null;
  dropLatitude?: number | null;
  dropLongitude?: number | null;
  pickupDistanceKm?: number | null;
  pickupEtaMinutes?: number | null;
  deliveryDistanceKm?: number | null;
  deliveryEtaMinutes?: number | null;
}

export function isActiveCaptainJob(job: CaptainDeliveryJob): boolean {
  return job.status === 'ACCEPTED' || job.status === 'PICKED_UP';
}

export function deliveryStepForStatus(status: CaptainJobStatus): 1 | 3 {
  return status === 'PICKED_UP' ? 3 : 1;
}
