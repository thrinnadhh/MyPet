import { useRouter } from 'expo-router';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import {
  ActionButton,
  AppBar,
  FeedbackBanner,
  FilterChip,
  RoleBadge,
  StateView,
  StatusBadge,
} from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { AppCard } from '@/components/ui/app-card';
import { ApiError, apiErrorKind, apiErrorMessage } from '@/contracts/api-error';
import { useAuth } from '@/context/AuthContext';
import { radii, shadows, spacing, touchTarget, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import {
  fetchMerchantOrders,
  isMerchantOrderInQueue,
  merchantOrderActions,
  transitionMerchantOrder,
  type MerchantOrder,
  type MerchantOrderActionDefinition,
  type MerchantOrderQueue,
} from '@/services/merchant-orders';
import { formatCurrency, formatDateTime, formatOrderStatus } from '@/utils/formatters';

const FILTERS: MerchantOrderQueue[] = ['NEW', 'ACCEPTED', 'PREPARING', 'READY', 'DELIVERY', 'PAST'];
const FILTER_LABELS: Record<MerchantOrderQueue, string> = {
  NEW: 'New',
  ACCEPTED: 'Accepted',
  PREPARING: 'Preparing',
  READY: 'Ready',
  DELIVERY: 'Delivery',
  PAST: 'Past',
};

function filterOrder(order: MerchantOrder, filter: MerchantOrderQueue): boolean {
  return isMerchantOrderInQueue(order.status, order.paymentStatus, filter);
}

function tone(status: MerchantOrder['status']): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
  if (['DELIVERED', 'COMPLETED'].includes(status)) return 'success';
  if (['REJECTED', 'CANCELLED'].includes(status)) return 'danger';
  if (['PLACED', 'ACCEPTED', 'PREPARING', 'READY_FOR_PICKUP'].includes(status)) return 'warning';
  if (['ASSIGNED', 'PICKED_UP'].includes(status)) return 'info';
  return 'neutral';
}

export default function MerchantOrdersScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { providerId } = useAuth();
  const [orders, setOrders] = useState<MerchantOrder[]>([]);
  const [filter, setFilter] = useState<MerchantOrderQueue>('NEW');
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [refreshingOrderId, setRefreshingOrderId] = useState<string | null>(null);
  const [pending, setPending] = useState<{
    order: MerchantOrder;
    action: MerchantOrderActionDefinition;
  } | null>(null);
  const [note, setNote] = useState('');

  const load = useCallback(async () => {
    if (!providerId) {
      setOrders([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setOrders(await fetchMerchantOrders(providerId));
    } catch (loadError) {
      setError(loadError);
      setOrders([]);
    } finally {
      setLoading(false);
    }
  }, [providerId]);

  useEffect(() => {
    void load();
  }, [load]);

  const counts = useMemo(() => {
    const result = {} as Record<MerchantOrderQueue, number>;
    FILTERS.forEach((queue) => {
      result[queue] = orders.filter((order) => filterOrder(order, queue)).length;
    });
    return result;
  }, [orders]);

  const visibleOrders = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return orders.filter((order) => {
      if (!filterOrder(order, filter)) return false;
      if (!normalizedQuery) return true;
      return [order.orderId, order.customerId, order.paymentMethod, order.paymentStatus]
        .some((value) => value?.toLowerCase().includes(normalizedQuery));
    });
  }, [filter, orders, query]);

  const applyTransition = useCallback(async () => {
    if (!pending) return;
    setRefreshingOrderId(pending.order.orderId);
    try {
      const updated = await transitionMerchantOrder(
        pending.order.orderId,
        pending.action.status,
        note,
      );
      setOrders((current) =>
        current.map((order) => (order.orderId === updated.orderId ? updated : order)),
      );
      setPending(null);
      setNote('');
    } catch (transitionError) {
      setError(transitionError);
      if (apiErrorKind(transitionError) === 'conflict') void load();
    } finally {
      setRefreshingOrderId(null);
    }
  }, [load, note, pending]);

  const errorTrace = error instanceof ApiError && error.traceId ? ` Reference: ${error.traceId}.` : '';

  return (
    <ScreenShell
      header={
        <AppBar
          eyebrow="MERCHANT WORKSPACE"
          title="Orders"
          subtitle="Accept, prepare and hand over actionable customer orders"
          action={<RoleBadge role="merchant" />}
        />
      }
      testID="merchant-orders"
    >
      {!providerId ? (
        <StateView
          kind="unauthorized"
          title="Store profile required"
          message="Complete and approve merchant onboarding before managing provider orders."
        />
      ) : null}

      {providerId && error ? (
        <FeedbackBanner
          tone="danger"
          title={apiErrorKind(error) === 'conflict' ? 'Order changed on the server' : 'Orders need attention'}
          message={`${apiErrorMessage(error, 'Could not load or update orders.')}${errorTrace}`}
          icon="dispute"
        />
      ) : null}

      {providerId ? (
        <>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filters}>
            {FILTERS.map((value) => (
              <FilterChip
                key={value}
                label={`${FILTER_LABELS[value]} (${counts[value] ?? 0})`}
                selected={filter === value}
                onPress={() => setFilter(value)}
              />
            ))}
          </ScrollView>

          <View style={[styles.search, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
            <AppIcon name="search" color={theme.textSecondary} size={18} />
            <TextInput
              value={query}
              onChangeText={setQuery}
              placeholder="Search order or customer ID"
              placeholderTextColor={theme.textSecondary}
              style={[styles.searchInput, { color: theme.text }]}
              accessibilityLabel="Search merchant orders"
              returnKeyType="search"
            />
            {query ? (
              <Pressable
                onPress={() => setQuery('')}
                accessibilityRole="button"
                accessibilityLabel="Clear order search"
                style={styles.clear}
              >
                <AppIcon name="xmark" color={theme.textSecondary} size={18} />
              </Pressable>
            ) : null}
          </View>

          {loading ? <StateView kind="loading" title="Loading orders" message="Checking the latest server state…" /> : null}
          {!loading && !error && visibleOrders.length === 0 ? (
            <StateView
              kind="empty"
              title={query ? 'No matching orders' : 'No orders in this queue'}
              message={
                query
                  ? 'Try another order or customer ID.'
                  : filter === 'NEW'
                    ? 'COD orders and paid online orders waiting for your decision will appear here.'
                    : 'Orders will move here only through the canonical lifecycle.'
              }
              actionLabel={query ? 'Clear search' : 'Refresh'}
              onAction={query ? () => setQuery('') : () => void load()}
            />
          ) : null}

          {!loading && visibleOrders.length > 0 ? (
            <View style={styles.list}>
              {visibleOrders.map((order) => {
                const actions = merchantOrderActions(order.status);
                return (
                  <AppCard key={order.orderId} style={styles.card}>
                    <View style={styles.cardHeader}>
                      <View style={[styles.orderIcon, { backgroundColor: theme.primarySoft }]}>
                        <AppIcon name="cart" color={theme.primary} size={22} />
                      </View>
                      <View style={styles.flex}>
                        <ThemedText style={styles.orderTitle}>Order #{order.orderId.slice(0, 8).toUpperCase()}</ThemedText>
                        <ThemedText type="small" themeColor="textSecondary">
                          {formatDateTime(order.placedAt)} · Customer {order.customerId.slice(0, 8).toUpperCase()}
                        </ThemedText>
                      </View>
                      <StatusBadge label={formatOrderStatus(order.status)} tone={tone(order.status)} />
                    </View>

                    <View style={[styles.summary, { backgroundColor: theme.muted }]}>
                      <View style={styles.summaryCell}>
                        <ThemedText type="small" themeColor="textSecondary">Order total</ThemedText>
                        <ThemedText style={styles.amount}>{formatCurrency(order.totalAmount)}</ThemedText>
                      </View>
                      <View style={styles.summaryCell}>
                        <ThemedText type="small" themeColor="textSecondary">Payment</ThemedText>
                        <ThemedText type="smallBold">{order.paymentMethod} · {formatOrderStatus(order.paymentStatus)}</ThemedText>
                      </View>
                    </View>

                    {order.cancellationReason ? (
                      <FeedbackBanner tone="warning" title="Cancellation note" message={order.cancellationReason} />
                    ) : null}

                    <View style={styles.actions}>
                      <ActionButton
                        label="View operational detail"
                        icon="document"
                        variant="ghost"
                        onPress={() => router.push(`/orders/${order.orderId}` as never)}
                        style={styles.action}
                      />
                      {actions.map((action) => (
                        <ActionButton
                          key={action.status}
                          label={action.label}
                          icon={action.destructive ? 'xmark' : 'check'}
                          variant={action.destructive ? 'destructive' : 'primary'}
                          loading={refreshingOrderId === order.orderId}
                          onPress={() => {
                            setError(null);
                            setPending({ order, action });
                            setNote('');
                          }}
                          style={styles.action}
                        />
                      ))}
                    </View>
                    {actions.length === 0 ? (
                      <ThemedText type="small" themeColor="textSecondary">No merchant action is available in this server state.</ThemedText>
                    ) : null}
                  </AppCard>
                );
              })}
            </View>
          ) : null}
        </>
      ) : null}

      <Modal visible={pending !== null} transparent animationType="fade" onRequestClose={() => setPending(null)}>
        <View style={styles.modalBackdrop}>
          <View style={[styles.modal, shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]} accessibilityViewIsModal>
            <ThemedText type="title">{pending?.action.label}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">Order #{pending?.order.orderId.slice(0, 8).toUpperCase()}. The server validates this exact transition and actor.</ThemedText>
            <TextInput
              value={note}
              onChangeText={setNote}
              placeholder={pending?.action.destructive ? 'Reason required for operational records' : 'Optional internal note'}
              placeholderTextColor={theme.textSecondary}
              multiline
              style={[styles.note, { color: theme.text, borderColor: theme.border, backgroundColor: theme.background }]}
              accessibilityLabel="Order status transition note"
            />
            <View style={styles.modalActions}>
              <ActionButton label="Back" variant="ghost" onPress={() => setPending(null)} style={styles.action} />
              <ActionButton
                label="Confirm"
                variant={pending?.action.destructive ? 'destructive' : 'primary'}
                disabled={Boolean(pending?.action.destructive && !note.trim())}
                loading={Boolean(pending && refreshingOrderId === pending.order.orderId)}
                onPress={() => void applyTransition()}
                style={styles.action}
              />
            </View>
          </View>
        </View>
      </Modal>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  filters: { gap: spacing.x2, paddingRight: spacing.x4 },
  search: { minHeight: touchTarget, borderWidth: 1, borderRadius: radii.compact, paddingLeft: spacing.x3, flexDirection: 'row', alignItems: 'center', gap: spacing.x2 },
  searchInput: { flex: 1, minHeight: touchTarget, ...typography.body, paddingVertical: 0 },
  clear: { width: touchTarget, height: touchTarget, alignItems: 'center', justifyContent: 'center' },
  list: { gap: spacing.x3 },
  card: { padding: spacing.x4, gap: spacing.x3 },
  cardHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing.x3 },
  orderIcon: { width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
  orderTitle: { ...typography.title, fontSize: 18, lineHeight: 24 },
  summary: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x4, padding: spacing.x3, borderRadius: radii.compact },
  summaryCell: { flexGrow: 1, minWidth: 140, gap: spacing.x1 },
  amount: { ...typography.title, fontSize: 19, lineHeight: 24 },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  action: { flexGrow: 1, flexBasis: 160 },
  modalBackdrop: { flex: 1, backgroundColor: 'rgba(11,28,48,0.58)', padding: spacing.x4, alignItems: 'center', justifyContent: 'center' },
  modal: { width: '100%', maxWidth: 560, borderWidth: StyleSheet.hairlineWidth, borderRadius: radii.feature, padding: spacing.x5, gap: spacing.x4 },
  note: { minHeight: 112, borderWidth: 1, borderRadius: radii.compact, padding: spacing.x3, textAlignVertical: 'top', ...typography.body },
  modalActions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
});