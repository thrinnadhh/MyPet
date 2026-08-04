import React, { useCallback, useEffect, useState } from 'react';
import { Alert, StyleSheet, View } from 'react-native';

import { BottomSheet, EntityCard, StateView } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ScreenHeader } from '@/components/ui/screen-header';
import { INITIAL_MARKET } from '@/config/markets';
import { useAuthIntent } from '@/context/AuthIntentContext';
import { spacing } from '@/design/tokens';
import { useTranslation } from '@/i18n';
import { fetchAvailableAppointmentSlots, type AppointmentSlotOption } from '@/services/appointment-booking';
import { isOfflineError } from '@/services/customer-profile';
import { fetchProviders, type DiscoverableProviderType, type ProviderSummary } from '@/services/provider-discovery';

interface Props { providerType: DiscoverableProviderType; route: '/vet' | '/groom'; titleKey: 'appointmentFoundation.vetTitle' | 'appointmentFoundation.groomTitle' }
type LoadState = 'loading' | 'ready' | 'offline' | 'error';

export default function AppointmentDiscoveryScreen({ providerType, route, titleKey }: Props) {
  const { t } = useTranslation();
  const { requireAuth } = useAuthIntent();
  const [providers, setProviders] = useState<ProviderSummary[]>([]);
  const [state, setState] = useState<LoadState>('loading');
  const [provider, setProvider] = useState<ProviderSummary | null>(null);
  const [slots, setSlots] = useState<AppointmentSlotOption[]>([]);
  const [slotState, setSlotState] = useState<LoadState>('ready');

  const loadProviders = useCallback(async () => {
    setState('loading');
    try { setProviders(await fetchProviders(providerType, INITIAL_MARKET)); setState('ready'); }
    catch (error) { setState(isOfflineError(error) ? 'offline' : 'error'); }
  }, [providerType]);
  useEffect(() => { void loadProviders(); }, [loadProviders]);

  const chooseProvider = useCallback(async (next: ProviderSummary) => {
    setProvider(next); setSlots([]); setSlotState('loading');
    try { setSlots(await fetchAvailableAppointmentSlots(next.id)); setSlotState('ready'); }
    catch (error) { setSlotState(isOfflineError(error) ? 'offline' : 'error'); }
  }, []);

  const requestBooking = useCallback(async () => {
    await requireAuth({ action: 'BOOKING', returnTo: route }, async () => {
      Alert.alert(t('appointmentFoundation.petRequiredTitle'), t('appointmentFoundation.petRequiredMessage'));
    });
  }, [requireAuth, route, t]);

  const close = () => { setProvider(null); setSlots([]); };

  return (
    <ScreenShell
      header={
        <ScreenHeader
          title={t(titleKey)}
          subtitle={t('appointmentFoundation.subtitle')}
        />
      }
      testID={`${providerType.toLowerCase()}-discovery-screen`}
    >
      {state === 'loading' ? <StateView kind="loading" title={t('states.loading')} /> : null}
      {state === 'offline' || state === 'error' ? <StateView kind={state} title={t(state === 'offline' ? 'states.offline' : 'states.error')} message={t(state === 'offline' ? 'states.offlineMessage' : 'states.errorMessage')} actionLabel={t('states.retry')} onAction={() => void loadProviders()} /> : null}
      {state === 'ready' && providers.length === 0 ? <StateView kind="empty" title={t('states.empty')} message={t('states.emptyMessage')} /> : null}
      {state === 'ready' ? <View style={styles.list}>{providers.map((item) => <EntityCard key={item.id} title={item.name} subtitle={item.description || t('appointmentFoundation.providerFallback')} meta={t('appointmentFoundation.providerMeta', { distance: item.distanceKm.toFixed(1), rating: item.rating.toFixed(1), count: item.ratingCount })} icon={providerType === 'GROOMER' ? 'groom' : 'medical'} onPress={() => void chooseProvider(item)} />)}</View> : null}

      <BottomSheet visible={Boolean(provider)} title={t('appointmentFoundation.slots')} onClose={close}>
        {slotState === 'loading' ? <StateView kind="loading" title={t('states.loading')} /> : null}
        {slotState === 'offline' || slotState === 'error' ? <StateView kind={slotState} title={t(slotState === 'offline' ? 'states.offline' : 'states.error')} message={t(slotState === 'offline' ? 'states.offlineMessage' : 'appointmentFoundation.holdFailed')} actionLabel={provider ? t('states.retry') : undefined} onAction={provider ? () => void chooseProvider(provider) : undefined} /> : null}
        {slotState === 'ready' && slots.length === 0 ? <StateView kind="empty" title={t('appointmentFoundation.noSlots')} /> : null}
        {slotState === 'ready' ? <View style={styles.list}>{slots.map((slot) => <EntityCard key={slot.id} title={slot.serviceName} subtitle={`${slot.startTime} – ${slot.endTime}`} meta={t('appointmentFoundation.price', { price: slot.price })} icon="calendar" onPress={() => void requestBooking()} />)}</View> : null}
      </BottomSheet>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({ list: { gap: spacing.x3 } });
