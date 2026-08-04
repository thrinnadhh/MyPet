import { normalizeBarcode } from '@/utils/barcode';

import { apiClient } from './api-client';

export type OfferingStatus = 'ACTIVE' | 'INACTIVE' | 'OUT_OF_STOCK';
export type FulfillmentType = 'DELIVERY' | 'APPOINTMENT';

export interface MerchantProvider {
  providerId: string;
  name: string;
  providerType: 'PET_STORE' | 'VET_HOSPITAL' | 'GROOMING_CENTER' | string;
  fulfillmentType: FulfillmentType;
  status?: string;
}

export interface MerchantOffering {
  offeringId?: string;
  providerId: string;
  name: string;
  description?: string | null;
  category?: string | null;
  price: number;
  imageUrl?: string | null;
  status: OfferingStatus;
  stockQuantity?: number | null;
  sku?: string | null;
  durationMinutes?: number | null;
  barcode?: string | null;
}

export interface OfferingDraft {
  name: string;
  description?: string;
  category?: string;
  price: number;
  status: OfferingStatus;
  stockQuantity?: number;
  sku?: string;
  durationMinutes?: number;
  barcode?: string;
  imageUrl?: string;
}

export async function fetchMerchantProviders(): Promise<MerchantProvider[]> {
  return apiClient.get<MerchantProvider[]>('/api/v1/providers/me');
}

export async function fetchMerchantOfferings(providerId: string): Promise<MerchantOffering[]> {
  const offerings = await apiClient.get<MerchantOffering[]>(
    `/api/v1/catalog/offerings?providerId=${encodeURIComponent(providerId)}`,
  );
  return [...offerings].sort((left, right) => left.name.localeCompare(right.name));
}

function payload(providerId: string, draft: OfferingDraft): MerchantOffering {
  const barcode = normalizeBarcode(draft.barcode ?? '');
  return {
    providerId,
    name: draft.name.trim(),
    description: draft.description?.trim() || null,
    category: draft.category?.trim() || null,
    price: draft.price,
    imageUrl: draft.imageUrl?.trim() || null,
    status: draft.status,
    stockQuantity: draft.stockQuantity,
    sku: draft.sku?.trim() || null,
    durationMinutes: draft.durationMinutes,
    barcode: barcode || null,
  };
}

export async function createMerchantOffering(
  providerId: string,
  draft: OfferingDraft,
): Promise<MerchantOffering> {
  return apiClient.post<MerchantOffering>('/api/v1/catalog/offerings', payload(providerId, draft));
}

export async function updateMerchantOffering(
  offering: MerchantOffering,
  draft: OfferingDraft,
): Promise<MerchantOffering> {
  if (!offering.offeringId) throw new Error('Offering ID is required.');
  return apiClient.put<MerchantOffering>(
    `/api/v1/catalog/offerings/${encodeURIComponent(offering.offeringId)}`,
    payload(offering.providerId, draft),
  );
}

export async function deleteMerchantOffering(offeringId: string): Promise<void> {
  await apiClient.delete(`/api/v1/catalog/offerings/${encodeURIComponent(offeringId)}`);
}
