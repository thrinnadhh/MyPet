import {
  type AdminAuditLog,
  type AdminDispute,
  type AdminOperationsSnapshot,
  type AdminProviderApproval,
  type AdminServiceArea,
  type AdminServiceAreaDraft,
  type AdminSupportCase,
} from '@/contracts/admin-operations';
import { apiErrorFromResponse } from '@/contracts/api-error';
import { appConfig } from '@/utils/app-config';

async function request<T>(path: string, accessToken: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${appConfig.apiBaseUrl}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
      ...((init.headers as Record<string, string> | undefined) ?? {}),
    },
  });
  if (!response.ok) throw await apiErrorFromResponse(response);
  if (response.status === 204) return {} as T;
  return (await response.json()) as T;
}

export function fetchAdminOperationsSnapshot(accessToken: string): Promise<AdminOperationsSnapshot> {
  return request('/api/v1/orders/admin/operations/snapshot', accessToken);
}

export function fetchAdminServiceAreas(accessToken: string): Promise<AdminServiceArea[]> {
  return request('/api/v1/orders/admin/operations/service-areas', accessToken);
}

export function saveAdminServiceArea(
  draft: AdminServiceAreaDraft,
  accessToken: string,
): Promise<AdminServiceArea> {
  return request(`/api/v1/orders/admin/operations/service-areas/${draft.pincode.trim()}`, accessToken, {
    method: 'PUT',
    body: JSON.stringify({
      city: draft.city.trim(),
      enabled: draft.enabled,
      deliveryEnabled: draft.deliveryEnabled,
      serviceRadiusKm: draft.serviceRadiusKm,
      emergencyMessage: draft.emergencyMessage?.trim() || null,
      reason: draft.reason.trim(),
    }),
  });
}

export function fetchAdminAuditLogs(accessToken: string, limit = 50): Promise<AdminAuditLog[]> {
  return request(`/api/v1/orders/admin/operations/audit-logs?limit=${Math.min(100, Math.max(1, limit))}`, accessToken);
}

export function fetchPendingProviderApprovals(accessToken: string): Promise<AdminProviderApproval[]> {
  return request('/api/v1/providers/pending', accessToken);
}

export function approveProviderFromAdmin(providerId: string, accessToken: string): Promise<AdminProviderApproval> {
  return request(`/api/v1/providers/${providerId}/approve`, accessToken, { method: 'POST' });
}

export function fetchAdminDisputes(accessToken: string): Promise<AdminDispute[]> {
  return request('/api/v1/orders/disputes', accessToken);
}

export function resolveAdminDispute(
  disputeId: string,
  decision: 'RESOLVED' | 'REJECTED',
  resolutionNotes: string,
  accessToken: string,
): Promise<AdminDispute> {
  return request(`/api/v1/orders/disputes/${disputeId}/resolve`, accessToken, {
    method: 'POST',
    body: JSON.stringify({ decision, resolutionNotes }),
  });
}

export function fetchAdminSupportCases(accessToken: string): Promise<AdminSupportCase[]> {
  return request('/api/v1/orders/admin/support-cases', accessToken);
}
