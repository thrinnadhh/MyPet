import { apiClient } from './api-client';

export interface MerchantLoyaltyProgram {
  programId?: string;
  providerId: string;
  targetStars: number;
  rewardAmount: number;
  minOrderValue: number;
  welcomeStarPolicy: boolean;
  isActive: boolean;
  isStackable: boolean;
  expiryDays: number;
}

export interface MerchantLoyaltyAudit {
  auditId?: string;
  actorId: string;
  providerId: string;
  action: string;
  beforeJson?: string | null;
  afterJson?: string | null;
  createdAt: string;
}

export function fetchMerchantLoyaltyProgram(providerId: string): Promise<MerchantLoyaltyProgram> {
  return apiClient.get(`/api/v1/loyalty/programs?providerId=${encodeURIComponent(providerId)}`);
}

export function saveMerchantLoyaltyProgram(program: MerchantLoyaltyProgram): Promise<MerchantLoyaltyProgram> {
  return apiClient.post('/api/v1/loyalty/programs', {
    ...program,
    targetStars: 10,
    isStackable: true,
  });
}

export function fetchMerchantLoyaltyAudit(providerId: string): Promise<MerchantLoyaltyAudit[]> {
  return apiClient.get(`/api/v1/loyalty/audit-logs?providerId=${encodeURIComponent(providerId)}`);
}
