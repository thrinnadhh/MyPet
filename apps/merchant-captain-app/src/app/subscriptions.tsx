import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import {
  AppBar,
  FeedbackBanner,
  RoleBadge,
  StateView,
  StatusBadge,
} from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { AppCard } from '@/components/ui/app-card';
import { useAuth } from '@/context/AuthContext';
import { spacing, typography } from '@/design/tokens';
import {
  fetchMerchantSubscriptionDemand,
  type MerchantSubscriptionDemand,
} from '@/services/merchant-subscriptions';
import { formatCurrency, formatDateTime } from '@/utils/formatters';

function tone(status: MerchantSubscriptionDemand['status']): 'success' | 'warning' | 'danger' | 'neutral' {
  if (status === 'ACTIVE') return 'success';
  if (status === 'CANCELLED') return 'danger';
  if (status === 'PAUSED' || status === 'AWAITING_CONFIRMATION') return 'warning';
  return 'neutral';
}

function compact(value: string): string {
  return value.slice(0, 8).toUpperCase();
}

export default function MerchantSubscriptionsScreen() {
  const { activeRole, providerId } = useAuth();
  const [subscriptions, setSubscriptions] = useState<MerchantSubscriptionDemand[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (silent = false) => {
    if (!providerId) {
      setSubscriptions([]);
      setLoading(false);
      return;
    }
    if (!silent) setLoading(true);
    try {
      setSubscriptions(await fetchMerchantSubscriptionDemand(providerId));
      setError(null);
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : 'Could not load recurring demand.');
      if (!silent) setSubscriptions([]);
    } finally {
      if (!silent) setLoading(false);
    }
  }, [providerId]);

  useEffect(() => {
    void load();
    if (!providerId) return undefined;
    const interval = setInterval(() => void load(true), 15_000);
    return () => clearInterval(interval);
  }, [load, providerId]);

  const activeCount = useMemo(
    () => subscriptions.filter((subscription) => subscription.status === 'ACTIVE').length,
    [subscriptions],
  );

  if (activeRole !== 'PROVIDER') {
    return (
      <ScreenShell scroll={false} header={<AppBar title="Subscriptions" />}>
        <StateView kind="unauthorized" title="Merchant access required" message="Recurring demand is available only to the owning merchant." />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell
      header={
        <AppBar
          eyebrow="MERCHANT WORKSPACE"
          title="Recurring demand"
          subtitle={`${activeCount} active subscription${activeCount === 1 ? '' : 's'} · customer terms are read-only`}
          action={<RoleBadge role="merchant" />}
        />
      }
      testID="merchant-subscriptions"
    >
      {!providerId ? (
        <StateView kind="unauthorized" title="Store profile required" message="Activate a merchant provider before viewing recurring demand." />
      ) : null}

      {providerId && error ? (
        <StateView kind="error" title="Recurring demand unavailable" message={error} actionLabel="Retry" onAction={() => void load()} />
      ) : null}

      {providerId && loading ? (
        <StateView kind="loading" title="Loading recurring demand" message="Reading the server-authoritative subscription schedule…" />
      ) : null}

      {providerId && !loading && !error && subscriptions.length === 0 ? (
        <StateView kind="empty" title="No recurring demand" message="Customer subscriptions for this store will appear here before their scheduled order dates." />
      ) : null}

      {providerId && !loading && subscriptions.length > 0 ? (
        <View style={styles.list}>
          {subscriptions.map((subscription) => (
            <AppCard key={subscription.subscriptionId} style={styles.card}>
              <View style={styles.headerRow}>
                <View style={styles.flex}>
                  <ThemedText style={styles.title}>Due {formatDateTime(subscription.nextOrderAt)}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    Subscription #{compact(subscription.subscriptionId)} · every {subscription.cadenceDays} days · {subscription.paymentMethod}
                  </ThemedText>
                </View>
                <StatusBadge label={subscription.status.replaceAll('_', ' ')} tone={tone(subscription.status)} />
              </View>

              <View style={styles.items}>
                {subscription.items.map((item) => (
                  <View key={item.offeringId} style={styles.itemRow}>
                    <View style={styles.flex}>
                      <ThemedText type="smallBold">{item.name}</ThemedText>
                      <ThemedText type="small" themeColor="textSecondary">
                        Offering #{compact(item.offeringId)} · base {item.baseQuantity} × multiplier {subscription.quantityMultiplier}
                      </ThemedText>
                    </View>
                    <View style={styles.itemValue}>
                      <ThemedText type="smallBold">{item.effectiveQuantity} units</ThemedText>
                      <ThemedText type="small" themeColor="textSecondary">created at {formatCurrency(item.unitPriceAtCreation)}</ThemedText>
                    </View>
                  </View>
                ))}
              </View>

              <ThemedText type="small" themeColor="textSecondary">
                Customer #{compact(subscription.customerId)} · source order #{compact(subscription.sourceOrderId)}
              </ThemedText>

              {subscription.lastOrderId ? (
                <FeedbackBanner
                  tone="success"
                  title={`Generated order #${compact(subscription.lastOrderId)}`}
                  message="This is a normal order. Manage it from Orders using the standard ACCEPT → PREPARING → READY lifecycle."
                />
              ) : null}

              {subscription.lastFailureCode ? (
                <FeedbackBanner
                  tone="warning"
                  title={`Recurring run: ${subscription.lastFailureCode.replaceAll('_', ' ')}`}
                  message={subscription.lastFailureDetail ?? 'No invalid order was created for the failed occurrence.'}
                />
              ) : null}

              {subscription.paymentMethod !== 'COD' ? (
                <FeedbackBanner
                  tone="info"
                  title="Prepaid subscription"
                  message="MyPet never silently charges the customer. A generated prepaid order must reach normal payment success before merchant acceptance is allowed."
                />
              ) : null}
            </AppCard>
          ))}
        </View>
      ) : null}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  list: { gap: spacing.x4 },
  card: { gap: spacing.x3 },
  headerRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.x3 },
  title: { ...typography.title },
  items: { gap: spacing.x2 },
  itemRow: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing.x3 },
  itemValue: { alignItems: 'flex-end', gap: spacing.x1 },
});