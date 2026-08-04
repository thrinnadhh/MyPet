import { appConfig } from '@/utils/app-config';

export type GuideWriter = {
  writerId: string;
  userId: string;
  email: string;
  authorName: string;
  companyName: string;
  accessStatus: 'ACTIVE' | 'REVOKED';
  createdAt: string;
  updatedAt: string;
};

export type GrantGuideWriterInput = {
  userId: string;
  email: string;
  authorName: string;
  companyName: string;
};

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

export async function fetchGuideWriters(accessToken?: string | null): Promise<GuideWriter[]> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/content/guides/writers`, { headers: jsonHeaders(accessToken) });
  if (!response.ok) throw new Error('Could not load guide writers');
  return response.json() as Promise<GuideWriter[]>;
}

export async function grantGuideWriter(
  input: GrantGuideWriterInput,
  accessToken?: string | null,
): Promise<GuideWriter> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/content/guides/writers`, {
    method: 'POST',
    headers: jsonHeaders(accessToken),
    body: JSON.stringify(input),
  });
  if (!response.ok) throw new Error('Could not grant guide-writer access');
  return response.json() as Promise<GuideWriter>;
}

export async function setGuideWriterAccess(
  writerId: string,
  active: boolean,
  accessToken?: string | null,
): Promise<GuideWriter> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/content/guides/writers/${writerId}/status`, {
    method: 'PUT',
    headers: jsonHeaders(accessToken),
    body: JSON.stringify({ active }),
  });
  if (!response.ok) throw new Error('Could not update writer access');
  return response.json() as Promise<GuideWriter>;
}

export async function revokeGuideWriter(writerId: string, accessToken?: string | null) {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/content/guides/writers/${writerId}`, {
    method: 'DELETE',
    headers: jsonHeaders(accessToken),
  });
  if (!response.ok) throw new Error('Could not revoke writer');
}
