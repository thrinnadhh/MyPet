export type MerchantProviderType = 'PET_STORE' | 'VET_HOSPITAL' | 'GROOMING_CENTER';
export type MerchantFulfillmentType = 'DELIVERY' | 'APPOINTMENT' | 'BOTH';

export interface MerchantOnboardingPayload {
  ownerUserId: string;
  providerType: MerchantProviderType;
  fulfillmentType: MerchantFulfillmentType;
  name: string;
  description: string | null;
  licenseNumber: string | null;
  licenseDocUrl: string;
  addressLine: string;
  city: string;
  pincode: string;
  longitude: number;
  latitude: number;
}

function authenticatedJsonHeaders(accessToken?: string | null): Record<string, string> {
  if (!accessToken) throw new Error('Authentication is required to submit merchant onboarding.');
  return {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    Authorization: `Bearer ${accessToken}`,
  };
}

async function apiError(response: Response, fallback: string): Promise<Error> {
  const body = (await response.json().catch(() => null)) as { error?: string } | null;
  return new Error(body?.error || fallback);
}

export async function submitMerchantOnboarding(
  payload: MerchantOnboardingPayload,
  accessToken?: string | null,
  apiBaseUrl = process.env.EXPO_PUBLIC_API_BASE_URL?.trim().replace(/\/+$/, '') || 'http://localhost:8080',
): Promise<string> {
  const headers = authenticatedJsonHeaders(accessToken);
  const createResponse = await fetch(`${apiBaseUrl}/api/v1/providers`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
  });
  if (!createResponse.ok) throw await apiError(createResponse, 'Failed to create provider application.');

  const created = (await createResponse.json()) as { providerId?: string };
  if (!created.providerId) throw new Error('Provider creation returned no provider identifier.');

  const submitResponse = await fetch(
    `${apiBaseUrl}/api/v1/providers/${encodeURIComponent(created.providerId)}/submit`,
    { method: 'POST', headers },
  );
  if (!submitResponse.ok) throw await apiError(submitResponse, 'Failed to submit provider application.');
  return created.providerId;
}
