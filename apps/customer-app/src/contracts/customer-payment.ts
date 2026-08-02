export type CustomerPaymentMethod = 'COD' | 'CARD' | 'UPI';
export type CustomerPaymentStatus =
  | 'NOT_STARTED'
  | 'PENDING'
  | 'SUCCESS'
  | 'FAILED'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED';

export interface CustomerPaymentState {
  orderId: string;
  transactionId?: string;
  status: CustomerPaymentStatus;
}

export function shouldPollPayment(status: CustomerPaymentStatus): boolean {
  return status === 'PENDING';
}

export function paymentAllowsCartClear(status: CustomerPaymentStatus): boolean {
  return status === 'SUCCESS';
}

export function paymentNeedsRetry(status: CustomerPaymentStatus): boolean {
  return status === 'FAILED' || status === 'NOT_STARTED';
}

export function isTerminalOrderStatus(status: string): boolean {
  return ['DELIVERED', 'COMPLETED', 'CANCELLED', 'REJECTED'].includes(status.toUpperCase());
}

export function activeOrderPollInterval(status: string): number | null {
  return isTerminalOrderStatus(status) ? null : 8_000;
}
