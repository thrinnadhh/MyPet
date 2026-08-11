import { apiClient } from './api-client';

export interface MerchantPayoutRecord {
  payoutId: string;
  payeeUserId: string;
  payeeRole: string;
  amount: number;
  status: string;
  periodStart: string;
  periodEnd: string;
  paidAt?: string | null;
  createdAt: string;
}

export interface MerchantFinanceSummary {
  providerId: string;
  commissionPercent: number;
  deliveredOrderCount: number;
  completedAppointmentCount: number;
  orderGrossRevenue: number;
  orderCommission: number;
  orderNetRevenue: number;
  appointmentRevenue: number;
  totalGrossRevenue: number;
  totalNetRevenue: number;
  accountPaidOut: number;
  accountPayoutInFlight: number;
  payoutScope: 'MERCHANT_ACCOUNT';
  payoutPage: number;
  payoutPageSize: number;
  payoutTotalRecords: number;
  payouts: MerchantPayoutRecord[];
}

export async function fetchMerchantFinance(
  providerId: string,
  payoutPage = 0,
  payoutSize = 50,
): Promise<MerchantFinanceSummary> {
  const safePage = Math.max(0, Math.trunc(payoutPage));
  const safeSize = Math.min(100, Math.max(1, Math.trunc(payoutSize)));
  return apiClient.get<MerchantFinanceSummary>(
    `/api/v1/payments/merchant-finance/providers/${encodeURIComponent(providerId)}` +
      `?payoutPage=${safePage}&payoutSize=${safeSize}`,
  );
}
