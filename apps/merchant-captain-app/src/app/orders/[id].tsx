import { useLocalSearchParams, useRouter } from 'expo-router';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { StyleSheet, TextInput, View } from 'react-native';

import {
  ActionButton,
  AppBar,
  FeedbackBanner,
  RoleBadge,
  StateView,
  StatusBadge,
} from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { AppCard } from '@/components/ui/app-card';
import { apiErrorKind, apiErrorMessage } from '@/contracts/api-error';
import { radii, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import {
  fetchMerchantOrder,
  isMerchantOrderInQueue,
  merchantOrderActions,
  transitionMerchantOrder,
  type MerchantOrder,
  type MerchantOrderActionDefinition,
} from '@/services/merchant-orders';
import { formatCurrency, formatDateTime, formatOrderStatus } from '@/utils/formatters';

export default function MerchantOrderDetailScreen() {
  const params = useLocalSearchParams<{ id?: string | string[] }>();
  const router = useRouter();
  const theme = useTheme();
  const orderId = useMemo(() => {
    const raw = Array.isArray(params.id) ? params.id[0] : params.id;
    return raw ? decodeURIComponent(raw) : '';
  }, [params.id]);
  const [order, setOrder] = useState<MerchantOrder | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [note, setNote] = useState('');
  const [pendingAction, setPendingAction] = useState<MerchantOrderActionDefinition | null>(null);

  const load = useCallback(async () => {
    if (!orderId) {
      setError(new Error('Missing order ID'));
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setOrder(await fetchMerchantOrder(orderId));
    } catch (loadError) {
      setError(loadError);
    } finally {
      setLoading(false);
    }
  }, [orderId]);

  useEffect(() => {
    void load();
  }, [load]);

  const actions = useMemo(() => {
    if (!order) return [];
    if (order.status === 'PLACED' && !isMerchantOrderInQueue(order.status, order.paymentStatus, 'NEW')) {
      return [];
    }
    return merchantOrderActions(order.status);
  }, [order]);

  const applyAction = useCallback(async (action: MerchantOrderActionDefinition) => {
    if (!order) return;
    if (action.destructive && !note.trim()) {
      setError(new Error('Add a reason before rejecting or cancelling an order.'));
      return;
    }
    setPendingAction(action);
    setError(null);
    try {
      const updated = await transitionMerchantOrder(order.orderId, action.status, note);
      setOrder(updated);
      setNote('');
    } catch (transitionError) {
      setError(transitionError);
      if (apiErrorKind(transitionError) === 'conflict') void load();
    } finally {
      setPendingAction(null);
    }
  }, [load, note, order]);

  return (
    <ScreenShell
      header={
        <AppBar
          eyebrow="MERCHANT ORDER"
          title={order ? `Order #${order.orderId.slice(0, 8).toUpperCase()}` : 'Order details'}
          subtitle="Review the exact order before changing its lifecycle"
          action={<RoleBadge role="merchant" />}
        />
      }
      testID="merchant-order-detail"
    >
      <ActionButton
        label="Back to orders"
        icon="chevron-left"
        variant="ghost"
        onPress={() => router.replace('/orders' as never)}
      />

      {loading ? <StateView kind="loading" title="Loading order" message="Fetching the current server state…" /> : null}

      {!loading && error ? (
        <FeedbackBanner
          tone="danger"
          title={apiErrorKind(error) === 'conflict' ? 'Order changed on the server' : 'Order action failed'}
          message={apiErrorMessage(error, 'Could not load or update this order.')}
          icon="dispute"
        />
      ) : null}

      {!loading && order ? (
        <AppCard style={styles.card}>
          <View style={styles.headerRow}>
            <View style={styles.flex}>
              <ThemedText style={styles.title}>Customer {order.customerId.slice(0, 8).toUpperCase()}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">Placed {formatDateTime(order.placedAt)}</ThemedText>
            </View>
            <StatusBadge label={formatOrderStatus(order.status)} tone="warning" />
          </View>

          <View style={[styles.summary, { backgroundColor: theme.muted }]}>
            <View style={styles.summaryCell}>
              <ThemedText type="small" themeColor="textSecondary">Total</ThemedText>
              <ThemedText style={styles.amount}>{formatCurrency(order.totalAmount)}</ThemedText>
            </View>
            <View style={styles.summaryCell}>
              <ThemedText type="small" themeColor="textSecondary">Payment</ThemedText>
              <ThemedText type="smallBold">{order.paymentMethod} · {formatOrderStatus(order.paymentStatus)}</ThemedText>
            </View>
          </View>

          {order.status === 'PLACED' && !isMerchantOrderInQueue(order.status, order.paymentStatus, 'NEW') ? (
            <FeedbackBanner
              tone="warning"
              title="Not actionable yet"
              message="Online orders stay PLACED until payment succeeds. Accept and Reject are disabled until payment is confirmed."
            />
          ) : null}

          {actions.some((action) => action.destructive) ? (
            <TextInput
              value={note}
              onChangeText={setNote}
              placeholder="Reason for reject/cancel (required for destructive actions)"
              placeholderTextColor={theme.textSecondary}
              multiline
              style={[styles.note, { color: theme.text, borderColor: theme.border, backgroundColor: theme.background }]}
              accessibilityLabel="Order action reason"
            />
          ) : null}

          {actions.length > 0 ? (
            <View style={styles.actions}>
              {actions.map((action) => (
                <ActionButton
                  key={action.status}
                  label={action.label}
                  icon={action.destructive ? 'xmark' : 'check'}
                  variant={action.destructive ? 'destructive' : 'primary'}
                  loading={pendingAction?.status === action.status}
                  disabled={Boolean(pendingAction) || Boolean(action.destructive && !note.trim())}
                  onPress={() => void applyAction(action)}
                  style={styles.action}
                />
              ))}
            </View>
          ) : (
            <ThemedText type="small" themeColor="textSecondary">
              No merchant transition is available from this lifecycle state.
            </ThemedText>
          )}
        </AppCard>
      ) : null}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  card: { padding: spacing.x4, gap: spacing.x4 },
  headerRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.x3 },
  title: { ...typography.title, fontSize: 18, lineHeight: 24 },
  summary: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x4, padding: spacing.x3, borderRadius: radii.compact },
  summaryCell: { flexGrow: 1, minWidth: 140, gap: spacing.x1 },
  amount: { ...typography.title, fontSize: 20, lineHeight: 26 },
  note: { minHeight: 104, borderWidth: 1, borderRadius: radii.compact, padding: spacing.x3, textAlignVertical: 'top', ...typography.body },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  action: { flexGrow: 1, flexBasis: 180 },
});
