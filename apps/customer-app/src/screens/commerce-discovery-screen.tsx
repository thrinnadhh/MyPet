import { useRouter } from 'expo-router';
import React, { useCallback, useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { AppBar, EntityCard, StateView } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { INITIAL_MARKET } from '@/config/markets';
import { spacing } from '@/design/tokens';
import { useTranslation } from '@/i18n';
import { isOfflineError } from '@/services/customer-profile';
import { fetchProviders, type ProviderSummary } from '@/services/provider-discovery';

type LoadState = 'loading' | 'ready' | 'offline' | 'error';
export default function CommerceDiscoveryScreen() {
  const router = useRouter();
  const { t } = useTranslation();
  const [providers, setProviders] = useState<ProviderSummary[]>([]);
  const [state, setState] = useState<LoadState>('loading');

  const load = useCallback(async () => {
    setState('loading');
    try { setProviders(await fetchProviders('PET_STORE', INITIAL_MARKET)); setState('ready'); }
    catch (error) { setState(isOfflineError(error) ? 'offline' : 'error'); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  return (
    <ScreenShell header={<AppBar title={t('commerceFoundation.title')} subtitle={t('commerceFoundation.subtitle')} />} testID="commerce-discovery-screen">
      {state === 'loading' ? <StateView kind="loading" title={t('states.loading')} /> : null}
      {state === 'offline' || state === 'error' ? <StateView kind={state} title={t(state === 'offline' ? 'states.offline' : 'states.error')} message={t(state === 'offline' ? 'states.offlineMessage' : 'states.errorMessage')} actionLabel={t('states.retry')} onAction={() => void load()} /> : null}
      {state === 'ready' && providers.length === 0 ? <StateView kind="empty" title={t('states.empty')} message={t('commerceFoundation.empty')} /> : null}
      {state === 'ready' ? <View style={styles.list}>{providers.map((provider) => <EntityCard key={provider.id} title={provider.name} subtitle={provider.description || t('commerceFoundation.providerFallback')} meta={t('appointmentFoundation.providerMeta', { distance: provider.distanceKm.toFixed(1), rating: provider.rating.toFixed(1), count: provider.ratingCount })} icon="store" onPress={() => router.push({ pathname: '/providers/[type]/[id]', params: { type: 'pet-store', id: provider.id } } as never)} />)}</View> : null}
    </ScreenShell>
  );
}
const styles = StyleSheet.create({ list: { gap: spacing.x3 } });
