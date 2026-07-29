import { useRouter } from 'expo-router';
import React, { useCallback, useEffect, useState } from 'react';

import { missingProfileRequirements, profileStateFromUser, type ProfileRequirement } from '@/auth/profile-completeness';
import { AppBar, StateView, StickyCta } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { useAuth } from '@/context/AuthContext';
import { useAuthIntent } from '@/context/AuthIntentContext';
import { useTranslation } from '@/i18n';
import { fetchDefaultAddress, isOfflineError } from '@/services/customer-profile';

export default function CheckoutFoundation() {
  const router = useRouter();
  const { t } = useTranslation();
  const { user, session } = useAuth();
  const { requireAuth } = useAuthIntent();
  const [missing, setMissing] = useState<ProfileRequirement[]>([]);
  const [state, setState] = useState<'loading' | 'ready' | 'offline' | 'error'>('loading');

  const check = useCallback(async () => {
    if (!user || !session) return;
    setState('loading');
    try {
      const address = await fetchDefaultAddress(session.access_token);
      setMissing(missingProfileRequirements(profileStateFromUser(user, Boolean(address)), 'CHECKOUT'));
      setState('ready');
    } catch (error) { setState(isOfflineError(error) ? 'offline' : 'error'); }
  }, [session, user]);
  useEffect(() => { if (user && session) void check(); }, [check, session, user]);

  if (!user || !session) return (
    <ScreenShell scroll={false} header={<AppBar title={t('routes.checkout')} />}>
      <StateView kind="unauthenticated" title={t('states.unauthenticated')} message={t('routes.checkoutSignIn')} actionLabel={t('common.signIn')} onAction={() => void requireAuth({ action: 'CHECKOUT', returnTo: '/checkout' })} />
    </ScreenShell>
  );
  if (state === 'loading') return <ScreenShell scroll={false} header={<AppBar title={t('routes.checkout')} />}><StateView kind="loading" title={t('states.loading')} /></ScreenShell>;
  if (state === 'offline' || state === 'error') return <ScreenShell scroll={false} header={<AppBar title={t('routes.checkout')} />}><StateView kind={state} title={t(state === 'offline' ? 'states.offline' : 'states.error')} message={t(state === 'offline' ? 'states.offlineMessage' : 'states.errorMessage')} actionLabel={t('states.retry')} onAction={() => void check()} /></ScreenShell>;
  if (missing.length > 0) return <ScreenShell scroll={false} header={<AppBar title={t('routes.checkout')} />}><StateView kind="error" title={t('profileFoundation.incomplete')} message={t('routes.checkoutProfile')} actionLabel={t('profileFoundation.title')} onAction={() => router.push('/(tabs)/profile' as never)} /></ScreenShell>;
  return (
    <ScreenShell scroll={false} header={<AppBar title={t('routes.checkout')} />} footer={<StickyCta label={t('common.continue')} onPress={() => void requireAuth({ action: 'CHECKOUT', returnTo: '/checkout' })} />}>
      <StateView kind="empty" title={t('routes.checkoutReady')} message={t('routes.foundationMessage')} />
    </ScreenShell>
  );
}
