import { apiClient } from './api-client';

export type MerchantProviderStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'INFO_REQUESTED'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'REJECTED';

export type BusinessDay =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

export interface MerchantStoreProfile {
  providerId: string;
  ownerUserId: string;
  providerType: 'PET_STORE' | 'VET_HOSPITAL' | 'GROOMING_CENTER';
  fulfillmentType: 'DELIVERY' | 'APPOINTMENT';
  name: string;
  description?: string | null;
  addressLine: string;
  city: string;
  pincode: string;
  longitude: number;
  latitude: number;
  contactPhone?: string | null;
  contactEmail?: string | null;
  opensAt?: string | null;
  closesAt?: string | null;
  weeklyOffDays: BusinessDay[];
  status: MerchantProviderStatus;
  ratingAvg: number;
  ratingCount: number;
  commissionPct: number;
}

export interface MerchantStoreProfileUpdate {
  name: string;
  description?: string | null;
  addressLine: string;
  city: string;
  pincode: string;
  longitude: number;
  latitude: number;
  contactPhone?: string | null;
  contactEmail?: string | null;
  opensAt?: string | null;
  closesAt?: string | null;
  weeklyOffDays: BusinessDay[];
}

export async function fetchMerchantStores(): Promise<MerchantStoreProfile[]> {
  return apiClient.get<MerchantStoreProfile[]>('/api/v1/providers/merchant-profiles');
}

export async function updateMerchantStore(
  providerId: string,
  update: MerchantStoreProfileUpdate,
): Promise<MerchantStoreProfile> {
  return apiClient.patch<MerchantStoreProfile>(
    `/api/v1/providers/${encodeURIComponent(providerId)}/profile`,
    update,
  );
}
