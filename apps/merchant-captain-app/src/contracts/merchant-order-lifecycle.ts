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
