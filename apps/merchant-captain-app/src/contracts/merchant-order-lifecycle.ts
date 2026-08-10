import {
  MERCHANT_ORDER_QUEUES,
  type MerchantOrderQueue,
  type OrderStatus,
  type PaymentStatus,
} from './order-contract.generated';

export type MerchantOrderStatus = OrderStatus;
export type MerchantPaymentStatus = PaymentStatus;
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
        { status: 'PREPARING', label: 'Start preparing' },
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

export function isMerchantOrderInQueue(
  status: MerchantOrderStatus,
  paymentStatus: MerchantPaymentStatus,
  queue: MerchantOrderQueue,
): boolean {
  const config = MERCHANT_ORDER_QUEUES[queue];
  if (!(config.statuses as readonly MerchantOrderStatus[]).includes(status)) return false;
  if ('paymentStatuses' in config) {
    return (config.paymentStatuses as readonly MerchantPaymentStatus[]).includes(paymentStatus);
  }
  return true;
}

export type { MerchantOrderQueue } from './order-contract.generated';
