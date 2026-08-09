import {
  type MerchantOrderAction,
  type MerchantOrderStatus,
} from '../contracts/merchant-order-lifecycle';
import { apiClient } from './api-client';

export {
  isMerchantOrderActive,
  merchantOrderActions,
  type MerchantOrderAction,
  type MerchantOrderActionDefinition,
  type MerchantOrderStatus,
} from '../contracts/merchant-order-lifecycle';

export interface MerchantOrderItem {
  offeringId: string;
  name: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface MerchantOrder {
  orderId: string;
  customerId: string;
  providerId: string;
  captainId?: string | null;
  deliveryAddressId: string;
  deliveryContactPhone?: string | null;
  deliveryContactVerified?: boolean;
  status: MerchantOrderStatus;
  subtotalAmount: number;
  deliveryFee: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  couponCode?: string | null;
  paymentId?: string | null;
  paymentMethod: string;
  paymentStatus: string;
  placedAt: string;
  acceptedAt?: string | null;
  readyAt?: string | null;
  pickedUpAt?: string | null;
  deliveredAt?: string | null;
  cancelledAt?: string | null;
  cancellationReason?: string | null;
  items: MerchantOrderItem[];
}

export interface MerchantOrderPage {
  providerId: string;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  content: MerchantOrder[];
}

export async function fetchMerchantOrdersPage(
  providerId: string,
  page = 0,
  size = 50,
): Promise<MerchantOrderPage> {
  const safePage = Math.max(0, Math.trunc(page));
  const safeSize = Math.min(100, Math.max(1, Math.trunc(size)));
  return apiClient.get<MerchantOrderPage>(
    `/api/v1/orders/provider/${encodeURIComponent(providerId)}?page=${safePage}&size=${safeSize}`,
  );
}

/** Compatibility helper; Merchant screens should prefer fetchMerchantOrdersPage. */
export async function fetchMerchantOrders(providerId: string): Promise<MerchantOrder[]> {
  const result = await fetchMerchantOrdersPage(providerId, 0, 100);
  return result.content;
}

export async function transitionMerchantOrder(
  orderId: string,
  status: MerchantOrderAction,
  note?: string,
): Promise<void> {
  const query = new URLSearchParams({ status });
  if (note?.trim()) query.set('note', note.trim());
  await apiClient.put<unknown>(
    `/api/v1/orders/${encodeURIComponent(orderId)}/status?${query.toString()}`,
  );
}
