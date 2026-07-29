import React, { useCallback, useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { AppBar, EntityCard, StateView } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { useAuth } from '@/context/AuthContext';
import { useAuthIntent } from '@/context/AuthIntentContext';
import { spacing } from '@/design/tokens';
import { useTranslation } from '@/i18n';
import { fetchCustomerOrders, type CustomerOrderRecord } from '@/services/customer-orders';
import { isOfflineError } from '@/services/customer-profile';

type LoadState = 'idle' | 'loading' | 'ready' | 'error' | 'offline';

export default function OrdersScreen() {
  const { t } = useTranslation();
  const { user, session } = useAuth();
  const { requireAuth } = useAuthIntent();
  const [orders, setOrders] = useState<CustomerOrderRecord[]>([]);
  const [state, setState] = useState<LoadState>('idle');

  const load = useCallback(async () => {
    if (!user || !session) return;
    setState('loading');
    try {
      setOrders(await fetchCustomerOrders(user.id, session.access_token));
      setState('ready');
    } catch (error) {
      setState(isOfflineError(error) ? 'offline' : 'error');
    }
  }, [session, user]);

  useEffect(() => { if (user && session) void load(); }, [load, session, user]);

  if (!user || !session) {
    return (
      <ScreenShell scroll={false} header={<AppBar title={t('ordersFoundation.title')} subtitle={t('ordersFoundation.subtitle')} />}>
        <StateView
          kind="unauthenticated"
          title={t('states.unauthenticated')}
          message={t('ordersFoundation.signInMessage')}
          actionLabel={t('common.signIn')}
          onAction={() => void requireAuth({ action: 'ORDER_HISTORY', returnTo: '/(tabs)/orders' })}
        />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell header={<AppBar title={t('ordersFoundation.title')} subtitle={t('ordersFoundation.subtitle')} />} testID="orders-screen">
      {state === 'loading' || state === 'idle' ? <StateView kind="loading" title={t('states.loading')} message={t('states.loadingMessage')} /> : null}
      {state === 'offline' ? <StateView kind="offline" title={t('states.offline')} message={t('states.offlineMessage')} actionLabel={t('states.retry')} onAction={() => void load()} /> : null}
      {state === 'error' ? <StateView kind="error" title={t('states.error')} message={t('ordersFoundation.loadError')} actionLabel={t('states.retry')} onAction={() => void load()} /> : null}
      {state === 'ready' && orders.length === 0 ? <StateView kind="empty" title={t('ordersFoundation.emptyTitle')} message={t('ordersFoundation.emptyMessage')} /> : null}
      {state === 'ready' && orders.length > 0 ? <View style={styles.list}>{orders.map((order) => <EntityCard key={order.id} title={order.providerName} subtitle={order.items.join(' · ')} meta={t('ordersFoundation.total', { amount: order.total })} icon="cart" badge={order.flowStep} onPress={() => undefined} />)}</View> : null}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({ list: { gap: spacing.x3 } });
