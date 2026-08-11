import { apiClient } from './api-client';

export type MerchantSubscriptionStatus = 'ACTIVE' | 'PAUSED' | 'AWAITING_CONFIRMATION' | 'CANCELLED';

export interface MerchantSubscriptionItem {
  offeringId: string;
  name: string;
  baseQuantity: number;
  effectiveQuantity: number;
  unitPriceAtCreation: number;
}

export interface MerchantSubscriptionDemand {
  subscriptionId: string;
  customerId: string;
  providerId: string;
  sourceOrderId: string;
  deliveryAddressId: string;
  cadenceDays: 7 | 15 | 25 | 30 | 35;
  quantityMultiplier: number;
  paymentMethod: string;
  status: MerchantSubscriptionStatus;
  nextOrderAt: string;
  lastExecutedAt?: string | null;
  lastOrderId?: string | null;
  lastFailureCode?: string | null;
  lastFailureDetail?: string | null;
  items: MerchantSubscriptionItem[];
  createdAt: string;
  updatedAt: string;
}

export async function fetchMerchantSubscriptionDemand(
  providerId: string,
): Promise<MerchantSubscriptionDemand[]> {
  return apiClient.get<MerchantSubscriptionDemand[]>(
    `/api/v1/orders/subscriptions/provider/${encodeURIComponent(providerId)}`,
  );
}