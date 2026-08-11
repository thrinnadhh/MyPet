import { useLocalSearchParams, useRouter } from 'expo-router';
import React, { useCallback, useEffect, useState } from 'react';
import { Alert, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import {
  AppBar,
  FeedbackBanner,
  FilterChip,
  PrimaryAction,
  SectionHeader,
  StateView,
  StatusBadge,
} from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { AppCard } from '@/components/ui/app-card';
import { RECURRING_CADENCES, type RecurringCadence, type RecurringOrderSubscription } from '@/contracts/recurring-orders';
import { apiErrorMessage } from '@/contracts/api-error';
import { useAuth } from '@/context/AuthContext';
import { useAuthIntent } from '@/context/AuthIntentContext';
import { useCart } from '@/context/CartContext';
import { spacing, typography } from '@/design/tokens';
import {
  confirmRecurringOrder,
  createRecurringOrder,
  fetchRecurringOrders,
  updateRecurringOrder,
} from '@/services/recurring-orders';
import { buildCartFromRevalidation } from '@/services/revalidated-cart';

function statusTone(status: RecurringOrderSubscription['status']): 'success' | 'warning' | 'error' | 'neutral' {
  if (status === 'ACTIVE') return 'success';
  if (status === 'AWAITING_CONFIRMATION') return 'warning';
  if (status === 'CANCELLED') return 'error';
  return 'neutral';
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(value));
}

function compact(value: string): string {
  return value.slice(0, 8).toUpperCase();
}

export default function RecurringOrdersScreen() {
  const router = useRouter();
  const params = useLocalSearchParams<{ sourceOrderId?: string }>();
  const { user, session } = useAuth();
  const { requireAuth } = useAuthIntent();
  const { replaceCart } = useCart();
  const [subscriptions, setSubscriptions] = useState<RecurringOrderSubscription[]>([]);
  const [cadence, setCadence] = useState<RecurringCadence>(30);
  const [quantityMultiplier, setQuantityMultiplier] = useState(1);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);

  const load = useCallback(async () => {
    if (!session) {
      setLoading(false);
      return;
    }
    setError(null);
    try {
      setSubscriptions(await fetchRecurringOrders(session.access_token));
    } catch (nextError) {
      setError(nextError);
    } finally {
      setLoading(false);
    }
  }, [session]);

  useEffect(() => {
    void load();
  }, [load]);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  }, [load]);

  const create = useCallback(async () => {
    if (!session || !params.sourceOrderId) return;
    setBusyId('create');
    try {
      const created = await createRecurringOrder(
        params.sourceOrderId,
        cadence,
        quantityMultiplier,
        session.access_token,
      );
      setSubscriptions((current) => [created, ...current]);
      Alert.alert(
        'Subscription active',
        created.paymentMethod === 'COD'
          ? 'MyPet will create the next COD order on schedule using current stock, price and serviceability checks.'
          : 'MyPet will create the next order on schedule. Payment is never charged silently; prepaid orders remain pending until you complete normal payment.',
      );
    } catch (nextError) {
      Alert.alert('Could not subscribe', apiErrorMessage(nextError));
    } finally {
      setBusyId(null);
    }
  }, [cadence, params.sourceOrderId, quantityMultiplier, session]);

  const action = useCallback(async (
    subscription: RecurringOrderSubscription,
    nextAction: 'PAUSE' | 'RESUME' | 'SKIP' | 'CANCEL',
  ) => {
    if (!session) return;
    setBusyId(subscription.subscriptionId);
    try {
      const updated = await updateRecurringOrder(subscription.subscriptionId, nextAction, session.access_token);
      setSubscriptions((current) => current.map((item) => item.subscriptionId === updated.subscriptionId ? updated : item));
    } catch (nextError) {
      Alert.alert('Subscription update failed', apiErrorMessage(nextError));
    } finally {
      setBusyId(null);
    }
  }, [session]);

  const confirm = useCallback(async (subscription: RecurringOrderSubscription) => {
    if (!session) return;
    setBusyId(subscription.subscriptionId);
    try {
      const result = await confirmRecurringOrder(subscription.subscriptionId, session.access_token);
      setSubscriptions((current) => current.map((item) => item.subscriptionId === result.subscription.subscriptionId ? result.subscription : item));
      if (result.reorder.canReorder) {
        const nextItems = await buildCartFromRevalidation(result.reorder);
        await replaceCart(nextItems);
        Alert.alert(
          'Order revalidated',
          'Current products and quantities are in your cart. Checkout will calculate a new server-authoritative quote; prepaid payment is never charged silently.',
          [
            { text: 'Later', style: 'cancel' },
            { text: 'Open cart', onPress: () => router.push('/cart' as never) },
          ],
        );
      } else {
        const unavailable = result.reorder.items
          .filter((item) => !item.isAvailable)
          .map((item) => `${item.offeringName}: ${item.message ?? 'Unavailable'}`)
          .join('\n');
        Alert.alert('Confirmation needs changes', unavailable || 'The provider or one of the items is currently unavailable.');
      }
    } catch (nextError) {
      Alert.alert('Confirmation failed', apiErrorMessage(nextError));
    } finally {
      setBusyId(null);
    }
  }, [replaceCart, router, session]);

  if (!user || !session) {
    return (
      <ScreenShell scroll={false} header={<AppBar title="Recurring orders" subtitle="Scheduled operational orders" />}>
        <StateView
          kind="unauthenticated"
          title="Sign in to manage subscriptions"
          message="MyPet validates merchant availability, stock, price and delivery serviceability before each recurring order."
          actionLabel="Sign in"
          onAction={() => void requireAuth({ action: 'ORDER_HISTORY', returnTo: '/subscriptions' })}
        />
      </ScreenShell>
    );
  }

  if (loading) {
    return (
      <ScreenShell scroll={false} header={<AppBar title="Recurring orders" />}>
        <StateView kind="loading" title="Loading subscriptions" />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell
      header={<AppBar title="Recurring orders" subtitle="One scheduled run creates at most one real order" />}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
      testID="recurring-orders-screen"
    >
      <FeedbackBanner
        tone="info"
        title="No silent charging"
        message="A recurring schedule never gives MyPet permission to silently charge a prepaid payment method. Prepaid occurrences must complete the normal payment flow before merchant acceptance."
      />
      <FeedbackBanner
        tone="info"
        title="Revalidate and confirm"
        message="Before each occurrence becomes a real order, MyPet revalidates the merchant, delivery serviceability, current stock and current price. Invalid occurrences stop without creating an order or payment."
      />

      {params.sourceOrderId ? (
        <AppCard style={styles.card}>
          <SectionHeader title="Subscribe to this completed order" />
          <ThemedText type="small" themeColor="textSecondary">
            Source order #{compact(params.sourceOrderId)}. Products and base quantities are snapshotted now; every run rechecks the merchant, current stock, current price and delivery area.
          </ThemedText>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.row}>
            {RECURRING_CADENCES.map((days) => (
              <FilterChip key={days} label={`${days} days`} selected={cadence === days} onPress={() => setCadence(days)} />
            ))}
          </ScrollView>
          <View style={styles.row}>
            {[1, 2, 3, 4].map((quantity) => (
              <FilterChip key={quantity} label={`${quantity}× quantity`} selected={quantityMultiplier === quantity} onPress={() => setQuantityMultiplier(quantity)} />
            ))}
          </View>
          <PrimaryAction label="Activate subscription" loading={busyId === 'create'} onPress={() => void create()} />
        </AppCard>
      ) : null}

      {error ? (
        <StateView kind="error" title="Subscriptions unavailable" message={apiErrorMessage(error)} actionLabel="Retry" onAction={() => void load()} />
      ) : null}

      {!error && subscriptions.length === 0 ? (
        <StateView kind="empty" title="No recurring orders" message="Open a completed order and select Subscribe to schedule it every 7, 15, 25, 30 or 35 days." />
      ) : null}

      {!error && subscriptions.length > 0 ? (
        <View style={styles.list}>
          {subscriptions.map((subscription) => (
            <AppCard key={subscription.subscriptionId} style={styles.card}>
              <View style={styles.headerRow}>
                <View style={styles.flex}>
                  <ThemedText style={styles.title}>Every {subscription.cadenceDays} days</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {subscription.paymentMethod} · {subscription.quantityMultiplier}× base quantities
                  </ThemedText>
                </View>
                <StatusBadge label={subscription.status.replaceAll('_', ' ')} tone={statusTone(subscription.status)} />
              </View>

              <View style={styles.itemList}>
                {subscription.items.map((item) => (
                  <ThemedText key={item.offeringId} type="small">
                    {item.name} · {item.effectiveQuantity} unit{item.effectiveQuantity === 1 ? '' : 's'}
                  </ThemedText>
                ))}
              </View>

              <ThemedText type="small" themeColor="textSecondary">Next scheduled order {formatDate(subscription.nextOrderAt)}</ThemedText>
              {subscription.lastExecutedAt ? (
                <ThemedText type="small" themeColor="textSecondary">Last run {formatDate(subscription.lastExecutedAt)}</ThemedText>
              ) : null}
              {subscription.lastOrderId ? (
                <FeedbackBanner
                  tone="success"
                  title={`Generated order #${compact(subscription.lastOrderId)}`}
                  message={subscription.paymentMethod === 'COD'
                    ? 'The generated COD order follows the normal merchant fulfilment lifecycle.'
                    : 'The generated prepaid order requires normal payment before the merchant can accept it.'}
                />
              ) : null}
              {subscription.lastFailureCode ? (
                <FeedbackBanner
                  tone="warning"
                  title={subscription.lastFailureCode.replaceAll('_', ' ')}
                  message={subscription.lastFailureDetail ?? 'The scheduled run was not converted into an invalid order.'}
                />
              ) : null}

              {subscription.status === 'AWAITING_CONFIRMATION' ? (
                <PrimaryAction label="Reactivate migrated subscription" loading={busyId === subscription.subscriptionId} onPress={() => void confirm(subscription)} />
              ) : null}

              {subscription.status !== 'CANCELLED' ? (
                <View style={styles.actions}>
                  {subscription.status === 'PAUSED' ? (
                    <FilterChip label="Resume" selected={false} onPress={() => void action(subscription, 'RESUME')} />
                  ) : (
                    <FilterChip label="Pause" selected={false} onPress={() => void action(subscription, 'PAUSE')} />
                  )}
                  <FilterChip label="Skip next" selected={false} onPress={() => void action(subscription, 'SKIP')} />
                  <FilterChip label="Cancel" selected={false} onPress={() => void action(subscription, 'CANCEL')} />
                </View>
              ) : null}
            </AppCard>
          ))}
        </View>
      ) : null}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  list: { gap: spacing.x4 },
  card: { gap: spacing.x3 },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: spacing.x3 },
  flex: { flex: 1 },
  title: { ...typography.title },
  itemList: { gap: spacing.x1 },
});
