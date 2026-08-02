import * as WebBrowser from 'expo-web-browser';

import type { CustomerPaymentStatus } from '../contracts/customer-payment';
import { appConfig } from '../utils/app-config';
import { apiClient } from './api-client';

export interface RazorpayOrderInitialization {
  keyId: string;
  orderId: string;
  amount: number;
  currency: string;
  transactionId: string;
}

export interface HostedCheckoutSession {
  checkoutPath: string;
  expiresAt: string;
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

export async function createHostedCheckoutSession(transactionId: string): Promise<HostedCheckoutSession> {
  return apiClient.post<HostedCheckoutSession>('/api/v1/payments/checkout-sessions', { transactionId });
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

export async function openRazorpayOrder(initialization: RazorpayOrderInitialization): Promise<void> {
  const session = await createHostedCheckoutSession(initialization.transactionId);
  const baseUrl = (appConfig.apiBaseUrl || 'http://localhost:8080').replace(/\/+$/, '');
  const checkoutUrl = session.checkoutPath.startsWith('http')
    ? session.checkoutPath
    : `${baseUrl}/${session.checkoutPath.replace(/^\/+/, '')}`;
  const redirectUrl = 'customerapp://payments/result';
  await WebBrowser.openAuthSessionAsync(checkoutUrl, redirectUrl, {
    showInRecents: true,
    preferEphemeralSession: true,
  });
}

export async function reconcilePaidOrder(orderId: string): Promise<CustomerPaymentStatusView> {
  const payment = await fetchOrderPaymentStatus(orderId);
  if (payment.status === 'SUCCESS') {
    await confirmPaidOrder(orderId, payment.transactionId);
  }
  return payment;
}

export async function waitForPaymentOutcome(
  orderId: string,
  attempts = 15,
  delayMs = 2_000,
): Promise<CustomerPaymentStatusView> {
  let latest = await fetchOrderPaymentStatus(orderId);
  for (let attempt = 1; attempt < attempts && latest.status === 'PENDING'; attempt += 1) {
    await new Promise((resolve) => setTimeout(resolve, delayMs));
    latest = await fetchOrderPaymentStatus(orderId);
  }
  if (latest.status === 'SUCCESS') {
    await confirmPaidOrder(orderId, latest.transactionId);
  }
  return latest;
}
