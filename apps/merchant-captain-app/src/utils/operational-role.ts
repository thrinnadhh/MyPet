import type { Session, User } from '@supabase/supabase-js';

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

export function trustedOperationalRole(user: Pick<User, 'app_metadata'>): TrustedOperationalRole | null {
  const raw = normalizedRole(user.app_metadata?.role);
  const role = raw === 'PROVIDER' ? 'MERCHANT' : raw;
  return role && TRUSTED_ROLES.has(role as TrustedOperationalRole)
    ? (role as TrustedOperationalRole)
    : null;
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
  return requested && SELF_SERVICE_ROLES.has(requested as SelfServiceOperationalRole)
    ? (requested as SelfServiceOperationalRole)
    : null;
}

export async function claimRequestedOperationalRole(
  session: Session,
  requestedRole: SelfServiceOperationalRole,
): Promise<Session> {
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
