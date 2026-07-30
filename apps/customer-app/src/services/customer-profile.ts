import { appConfig } from '@/utils/app-config';

export interface CustomerAddress {
  addressId: string;
  label: string | null;
  line1: string;
  line2: string | null;
  city: string;
  state: string;
  pincode: string;
  geoLat: number;
  geoLng: number;
  isDefault: boolean;
}

export interface AddressInput {
  label?: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  pincode: string;
  geoLat: number;
  geoLng: number;
}

const authHeaders = (accessToken: string) => ({ Accept: 'application/json', Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' });

export async function fetchDefaultAddress(accessToken: string): Promise<CustomerAddress | null> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/addresses/default`, { headers: authHeaders(accessToken) });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`ADDRESS_FETCH_${response.status}`);
  const value = await response.json() as Omit<CustomerAddress, 'geoLat' | 'geoLng'> & { geoLat: number | string; geoLng: number | string };
  return { ...value, geoLat: Number(value.geoLat), geoLng: Number(value.geoLng) };
}

export async function createDefaultAddress(accessToken: string, input: AddressInput): Promise<CustomerAddress> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/addresses`, {
    method: 'POST',
    headers: authHeaders(accessToken),
    body: JSON.stringify({ ...input, isDefault: true }),
  });
  if (!response.ok) throw new Error(`ADDRESS_SAVE_${response.status}`);
  const value = await response.json() as Omit<CustomerAddress, 'geoLat' | 'geoLng'> & { geoLat: number | string; geoLng: number | string };
  return { ...value, geoLat: Number(value.geoLat), geoLng: Number(value.geoLng) };
}

export function isOfflineError(error: unknown): boolean {
  const message = error instanceof Error ? error.message.toLowerCase() : '';
  return message.includes('network') || message.includes('fetch') || message.includes('offline');
}
