export const RECURRING_CADENCES = [7, 15, 25, 30, 35] as const;
export type RecurringCadence = (typeof RECURRING_CADENCES)[number];
export type RecurringOrderStatus = 'ACTIVE' | 'PAUSED' | 'AWAITING_CONFIRMATION' | 'CANCELLED';
export type RecurringOccurrenceStatus = 'PROCESSING' | 'ORDER_CREATED' | 'FAILED';

export interface RecurringOrderItem {
  offeringId: string;
  name: string;
  baseQuantity: number;
  effectiveQuantity: number;
  unitPriceAtCreation: number;
}

export interface RecurringOrderSubscription {
  subscriptionId: string;
  customerId: string;
  providerId: string;
  sourceOrderId: string;
  deliveryAddressId: string;
  cadenceDays: RecurringCadence;
  quantityMultiplier: number;
  paymentMethod: 'COD' | 'CARD' | 'UPI' | string;
  status: RecurringOrderStatus;
  nextOrderAt: string;
  lastRemindedAt?: string | null;
  lastExecutedAt?: string | null;
  lastOrderId?: string | null;
  lastFailureCode?: string | null;
  lastFailureDetail?: string | null;
  items: RecurringOrderItem[];
  createdAt: string;
  updatedAt: string;
}

export interface RecurringOrderOccurrence {
  occurrenceId: string;
  subscriptionId: string;
  scheduledFor: string;
  orderId?: string | null;
  status: RecurringOccurrenceStatus;
  failureCode?: string | null;
  failureDetail?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ReorderValidationItem {
  offeringId: string;
  offeringName: string;
  unitPrice: number;
  quantity: number;
  isAvailable: boolean;
  message?: string | null;
}

export interface RecurringOrderConfirmation {
  subscription: RecurringOrderSubscription;
  reorder: {
    originalOrderId: string;
    providerId: string;
    isProviderServiceable: boolean;
    items: ReorderValidationItem[];
    canReorder: boolean;
  };
}

export function isRecurringCadence(value: number): value is RecurringCadence {
  return RECURRING_CADENCES.includes(value as RecurringCadence);
}