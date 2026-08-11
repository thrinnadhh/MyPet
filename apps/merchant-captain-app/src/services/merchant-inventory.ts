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

export interface MerchantOfferingPage {
  providerId: string;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  content: MerchantOffering[];
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

export interface OfferingMediaResponse {
  offering: MerchantOffering;
  imageUrl: string;
}

export async function fetchMerchantProviders(): Promise<MerchantProvider[]> {
  return apiClient.get<MerchantProvider[]>('/api/v1/providers/me');
}

export async function fetchMerchantOfferingsPage(
  providerId: string,
  options: { query?: string; page?: number; size?: number } = {},
): Promise<MerchantOfferingPage> {
  const page = Math.max(0, Math.trunc(options.page ?? 0));
  const size = Math.min(100, Math.max(1, Math.trunc(options.size ?? 50)));
  const query = options.query?.trim().slice(0, 120) ?? '';
  return apiClient.get<MerchantOfferingPage>(
    `/api/v1/catalog/merchant/offerings?providerId=${encodeURIComponent(providerId)}` +
      `&query=${encodeURIComponent(query)}&page=${page}&size=${size}`,
  );
}

/**
 * Bounded compatibility helper for screens that do not yet expose page controls.
 * New merchant catalog surfaces should use fetchMerchantOfferingsPage directly.
 */
export async function fetchMerchantOfferings(providerId: string): Promise<MerchantOffering[]> {
  const result = await fetchMerchantOfferingsPage(providerId, { page: 0, size: 100 });
  return result.content;
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

export async function uploadMerchantOfferingImage(
  offeringId: string,
  localUri: string,
  filename: string,
  mimeType: 'image/jpeg' | 'image/png' | 'image/webp',
): Promise<MerchantOffering> {
  const form = new FormData();
  form.append('file', {
    uri: localUri,
    name: filename,
    type: mimeType,
  } as unknown as Blob);
  const response = await apiClient.post<OfferingMediaResponse>(
    `/api/v1/catalog/offerings/${encodeURIComponent(offeringId)}/media`,
    form,
  );
  return response.offering;
}

export async function deleteMerchantOffering(offeringId: string): Promise<void> {
  await apiClient.delete(`/api/v1/catalog/offerings/${encodeURIComponent(offeringId)}`);
}
