import { appConfig } from '@/utils/app-config';

export interface MerchantAlert {
  notificationId: string;
  title: string;
  body: string;
  referenceId: string | null;
  priority: string;
  createdAt: string;
}

function headers(accessToken?: string | null): Record<string, string> {
  const result: Record<string, string> = { Accept: 'application/json' };
  if (accessToken) result.Authorization = `Bearer ${accessToken}`;
  return result;
}

export async function fetchUnreadMerchantAlerts(accessToken?: string | null): Promise<MerchantAlert[]> {
  if (appConfig.allowDemoMode) return [];
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/notifications/in-app/me/unread`, {
    headers: headers(accessToken),
  });
  if (!response.ok) return [];
  const rows = (await response.json()) as Array<MerchantAlert & { notificationId: string }>;
  return rows
    .filter((row) => row.priority === 'HIGH')
    .map((row) => ({ ...row, notificationId: String(row.notificationId) }));
}

export async function markAlertRead(notificationId: string, accessToken?: string | null): Promise<void> {
  if (appConfig.allowDemoMode) return;
  await fetch(`${appConfig.apiBaseUrl}/api/v1/notifications/in-app/${notificationId}/read`, {
    method: 'PATCH',
    headers: headers(accessToken),
  });
}
