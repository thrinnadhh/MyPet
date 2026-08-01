import { apiClient } from './api-client';

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

export async function fetchCaptainJobs(): Promise<CaptainDeliveryJob[]> {
  const jobs = await apiClient.get<CaptainDeliveryJob[]>('/api/v1/dispatch/jobs/me');
  return [...jobs].sort((left, right) => {
    const leftTime = new Date(left.assignedAt ?? left.createdAt).getTime();
    const rightTime = new Date(right.assignedAt ?? right.createdAt).getTime();
    return rightTime - leftTime;
  });
}

export async function submitCaptainProof(
  jobId: string,
  kind: 'pickup' | 'deliver',
  proofCode: string,
): Promise<CaptainDeliveryJob> {
  return apiClient.post<CaptainDeliveryJob>(
    `/api/v1/dispatch/jobs/${encodeURIComponent(jobId)}/${kind}`,
    { proofCode },
  );
}
