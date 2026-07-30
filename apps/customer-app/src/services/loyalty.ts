import { appConfig } from '@/utils/app-config';

export interface LoyaltyProgressDto {
  providerId: string;
  starBalance: number;
  targetStars: number;
  cycleCount: number;
  totalStarsEarned: number;
  welcomeStarClaimed: boolean;
  rewardAmount: number;
  isProgramActive: boolean;
  minOrderValue: number;
}

export interface LoyaltyRewardDto {
  rewardId: string;
  providerId: string;
  rewardAmount: number;
  code: string;
  status: 'ISSUED' | 'RESERVED' | 'REDEEMED' | 'REVOKED' | 'EXPIRED';
  expiresAt: string;
}

export interface LoyaltyLedgerEntryDto {
  entryId: string;
  customerId: string;
  providerId: string;
  deltaStars: number;
  entryType: 'WELCOME_STAR' | 'PURCHASE_STAR' | 'CYCLE_ROLLOVER' | 'STAR_REVERSAL' | 'ADMIN_ADJUSTMENT';
  referenceId?: string | null;
  note?: string | null;
  createdAt: string;
}

function authHeaders(accessToken: string): Record<string, string> {
  return {
    Accept: 'application/json',
    Authorization: `Bearer ${accessToken}`,
    'Content-Type': 'application/json',
  };
}

export async function fetchLoyaltyProgress(
  providerId: string,
  accessToken: string
): Promise<LoyaltyProgressDto> {
  const response = await fetch(
    `${appConfig.apiBaseUrl}/api/v1/loyalty/progress?providerId=${providerId}`,
    { headers: authHeaders(accessToken) }
  );
  if (!response.ok) {
    throw new Error('Could not fetch loyalty progress');
  }
  return (await response.json()) as LoyaltyProgressDto;
}

export async function claimWelcomeStar(
  providerId: string,
  accessToken: string
): Promise<LoyaltyProgressDto> {
  const response = await fetch(
    `${appConfig.apiBaseUrl}/api/v1/loyalty/welcome-star/claim?providerId=${providerId}`,
    {
      method: 'POST',
      headers: authHeaders(accessToken),
    }
  );
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.message || body?.error || 'Could not claim welcome star');
  }
  return (await response.json()) as LoyaltyProgressDto;
}

export async function fetchCustomerWallet(
  accessToken: string
): Promise<LoyaltyRewardDto[]> {
  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/loyalty/wallet`, {
    headers: authHeaders(accessToken),
  });
  if (!response.ok) {
    throw new Error('Could not fetch loyalty wallet');
  }
  return (await response.json()) as LoyaltyRewardDto[];
}

export async function fetchLoyaltyLedger(
  accessToken: string,
  providerId?: string
): Promise<LoyaltyLedgerEntryDto[]> {
  const url = providerId
    ? `${appConfig.apiBaseUrl}/api/v1/loyalty/ledger?providerId=${providerId}`
    : `${appConfig.apiBaseUrl}/api/v1/loyalty/ledger`;
  const response = await fetch(url, { headers: authHeaders(accessToken) });
  if (!response.ok) {
    throw new Error('Could not fetch loyalty ledger history');
  }
  return (await response.json()) as LoyaltyLedgerEntryDto[];
}
