import { fetchMerchantBookings } from './merchant-appointments';
import { fetchMerchantOfferings, fetchMerchantProviders } from './merchant-inventory';
import { fetchMerchantOrders, isMerchantOrderActive } from './merchant-orders';

export interface MerchantDashboardMetrics {
  providerStatus: string;
  activeOfferings: number;
  lowStockOfferings: number;
  openOrders: number;
  todayOrders: number;
  todayRevenue: number;
  todayBookings: number;
}

function sameLocalDay(iso: string | null | undefined, now: Date): boolean {
  if (!iso) return false;
  const value = new Date(iso);
  return value.getFullYear() === now.getFullYear()
    && value.getMonth() === now.getMonth()
    && value.getDate() === now.getDate();
}

export async function fetchMerchantDashboardMetrics(
  providerId: string,
  now = new Date(),
): Promise<MerchantDashboardMetrics> {
  const [providers, offerings, orders, bookings] = await Promise.all([
    fetchMerchantProviders(),
    fetchMerchantOfferings(providerId),
    fetchMerchantOrders(providerId),
    fetchMerchantBookings(),
  ]);

  const provider = providers.find((item) => item.providerId === providerId);
  const todayOrders = orders.filter((order) => sameLocalDay(order.placedAt, now));
  const todayRevenue = orders
    .filter((order) =>
      ['DELIVERED', 'COMPLETED'].includes(order.status) && sameLocalDay(order.deliveredAt, now),
    )
    .reduce((total, order) => total + Number(order.totalAmount || 0), 0);

  return {
    providerStatus: provider?.status ?? 'UNKNOWN',
    activeOfferings: offerings.filter((offering) => offering.status === 'ACTIVE').length,
    lowStockOfferings: offerings.filter((offering) =>
      offering.stockQuantity !== null
      && offering.stockQuantity !== undefined
      && offering.stockQuantity >= 0
      && offering.stockQuantity <= 5,
    ).length,
    openOrders: orders.filter((order) => isMerchantOrderActive(order.status)).length,
    todayOrders: todayOrders.length,
    todayRevenue,
    todayBookings: bookings.filter((booking) =>
      booking.providerId === providerId
      && sameLocalDay(booking.slotStartsAt, now)
      && !['COMPLETED', 'CANCELLED', 'NO_SHOW', 'EXPIRED'].includes(booking.status),
    ).length,
  };
}
