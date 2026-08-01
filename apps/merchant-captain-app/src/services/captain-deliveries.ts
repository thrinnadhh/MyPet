import type { CaptainDeliveryJob } from '../contracts/captain-delivery-lifecycle';
import { apiClient } from './api-client';

export {
  deliveryStepForStatus,
  isActiveCaptainJob,
  type CaptainDeliveryJob,
  type CaptainJobStatus,
} from '../contracts/captain-delivery-lifecycle';

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
