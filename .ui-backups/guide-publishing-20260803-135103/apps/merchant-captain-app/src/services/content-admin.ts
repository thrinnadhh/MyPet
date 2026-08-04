import { appConfig } from '@/utils/app-config';

function jsonHeaders(accessToken?: string | null): Record<string, string> {
  const headers: Record<string, string> = { Accept: 'application/json', 'Content-Type': 'application/json' };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  return headers;
}

export async function fetchBanners(accessToken?: string | null) {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/content/banners`, { headers: jsonHeaders(accessToken) });
  if (!response.ok) throw new Error('Could not load banners');
  return response.json();
}

export async function fetchGuideWriters(accessToken?: string | null) {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/content/guides/writers`, { headers: jsonHeaders(accessToken) });
  if (!response.ok) throw new Error('Could not load guide writers');
  return response.json();
}

export async function revokeGuideWriter(writerId: string, accessToken?: string | null) {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/content/guides/writers/${writerId}`, {
    method: 'DELETE',
    headers: jsonHeaders(accessToken),
  });
  if (!response.ok) throw new Error('Could not revoke writer');
}
