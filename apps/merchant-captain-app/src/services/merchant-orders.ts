import {
  type MerchantOrderAction,
  type MerchantOrderStatus,
  type MerchantPaymentStatus,
} from '../contracts/merchant-order-lifecycle';
import { apiClient } from './api-client';

export {
  isMerchantOrderActive,
  isMerchantOrderInQueue,
  merchantOrderActions,
  type MerchantOrderAction,
  type MerchantOrderActionDefinition,
  type MerchantOrderQueue,
  type MerchantOrderStatus,
  type MerchantPaymentStatus,
} from '../contracts/merchant-order-lifecycle';

export interface MerchantOrder {
  orderId: string;
  customerId: string;
  providerId: string;
  captainId?: string | null;
  deliveryAddressId: string;
  status: MerchantOrderStatus;
  subtotalAmount: number;
  deliveryFee: number;
  discountAmount: number;
  taxAmount: number;
  totalAmount: number;
  paymentId?: string | null;
  paymentMethod: string;
  paymentStatus: MerchantPaymentStatus;
  placedAt: string;
  acceptedAt?: string | null;
  readyAt?: string | null;
  picked_upAt?: string | null;
  deliveredAt?: string | null;
  cancelledAt?: string | null;
  cancellationReason?: string | null;
}

export async function fetchMerchantOrders(providerId: string): Promise<MerchantOrder[]> {
  const orders = await apiClient.get<MerchantOrder[]>(
    `/api/v1/orders/provider/${encodeURIComponent(providerId)}`,
  );
  return [...orders].sort(
    (left, right) => new Date(right.placedAt).getTime() - new Date(left.placedAt).getTime(),
  );
}

export async function fetchMerchantOrder(orderId: string): Promise<MerchantOrder> {
  return apiClient.get<MerchantOrder>(`/api/v1/orders/${encodeURIComponent(orderId)}`);
}

export async function transitionMerchantOrder(
  orderId: string,
  status: MerchantOrderAction,
  note?: string,
): Promise<MerchantOrder> {
  const query = new URLSearchParams({ status });
  if (note?.trim()) query.set('note', note.trim());
  return apiClient.put<MerchantOrder>(
    `/api/v1/orders/${encodeURIComponent(orderId)}/status?${query.toString()}`,
  );
}
