export type AdminSection = 'overview' | 'approvals' | 'service-areas' | 'disputes' | 'audit';

export interface AdminOperationsSnapshot {
  /** Compatibility aggregate; canonical counters below remain authoritative. */
  activeOrders: number;
  delayedOrders: number;
  failedPayments: number;
  openDisputes: number;
  openSupportCases: number;
  ordersPlaced: number;
  merchantPending: number;
  accepted: number;
  preparing: number;
  readyForPickup: number;
  assigned: number;
  dispatchFailures: number;
  pickedUp: number;
  delivered: number;
  completed: number;
  cancelled: number;
  rejected: number;
  paymentFailures: number;
  refunds: number;
  refundPending: number;
  generatedAt: string;
}

export interface AdminServiceArea {
  pincode: string;
  city: string;
  enabled: boolean;
  deliveryEnabled: boolean;
  serviceRadiusKm: number;
  emergencyMessage?: string | null;
  updatedByUserId: string;
  updatedAt: string;
}

export interface AdminServiceAreaDraft {
  pincode: string;
  city: string;
  enabled: boolean;
  deliveryEnabled: boolean;
  serviceRadiusKm: number;
  emergencyMessage?: string | null;
  reason: string;
}

export interface AdminAuditLog {
  auditId: string;
  adminUserId: string;
  action: string;
  entityType: string;
  entityId?: string | null;
  previousValue?: string | null;
  newValue?: string | null;
  reason: string;
  traceId: string;
  createdAt: string;
}

export interface AdminProviderApproval {
  providerId: string;
  ownerUserId: string;
  providerType: string;
  fulfillmentType: string;
  name: string;
  city: string;
  pincode: string;
  status: string;
  commissionPct: number | string;
  licenseNumber?: string | null;
  licenseDocUrl?: string | null;
}

export interface AdminDispute {
  disputeId: string;
  orderId: string;
  status: string;
  reason: string;
  caseType?: string | null;
  refundStatus?: string | null;
  evidenceCount?: number;
  resolutionNotes?: string | null;
  createdAt?: string | null;
  resolvedAt?: string | null;
}

export interface AdminSupportCase {
  supportCaseId: string;
  title: string;
  detail: string;
  actionType: string;
  entityType?: string | null;
  entityId?: string | null;
  status: string;
  createdAt: string;
}

export function validateServiceAreaDraft(draft: AdminServiceAreaDraft): Record<string, string> {
  const errors: Record<string, string> = {};
  if (!/^[1-9][0-9]{5}$/.test(draft.pincode.trim())) errors.pincode = 'Enter a valid six-digit Indian pincode.';
  if (draft.city.trim().length < 2) errors.city = 'City is required.';
  if (!Number.isFinite(draft.serviceRadiusKm) || draft.serviceRadiusKm < 0.5 || draft.serviceRadiusKm > 100) {
    errors.serviceRadiusKm = 'Radius must be between 0.5 and 100 km.';
  }
  if (draft.reason.trim().length < 3) errors.reason = 'Record a reason for this administrative change.';
  return errors;
}
