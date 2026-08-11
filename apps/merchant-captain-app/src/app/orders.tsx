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
  fetchMerchantOrdersPage,
  isMerchantOrderInQueue,
  merchantOrderActions,
  transitionMerchantOrder,
  type MerchantOrder,
  type MerchantOrderActionDefinition,
  type MerchantOrderQueue,
} from '@/services/merchant-orders';
import { formatCurrency, formatDateTime, formatOrderStatus } from '@/utils/formatters';

const ORDER_PAGE_SIZE = 40;
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

function compact(value: string): string {
  return value.slice(0, 8).toUpperCase();
}

function mergeNewestPage(current: MerchantOrder[], newest: MerchantOrder[]): MerchantOrder[] {
  const newestIds = new Set(newest.map((order) => order.orderId));
  return [
    ...newest,
    ...current.filter((order) => !newestIds.has(order.orderId)),
  ];
}

export default function MerchantOrdersScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { providerId } = useAuth();
  const [orders, setOrders] = useState<MerchantOrder[]>([]);
  const [filter, setFilter] = useState<MerchantOrderQueue>('NEW');
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [nextPage, setNextPage] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [refreshingOrderId, setRefreshingOrderId] = useState<string | null>(null);
  const [pending, setPending] = useState<{
    order: MerchantOrder;
    action: MerchantOrderActionDefinition;
  } | null>(null);
  const [note, setNote] = useState('');

  const loadLatest = useCallback(async (silent = false) => {
    if (!providerId) {
      setOrders([]);
      setTotalElements(0);
      setHasMore(false);
      setNextPage(1);
      setLoading(false);
      return;
    }
    if (!silent) setLoading(true);
    try {
      const result = await fetchMerchantOrdersPage(providerId, 0, ORDER_PAGE_SIZE);
      if (silent) {
        setOrders((current) => {
          const merged = mergeNewestPage(current, result.content);
          setHasMore(merged.length < result.totalElements);
          return merged;
        });
      } else {
        setOrders(result.content);
        setNextPage(1);
        setHasMore(result.hasNext);
      }
      setTotalElements(result.totalElements);
      setError(null);
    } catch (loadError) {
      setError(loadError);
      if (!silent) {
        setOrders([]);
        setTotalElements(0);
        setHasMore(false);
      }
    } finally {
      if (!silent) setLoading(false);
    }
  }, [providerId]);

  const loadMore = useCallback(async () => {
    if (!providerId || !hasMore || loadingMore) return;
    setLoadingMore(true);
    try {
      const result = await fetchMerchantOrdersPage(providerId, nextPage, ORDER_PAGE_SIZE);
      setOrders((current) => {
        const existingIds = new Set(current.map((order) => order.orderId));
        return [
          ...current,
          ...result.content.filter((order) => !existingIds.has(order.orderId)),
        ];
      });
      setNextPage((value) => value + 1);
      setTotalElements(result.totalElements);
      setHasMore(result.hasNext);
      setError(null);
    } catch (loadError) {
      setError(loadError);
    } finally {
      setLoadingMore(false);
    }
  }, [hasMore, loadingMore, nextPage, providerId]);

  useEffect(() => {
    void loadLatest();
    if (!providerId) return undefined;
    const interval = setInterval(() => void loadLatest(true), 10_000);
    return () => clearInterval(interval);
  }, [loadLatest, providerId]);

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
      return [
        order.orderId,
        order.customerId,
        order.paymentMethod,
        order.paymentStatus,
        order.couponCode ?? '',
        ...order.items.map((item) => item.name),
      ].some((value) => value.toLowerCase().includes(normalizedQuery));
    });
  }, [filter, orders, query]);

  const applyTransition = useCallback(async () => {
    if (!pending) return;
    setRefreshingOrderId(pending.order.orderId);
    try {
      await transitionMerchantOrder(
        pending.order.orderId,
        pending.action.status,
        note,
      );
      await loadLatest(true);
      setPending(null);
      setNote('');
    } catch (transitionError) {
      setError(transitionError);
      if (apiErrorKind(transitionError) === 'conflict') void loadLatest(true);
    } finally {
      setRefreshingOrderId(null);
    }
  }, [loadLatest, note, pending]);

  const errorTrace = error instanceof ApiError && error.traceId ? ` Reference: ${error.traceId}.` : '';

  return (
    <ScreenShell
      header={
        <AppBar
          eyebrow="MERCHANT WORKSPACE"
          title="Orders"
          subtitle="Server-authoritative items, payment, pricing and canonical fulfilment state"
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

          {totalElements > 0 ? (
            <FeedbackBanner
              tone="info"
              title="Bounded live order queue"
              message={`Loaded ${orders.length} of ${totalElements} orders. The newest ${ORDER_PAGE_SIZE} are refreshed every 10 seconds; older history loads only when requested. Queue counts and search apply to loaded orders.`}
              icon="history"
            />
          ) : null}

          <View style={[styles.search, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
            <AppIcon name="search" color={theme.textSecondary} size={18} />
            <TextInput
              value={query}
              onChangeText={setQuery}
              placeholder="Search loaded order, customer, coupon or item"
              placeholderTextColor={theme.textSecondary}
              style={[styles.searchInput, { color: theme.text }]}
              accessibilityLabel="Search loaded merchant orders"
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
              title={query ? 'No matching loaded orders' : 'No orders in this loaded queue'}
              message={query
                ? 'Try another order, customer, coupon or product, or load older history.'
                : hasMore
                  ? 'Load older history to continue checking this canonical queue.'
                  : filter === 'NEW'
                    ? 'COD orders and paid online orders waiting for your decision will appear here.'
                    : 'Orders will move here only through the canonical lifecycle.'}
              actionLabel={query ? 'Clear search' : hasMore ? 'Load older orders' : 'Refresh'}
              onAction={query ? () => setQuery('') : hasMore ? () => void loadMore() : () => void loadLatest()}
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
                        <ThemedText style={styles.orderTitle}>Order #{compact(order.orderId)}</ThemedText>
                        <ThemedText type="small" themeColor="textSecondary">
                          {formatDateTime(order.placedAt)} · Customer {compact(order.customerId)}
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
                      <View style={styles.summaryCell}>
                        <ThemedText type="small" themeColor="textSecondary">Delivery contact</ThemedText>
                        <ThemedText type="smallBold">
                          {order.deliveryContactPhone ?? 'Not available'}{order.deliveryContactVerified ? ' · verified' : ''}
                        </ThemedText>
                      </View>
                    </View>

                    <View style={styles.detailSection}>
                      <ThemedText type="smallBold">Items</ThemedText>
                      {order.items.length === 0 ? (
                        <FeedbackBanner tone="danger" title="Order items missing" message="The server returned this order without item snapshots. Do not fulfil it until the order data is reconciled." />
                      ) : order.items.map((item) => (
                        <View key={`${order.orderId}-${item.offeringId}`} style={styles.itemRow}>
                          <View style={styles.flex}>
                            <ThemedText type="smallBold">{item.name}</ThemedText>
                            <ThemedText type="small" themeColor="textSecondary">
                              {item.quantity} × {formatCurrency(item.unitPrice)}
                            </ThemedText>
                          </View>
                          <ThemedText type="smallBold">{formatCurrency(item.lineTotal)}</ThemedText>
                        </View>
                      ))}
                    </View>

                    <View style={[styles.breakdown, { borderColor: theme.border }]}>
                      <View style={styles.breakdownRow}><ThemedText type="small" themeColor="textSecondary">Subtotal</ThemedText><ThemedText type="small">{formatCurrency(order.subtotalAmount)}</ThemedText></View>
                      {order.discountAmount > 0 ? <View style={styles.breakdownRow}><ThemedText type="small" themeColor="textSecondary">Discount{order.couponCode ? ` (${order.couponCode})` : ''}</ThemedText><ThemedText type="small">−{formatCurrency(order.discountAmount)}</ThemedText></View> : null}
                      <View style={styles.breakdownRow}><ThemedText type="small" themeColor="textSecondary">Delivery fee</ThemedText><ThemedText type="small">{formatCurrency(order.deliveryFee)}</ThemedText></View>
                      <View style={styles.breakdownRow}><ThemedText type="small" themeColor="textSecondary">Tax</ThemedText><ThemedText type="small">{formatCurrency(order.taxAmount)}</ThemedText></View>
                      <View style={styles.breakdownRow}><ThemedText type="smallBold">Authoritative total</ThemedText><ThemedText type="smallBold">{formatCurrency(order.totalAmount)}</ThemedText></View>
                    </View>

                    <ThemedText type="small" themeColor="textSecondary">
                      Delivery address reference: {compact(order.deliveryAddressId)}
                      {order.acceptedAt ? ` · Accepted ${formatDateTime(order.acceptedAt)}` : ''}
                      {order.readyAt ? ` · Ready ${formatDateTime(order.readyAt)}` : ''}
                      {order.pickedUpAt ? ` · Picked up ${formatDateTime(order.pickedUpAt)}` : ''}
                      {order.deliveredAt ? ` · Delivered ${formatDateTime(order.deliveredAt)}` : ''}
                    </ThemedText>

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

          {hasMore ? (
            <ActionButton
              label={loadingMore ? 'Loading older orders…' : `Load older orders (${orders.length}/${totalElements})`}
              variant="secondary"
              icon="history"
              loading={loadingMore}
              disabled={loadingMore}
              onPress={() => void loadMore()}
            />
          ) : null}
        </>
      ) : null}

      <Modal visible={pending !== null} transparent animationType="fade" onRequestClose={() => setPending(null)}>
        <View style={styles.modalBackdrop}>
          <View style={[styles.modal, shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]} accessibilityViewIsModal>
            <ThemedText type="title">{pending?.action.label}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              Order #{pending ? compact(pending.order.orderId) : ''}. The server validates this exact transition and actor.
            </ThemedText>
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
  detailSection: { gap: spacing.x2 },
  itemRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.x3 },
  breakdown: { borderTopWidth: StyleSheet.hairlineWidth, paddingTop: spacing.x2, gap: spacing.x1 },
  breakdownRow: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing.x3 },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  action: { flexGrow: 1, flexBasis: 160 },
  modalBackdrop: { flex: 1, backgroundColor: 'rgba(11,28,48,0.58)', padding: spacing.x4, alignItems: 'center', justifyContent: 'center' },
  modal: { width: '100%', maxWidth: 560, borderWidth: StyleSheet.hairlineWidth, borderRadius: radii.feature, padding: spacing.x5, gap: spacing.x4 },
  note: { minHeight: 112, borderWidth: 1, borderRadius: radii.compact, padding: spacing.x3, textAlignVertical: 'top', ...typography.body },
  modalActions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
});
