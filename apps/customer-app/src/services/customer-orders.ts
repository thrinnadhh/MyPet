import AsyncStorage from '@react-native-async-storage/async-storage';
import { appConfig } from '@/utils/app-config';
import type { OrderFlowStepId } from '@/constants/content';

export type OrderTabCategory = 'active' | 'past' | 'subscription';

export interface CustomerOrderRecord {
  id: string;
  providerId: string;
  providerName: string;
  items: string[];
  total: string;
  rawTotal: number;
  status: string;
  orderedAt: string;
  hasReview: boolean;
  flowStep: OrderFlowStepId;
  isSubscription?: boolean;
  deliveryAddressId?: string;
  captainId?: string;
  statusHistory?: Array<{
    fromStatus: string | null;
    toStatus: string;
    changedAt: string;
    note: string | null;
  }>;
}

export interface ReorderItemValidation {
  offeringId: string;
  offeringName: string;
  unitPrice: number;
  quantity: number;
  isAvailable: boolean;
  message?: string | null;
}

export interface ReorderValidationResult {
  originalOrderId: string;
  providerId: string;
  isProviderServiceable: boolean;
  items: ReorderItemValidation[];
  canReorder: boolean;
}

interface OrderTrackingDto {
  orderId: string;
  providerId: string;
  status: string;
  flowStep: OrderFlowStepId;
  totalAmount: number | string;
  placedAt: string;
  items: string[];
  statusHistory?: Array<{
    fromStatus: string | null;
    toStatus: string;
    changedAt: string;
    note: string | null;
  }>;
}

const CACHE_PREFIX = '@mypet_orders_cache_v1_';

function headers(accessToken?: string | null): Record<string, string> {
  const result: Record<string, string> = { Accept: 'application/json' };
  if (accessToken) result.Authorization = `Bearer ${accessToken}`;
  return result;
}

async function providerName(providerId: string, accessToken?: string | null): Promise<string> {
  try {
    const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/providers/${providerId}`, { headers: headers(accessToken) });
    if (!response.ok) return `Store ${providerId.slice(0, 8)}`;
    const body = (await response.json()) as { name: string };
    return body.name || `Store ${providerId.slice(0, 8)}`;
  } catch {
    return `Store ${providerId.slice(0, 8)}`;
  }
}

export async function fetchCustomerOrders(
  customerId: string,
  accessToken?: string | null,
): Promise<CustomerOrderRecord[]> {
  const cacheKey = `${CACHE_PREFIX}${customerId}`;

  try {
    const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/orders/customer/${customerId}/tracking`, {
      headers: headers(accessToken),
    });
    if (!response.ok) throw new Error('Could not load order history');

    const rawOrders = (await response.json()) as OrderTrackingDto[];
    const orders: CustomerOrderRecord[] = await Promise.all(
      rawOrders.map(async (order) => {
        const rawTotal = Number(order.totalAmount) || 0;
        return {
          id: order.orderId,
          providerId: order.providerId,
          providerName: await providerName(order.providerId, accessToken),
          items: order.items || [],
          total: `₹${rawTotal.toFixed(0)}`,
          rawTotal,
          status: order.status,
          orderedAt: order.placedAt,
          hasReview: false,
          flowStep: order.flowStep || 'placed',
          statusHistory: order.statusHistory || [],
        };
      }),
    );

    await AsyncStorage.setItem(cacheKey, JSON.stringify(orders)).catch(() => null);
    return orders;
  } catch (error) {
    const cached = await AsyncStorage.getItem(cacheKey).catch(() => null);
    if (cached) {
      try {
        return JSON.parse(cached) as CustomerOrderRecord[];
      } catch {
        // Fall through to error throw
      }
    }
    throw error;
  }
}

export async function fetchOrderDetails(
  orderId: string,
  accessToken?: string | null,
): Promise<CustomerOrderRecord> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/orders/${orderId}`, {
    headers: headers(accessToken),
  });
  if (!response.ok) throw new Error('Could not load order details');

  const order = (await response.json()) as any;
  const rawTotal = Number(order.totalAmount) || 0;

  return {
    id: order.orderId || order.id,
    providerId: order.providerId,
    providerName: await providerName(order.providerId, accessToken),
    items: order.items?.map((i: any) => i.offeringNameSnapshot || i.name) || ['Pet Item'],
    total: `₹${rawTotal.toFixed(0)}`,
    rawTotal,
    status: order.status,
    orderedAt: order.placedAt || order.createdAt || new Date().toISOString(),
    hasReview: false,
    flowStep: order.flowStep || 'placed',
    deliveryAddressId: order.deliveryAddressId,
    captainId: order.captainId,
  };
}

export async function cancelOrder(
  orderId: string,
  reason: string,
  accessToken?: string | null,
): Promise<void> {
  const url = `${appConfig.apiBaseUrl}/api/v1/orders/${orderId}/cancel?reason=${encodeURIComponent(reason)}`;
  const response = await fetch(url, {
    method: 'POST',
    headers: headers(accessToken),
  });

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { message?: string; error?: string } | null;
    throw new Error(body?.message || body?.error || 'Could not cancel order');
  }
}

export async function reorderItems(
  orderId: string,
  accessToken?: string | null,
): Promise<ReorderValidationResult> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/orders/${orderId}/reorder`, {
    method: 'POST',
    headers: headers(accessToken),
  });

  if (!response.ok) {
    throw new Error('Reorder revalidation failed');
  }

  return (await response.json()) as ReorderValidationResult;
}

export interface CheckoutQuoteInput {
  customerId: string;
  providerId: string;
  deliveryAddressId: string;
  items: Array<{ offeringId: string; quantity: number }>;
  couponCode?: string | null;
  paymentMethod?: 'CARD' | 'UPI' | 'COD' | string | null;
  city?: string | null;
}

export interface CheckoutQuoteOutput {
  quoteToken: string;
  subtotal: number;
  itemDiscount: number;
  couponDiscount: number;
  loyaltyDiscount: number;
  deliveryFee: number;
  tax: number;
  roundOff: number;
  payableTotal: number;
  couponCode?: string | null;
  paymentMethod?: string | null;
  isCodAvailable: boolean;
  codRejectionReason?: string | null;
  expiresAt: string;
}

export interface CreateOrderInput {
  customerId: string;
  providerId: string;
  deliveryAddressId: string;
  items: Array<{ offeringId: string; quantity: number }>;
  couponCode?: string | null;
  paymentMethod?: 'CARD' | 'UPI' | 'COD' | string | null;
  quoteToken?: string | null;
  city?: string | null;
}

export async function fetchCheckoutQuote(
  input: CheckoutQuoteInput,
  accessToken?: string | null,
): Promise<CheckoutQuoteOutput> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/checkout/quote`, {
    method: 'POST',
    headers: { ...headers(accessToken), 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null);
    throw new Error(errorBody?.message || errorBody?.error || 'Could not calculate checkout quote');
  }

  return (await response.json()) as CheckoutQuoteOutput;
}

export async function createCustomerOrder(
  input: CreateOrderInput,
  accessToken?: string | null,
): Promise<CustomerOrderRecord> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/orders`, {
    method: 'POST',
    headers: { ...headers(accessToken), 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null);
    throw new Error(errorBody?.message || errorBody?.error || 'Could not place order');
  }

  const order = await response.json();
  const rawTotal = Number(order.totalAmount) || 0;

  return {
    id: order.orderId || order.id,
    providerId: order.providerId,
    providerName: `Store ${order.providerId.slice(0, 8)}`,
    items: order.items?.map((i: any) => i.offeringNameSnapshot || i.name) || ['Pet Product'],
    total: `₹${rawTotal.toFixed(0)}`,
    rawTotal,
    status: order.status,
    orderedAt: order.placedAt || new Date().toISOString(),
    hasReview: false,
    flowStep: 'placed',
  };
}

