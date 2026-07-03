import { appConfig } from '@/utils/app-config';

export interface CaptainOnboardPayload {
  vehicleType: 'BIKE' | 'SCOOTER' | 'BICYCLE' | 'ON_FOOT';
  vehicleNumber: string;
  bankAccount: string;
  bankIfsc: string;
  licenseDocUrl?: string;
  selfieDocUrl?: string;
  documents: Array<{ docType: string; docUrl: string }>;
}

function jsonHeaders(accessToken?: string | null): Record<string, string> {
  const headers: Record<string, string> = { Accept: 'application/json', 'Content-Type': 'application/json' };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  return headers;
}

export async function submitCaptainOnboarding(
  payload: CaptainOnboardPayload,
  accessToken?: string | null,
): Promise<void> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/captains/profiles`, {
    method: 'POST',
    headers: jsonHeaders(accessToken),
    body: JSON.stringify({
      vehicleType: payload.vehicleType,
      vehicleNumber: payload.vehicleNumber,
      bankAccount: payload.bankAccount,
      bankIfsc: payload.bankIfsc,
      licenseDocUrl: payload.licenseDocUrl,
      selfieDocUrl: payload.selfieDocUrl,
      documents: payload.documents,
    }),
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { error?: string } | null;
    throw new Error(body?.error ?? 'Captain onboarding failed');
  }
}

export async function fetchPendingCaptains(accessToken?: string | null) {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/captains/pending`, {
    headers: jsonHeaders(accessToken),
  });
  if (!response.ok) throw new Error('Could not load pending captains');
  return response.json();
}

export async function approveCaptain(captainId: string, accessToken?: string | null) {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/captains/${captainId}/approve`, {
    method: 'POST',
    headers: jsonHeaders(accessToken),
  });
  if (!response.ok) throw new Error('Could not approve captain');
  return response.json();
}
