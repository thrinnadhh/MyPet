import { apiErrorFromResponse } from '@/contracts/api-error';
import { appConfig } from '@/utils/app-config';

export interface CustomerPet {
  petId: string;
  name: string;
  species: string;
  breed?: string | null;
  dateOfBirth?: string | null;
}

export interface CreateCustomerPetInput {
  name: string;
  species: string;
  breed?: string | null;
  dateOfBirth?: string | null;
}

async function request<T>(
  path: string,
  accessToken: string,
  init: RequestInit = {},
): Promise<T> {
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
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export function fetchCustomerPets(accessToken: string): Promise<CustomerPet[]> {
  return request('/api/v1/pets', accessToken);
}

export function createCustomerPet(
  input: CreateCustomerPetInput,
  accessToken: string,
): Promise<CustomerPet> {
  return request('/api/v1/pets', accessToken, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}
