import type { Session, User } from '@supabase/supabase-js';

import { appConfig } from './app-config';
import { supabase } from './supabase';

export type TrustedOperationalRole = 'MERCHANT' | 'CAPTAIN' | 'ADMIN';
export type SelfServiceOperationalRole = 'MERCHANT' | 'CAPTAIN';

const TRUSTED_ROLES = new Set<TrustedOperationalRole>(['MERCHANT', 'CAPTAIN', 'ADMIN']);
const SELF_SERVICE_ROLES = new Set<SelfServiceOperationalRole>(['MERCHANT', 'CAPTAIN']);

function normalizedRole(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const normalized = value.trim().toUpperCase();
  return normalized || null;
}

function roleAllowedByVariant(role: TrustedOperationalRole): boolean {
  return !appConfig.isMerchantBuild || role === 'MERCHANT';
}

export function trustedOperationalRole(user: Pick<User, 'app_metadata'>): TrustedOperationalRole | null {
  const raw = normalizedRole(user.app_metadata?.role);
  const role = raw === 'PROVIDER' ? 'MERCHANT' : raw;
  if (!role || !TRUSTED_ROLES.has(role as TrustedOperationalRole)) return null;

  const trustedRole = role as TrustedOperationalRole;
  return roleAllowedByVariant(trustedRole) ? trustedRole : null;
}

/**
 * User metadata is never authorization evidence. It is read only as an onboarding
 * intent so legacy signups can request a server-side app_metadata claim.
 */
export function requestedSelfServiceRole(
  user: Pick<User, 'user_metadata'>,
): SelfServiceOperationalRole | null {
  const requested = normalizedRole(
    user.user_metadata?.requested_operational_role ?? user.user_metadata?.role,
  );
  if (!requested || !SELF_SERVICE_ROLES.has(requested as SelfServiceOperationalRole)) return null;

  const requestedRole = requested as SelfServiceOperationalRole;
  if (appConfig.isMerchantBuild && requestedRole !== 'MERCHANT') return null;
  return requestedRole;
}

export async function claimRequestedOperationalRole(
  session: Session,
  requestedRole: SelfServiceOperationalRole,
): Promise<Session> {
  if (appConfig.isMerchantBuild && requestedRole !== 'MERCHANT') {
    throw new Error('This MyPet Merchant build can provision merchant accounts only.');
  }

  const { error } = await supabase.functions.invoke('claim-operational-role', {
    body: { role: requestedRole },
    headers: { Authorization: `Bearer ${session.access_token}` },
  });
  if (error) throw error;

  const { data, error: refreshError } = await supabase.auth.refreshSession();
  if (refreshError) throw refreshError;
  if (!data.session) throw new Error('Operational role was granted but the session could not be refreshed.');
  return data.session;
}
