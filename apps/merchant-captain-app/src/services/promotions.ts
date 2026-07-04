import { appConfig } from '@/utils/app-config';

export interface Promotion {
  promotionId?: string;
  providerId: string | null;
  code: string;
  discountType: string;
  discountValue: number;
  maxDiscountAmount: number | null;
  minOrderValue: number | null;
  applicableCategory: string | null;
  validFrom: string;
  validUntil: string;
  isActive: boolean;
}

function jsonHeaders(accessToken?: string | null): Record<string, string> {
  const headers: Record<string, string> = { Accept: 'application/json', 'Content-Type': 'application/json' };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  return headers;
}

export async function fetchPromotions(providerId?: string | null, accessToken?: string | null): Promise<Promotion[]> {
  const query = providerId ? `?providerId=${providerId}` : '';
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/payments/promotions${query}`, {
    headers: jsonHeaders(accessToken),
  });
  if (!response.ok) throw new Error('Could not load promotions');
  return response.json();
}

export async function createPromotion(promo: Promotion, accessToken?: string | null): Promise<Promotion> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/payments/promotions`, {
    method: 'POST',
    headers: jsonHeaders(accessToken),
    body: JSON.stringify(promo),
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { error?: string } | null;
    throw new Error(body?.error ?? 'Could not create promotion');
  }
  return response.json();
}

export function formatPromotionLabel(promo: Promotion): string {
  if (promo.discountType === 'PERCENTAGE') return `${promo.discountValue}% off`;
  return `₹${promo.discountValue} off`;
}

export function formatPromotionScope(promo: Promotion): string {
  if (!promo.providerId) return 'Platform';
  if (promo.applicableCategory) return promo.applicableCategory;
  return 'Merchant';
}
