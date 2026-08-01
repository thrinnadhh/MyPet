import { Platform } from 'react-native';

import type { CustomerPaymentStatus } from '../contracts/customer-payment';
import { apiClient } from './api-client';

export interface RazorpayOrderInitialization {
  keyId: string;
  orderId: string;
  amount: number;
  currency: string;
  transactionId: string;
}

export interface CustomerPaymentStatusView {
  transactionId: string;
  referenceId: string;
  transactionType: string;
  amount: number;
  currency: string;
  status: CustomerPaymentStatus;
  createdAt: string;
  updatedAt: string;
}

export interface RazorpayCheckoutResult {
  razorpay_payment_id: string;
  razorpay_order_id: string;
  razorpay_signature: string;
}

export async function initiateOrderPayment(
  userId: string,
  orderId: string,
  amount: number,
): Promise<RazorpayOrderInitialization> {
  return apiClient.post<RazorpayOrderInitialization>('/api/v1/payments/orders', {
    userId,
    referenceId: orderId,
    amount,
    transactionType: 'ORDER_PAYMENT',
  });
}

export async function fetchOrderPaymentStatus(orderId: string): Promise<CustomerPaymentStatusView> {
  return apiClient.get<CustomerPaymentStatusView>(
    `/api/v1/payments/transactions/reference/${encodeURIComponent(orderId)}`,
  );
}

export async function confirmPaidOrder(orderId: string, transactionId: string): Promise<void> {
  await apiClient.post(
    `/api/v1/orders/${encodeURIComponent(orderId)}/confirm?paymentId=${encodeURIComponent(transactionId)}`,
  );
}

export async function openRazorpayOrder(
  initialization: RazorpayOrderInitialization,
  customer: { name?: string; email?: string; contact?: string },
): Promise<RazorpayCheckoutResult> {
  if (Platform.OS === 'web') {
    throw new Error('Online payment requires the Android or iOS MyPet app.');
  }
  const module = await import('react-native-razorpay');
  const RazorpayCheckout = module.default;
  return RazorpayCheckout.open({
    key: initialization.keyId,
    order_id: initialization.orderId,
    amount: Math.round(initialization.amount * 100),
    currency: initialization.currency,
    name: 'MyPet',
    description: 'Pet care marketplace order',
    prefill: {
      name: customer.name,
      email: customer.email,
      contact: customer.contact,
    },
    theme: { color: '#1565D8' },
  }) as Promise<RazorpayCheckoutResult>;
}

export async function reconcilePaidOrder(
  orderId: string,
): Promise<CustomerPaymentStatusView> {
  const payment = await fetchOrderPaymentStatus(orderId);
  if (payment.status === 'SUCCESS') {
    await confirmPaidOrder(orderId, payment.transactionId);
  }
  return payment;
}
