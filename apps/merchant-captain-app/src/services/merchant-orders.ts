import { apiClient } from './api-client';

export type MerchantOrderStatus =
  | 'PLACED'
  | 'ACCEPTED'
  | 'PREPARING'
  | 'READY_FOR_PICKUP'
  | 'ASSIGNED'
  | 'REASSIGNED'
  | 'PICKED_UP'
  | 'DELIVERED'
  | 'COMPLETED'
  | 'REJECTED'
  | 'CANCELLED';

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
  paymentStatus: string;
  placedAt: string;
  acceptedAt?: string | null;
  readyAt?: string | null;
  picked_upAt?: string | null;
  deliveredAt?: string | null;
  cancelledAt?: string | null;
  cancellationReason?: string | null;
}

export type MerchantOrderAction = 'ACCEPTED' | 'PREPARING' | 'READY_FOR_PICKUP' | 'REJECTED' | 'CANCELLED';

export interface MerchantOrderActionDefinition {
  status: MerchantOrderAction;
  label: string;
  destructive?: boolean;
}

export function merchantOrderActions(status: MerchantOrderStatus): MerchantOrderActionDefinition[] {
  switch (status) {
    case 'PLACED':
      return [
        { status: 'ACCEPTED', label: 'Accept order' },
        { status: 'REJECTED', label: 'Reject order', destructive: true },
      ];
    case 'ACCEPTED':
      return [
        { status: 'PREPARING', label: 'Start packing' },
        { status: 'CANCELLED', label: 'Cancel order', destructive: true },
      ];
    case 'PREPARING':
      return [{ status: 'READY_FOR_PICKUP', label: 'Mark ready for pickup' }];
    default:
      return [];
  }
}

export function isMerchantOrderActive(status: MerchantOrderStatus): boolean {
  return !['DELIVERED', 'COMPLETED', 'REJECTED', 'CANCELLED'].includes(status);
}

export async function fetchMerchantOrders(providerId: string): Promise<MerchantOrder[]> {
  const orders = await apiClient.get<MerchantOrder[]>(
    `/api/v1/orders/provider/${encodeURIComponent(providerId)}`,
  );
  return [...orders].sort(
    (left, right) => new Date(right.placedAt).getTime() - new Date(left.placedAt).getTime(),
  );
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
