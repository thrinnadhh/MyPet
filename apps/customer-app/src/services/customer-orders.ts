import { appConfig } from '@/utils/app-config';
import type { OrderFlowStepId } from '@/constants/content';

export interface CustomerOrderRecord {
  id: string;
  providerId: string;
  providerName: string;
  items: string[];
  total: string;
  orderedAt: string;
  hasReview: boolean;
  flowStep: OrderFlowStepId;
}

interface OrderTrackingDto {
  orderId: string;
  providerId: string;
  status: string;
  flowStep: OrderFlowStepId;
  totalAmount: number | string;
  placedAt: string;
  items: string[];
}

function headers(accessToken?: string | null): Record<string, string> {
  const result: Record<string, string> = { Accept: 'application/json' };
  if (accessToken) result.Authorization = `Bearer ${accessToken}`;
  return result;
}

async function providerName(providerId: string, accessToken?: string | null): Promise<string> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/providers/${providerId}`, { headers: headers(accessToken) });
  if (!response.ok) return `Store ${providerId.slice(0, 8)}`;
  const body = (await response.json()) as { name: string };
  return body.name;
}

export async function fetchCustomerOrders(
  customerId: string,
  accessToken?: string | null,
): Promise<CustomerOrderRecord[]> {
  if (appConfig.allowDemoMode) return [];

  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/orders/customer/${customerId}/tracking`, {
    headers: headers(accessToken),
  });
  if (!response.ok) throw new Error('Could not load order history');

  const orders = (await response.json()) as OrderTrackingDto[];
  return Promise.all(
    orders.map(async (order) => ({
      id: order.orderId,
      providerId: order.providerId,
      providerName: await providerName(order.providerId, accessToken),
      items: order.items,
      total: `₹${Number(order.totalAmount).toFixed(0)}`,
      orderedAt: order.placedAt,
      hasReview: false,
      flowStep: order.flowStep,
    })),
  );
}
