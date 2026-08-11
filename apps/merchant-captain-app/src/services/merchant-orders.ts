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

export interface MerchantOrderItem {
  orderItemId?: string;
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
  paymentStatus: MerchantPaymentStatus;
  placedAt: string;
  acceptedAt?: string | null;
  preparingAt?: string | null;
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

export interface MerchantDeliveryAddress {
  label?: string | null;
  line1: string;
  line2?: string | null;
  city: string;
  state: string;
  pincode: string;
  latitude: number;
  longitude: number;
}

export interface MerchantOrderHistoryEntry {
  fromStatus?: MerchantOrderStatus | null;
  toStatus: MerchantOrderStatus;
  changedAt: string;
  actorId?: string | null;
  note?: string | null;
}

export interface MerchantOrderDetail {
  orderId: string;
  customerId: string;
  customerName?: string | null;
  deliveryAddressId: string;
  deliveryAddress: MerchantDeliveryAddress;
  contactPhone?: string | null;
  contactVerified: boolean;
  items: MerchantOrderItem[];
  paymentMethod: string;
  paymentStatus: MerchantPaymentStatus;
  subtotal: number;
  discount: number;
  delivery: number;
  tax: number;
  total: number;
  placedAt: string;
  acceptedAt?: string | null;
  preparingAt?: string | null;
  readyAt?: string | null;
  status: MerchantOrderStatus;
  history: MerchantOrderHistoryEntry[];
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

export async function fetchMerchantOrder(orderId: string): Promise<MerchantOrder> {
  return apiClient.get<MerchantOrder>(`/api/v1/orders/${encodeURIComponent(orderId)}`);
}

export async function fetchMerchantOrderDetail(orderId: string): Promise<MerchantOrderDetail> {
  return apiClient.get<MerchantOrderDetail>(
    `/api/v1/orders/${encodeURIComponent(orderId)}/merchant-detail`,
  );
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
