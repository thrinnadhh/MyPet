import { useLocalSearchParams, useRouter } from 'expo-router';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { StyleSheet, TextInput, View } from 'react-native';

import {
  ActionButton,
  AppBar,
  FeedbackBanner,
  RoleBadge,
  SectionHeader,
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
  fetchMerchantOrderDetail,
  isMerchantOrderInQueue,
  merchantOrderActions,
  transitionMerchantOrder,
  type MerchantOrderActionDefinition,
  type MerchantOrderDetail,
  type MerchantOrderStatus,
} from '@/services/merchant-orders';
import { formatCurrency, formatDateTime, formatOrderStatus } from '@/utils/formatters';

function statusTone(status: MerchantOrderStatus): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
  if (['DELIVERED', 'COMPLETED'].includes(status)) return 'success';
  if (['REJECTED', 'CANCELLED'].includes(status)) return 'danger';
  if (['ASSIGNED', 'PICKED_UP'].includes(status)) return 'info';
  if (['PLACED', 'ACCEPTED', 'PREPARING', 'READY_FOR_PICKUP'].includes(status)) return 'warning';
  return 'neutral';
}

function addressText(order: MerchantOrderDetail): string {
  const address = order.deliveryAddress;
  return [address.label, address.line1, address.line2, address.city, address.state, address.pincode]
    .filter((value): value is string => Boolean(value?.trim()))
    .join(', ');
}

export default function MerchantOrderDetailScreen() {
  const params = useLocalSearchParams<{ id?: string | string[] }>();
  const router = useRouter();
  const theme = useTheme();
  const orderId = useMemo(() => {
    const raw = Array.isArray(params.id) ? params.id[0] : params.id;
    return raw ? decodeURIComponent(raw) : '';
  }, [params.id]);
  const [order, setOrder] = useState<MerchantOrderDetail | null>(null);
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
      setOrder(await fetchMerchantOrderDetail(orderId));
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
      await transitionMerchantOrder(order.orderId, action.status, note);
      setNote('');
      await load();
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
          subtitle="Customer, fulfilment, payment and SLA history from the server"
          action={<RoleBadge role="merchant" />}
        />
      }
      testID="merchant-order-detail"
    >
      <ActionButton label="Back to orders" variant="ghost" onPress={() => router.replace('/orders' as never)} />

      {loading ? <StateView kind="loading" title="Loading order" message="Fetching the current operational snapshot…" /> : null}

      {!loading && error ? (
        <FeedbackBanner
          tone="danger"
          title={apiErrorKind(error) === 'conflict' ? 'Order changed on the server' : 'Order action failed'}
          message={apiErrorMessage(error, 'Could not load or update this order.')}
          icon="dispute"
        />
      ) : null}

      {!loading && order ? (
        <View style={styles.page}>
          <AppCard style={styles.card}>
            <View style={styles.headerRow}>
              <View style={styles.flex}>
                <ThemedText style={styles.title}>Order #{order.orderId.toUpperCase()}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">Placed {formatDateTime(order.placedAt)}</ThemedText>
              </View>
              <StatusBadge label={formatOrderStatus(order.status)} tone={statusTone(order.status)} />
            </View>

            <View style={[styles.summary, { backgroundColor: theme.muted }]}>
              <InfoCell label="Customer" value={order.customerName?.trim() || order.customerId.slice(0, 8).toUpperCase()} />
              <InfoCell label="Contact" value={order.contactPhone || 'Not available'} supporting={order.contactVerified ? 'Verified delivery contact' : 'Customer delivery contact'} />
              <InfoCell label="Payment" value={`${order.paymentMethod} · ${formatOrderStatus(order.paymentStatus)}`} />
              <InfoCell label="Status" value={formatOrderStatus(order.status)} />
            </View>
          </AppCard>

          <AppCard style={styles.card}>
            <SectionHeader title="Delivery address" subtitle="Customer-owned checkout address" />
            <ThemedText>{addressText(order)}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">Address ID: {order.deliveryAddressId}</ThemedText>
          </AppCard>

          <AppCard style={styles.card}>
            <SectionHeader title={`Items (${order.items.length})`} subtitle="Authoritative order-item snapshots" />
            {order.items.map((item) => (
              <View key={item.orderItemId} style={[styles.itemRow, { borderBottomColor: theme.border }]}>
                <View style={styles.flex}>
                  <ThemedText type="smallBold">{item.name}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">{formatCurrency(item.unitPrice)} × {item.quantity}</ThemedText>
                </View>
                <ThemedText type="smallBold">{formatCurrency(item.lineTotal)}</ThemedText>
              </View>
            ))}
          </AppCard>

          <AppCard style={styles.card}>
            <SectionHeader title="Price breakdown" subtitle="Same server-owned checkout amounts used for payment" />
            <MoneyRow label="Subtotal" value={order.subtotal} />
            <MoneyRow label="Discount" value={-Math.abs(order.discount)} />
            <MoneyRow label="Delivery" value={order.delivery} />
            <MoneyRow label="Tax" value={order.tax} />
            <View style={[styles.totalRow, { borderTopColor: theme.border }]}>
              <ThemedText style={styles.totalLabel}>Total</ThemedText>
              <ThemedText style={styles.amount}>{formatCurrency(order.total)}</ThemedText>
            </View>
          </AppCard>

          <AppCard style={styles.card}>
            <SectionHeader title="Merchant SLA" subtitle="Server-recorded operational timestamps" />
            <TimelineRow label="Placed" value={order.placedAt} />
            <TimelineRow label="Accepted" value={order.acceptedAt} />
            <TimelineRow label="Preparing" value={order.preparingAt} />
            <TimelineRow label="Ready for pickup" value={order.readyAt} />
          </AppCard>

          <AppCard style={styles.card}>
            <SectionHeader title="Status history" subtitle="Every lifecycle change with actor and note" />
            {order.history.length === 0 ? (
              <ThemedText type="small" themeColor="textSecondary">No lifecycle history was recorded.</ThemedText>
            ) : order.history.map((entry, index) => (
              <View key={`${entry.toStatus}-${entry.changedAt}-${index}`} style={[styles.historyRow, { borderBottomColor: theme.border }]}>
                <View style={styles.flex}>
                  <ThemedText type="smallBold">{entry.fromStatus ? `${formatOrderStatus(entry.fromStatus)} → ` : ''}{formatOrderStatus(entry.toStatus)}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">{formatDateTime(entry.changedAt)}</ThemedText>
                  {entry.note ? <ThemedText type="small">{entry.note}</ThemedText> : null}
                </View>
                <ThemedText type="small" themeColor="textSecondary">{entry.actorId ? `Actor ${entry.actorId.slice(0, 8).toUpperCase()}` : 'System'}</ThemedText>
              </View>
            ))}
          </AppCard>

          <AppCard style={styles.card}>
            <SectionHeader title="Operational action" subtitle="Actions are derived only from the canonical server status" />
            {order.status === 'PLACED' && !isMerchantOrderInQueue(order.status, order.paymentStatus, 'NEW') ? (
              <FeedbackBanner
                tone="warning"
                title="Not actionable yet"
                message="Online orders stay PLACED until payment succeeds. Accept and Reject are disabled until the payment is confirmed."
              />
            ) : null}

            {actions.some((action) => action.destructive) ? (
              <TextInput
                value={note}
                onChangeText={setNote}
                placeholder="Reason for reject/cancel (required)"
                placeholderTextColor={theme.textSecondary}
                multiline
                style={[styles.note, { color: theme.text, borderColor: theme.border, backgroundColor: theme.background }]}
                accessibilityLabel="Order action reason"
              />
            ) : (
              <TextInput
                value={note}
                onChangeText={setNote}
                placeholder="Optional internal note"
                placeholderTextColor={theme.textSecondary}
                multiline
                style={[styles.note, { color: theme.text, borderColor: theme.border, backgroundColor: theme.background }]}
                accessibilityLabel="Order action note"
              />
            )}

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
              <ThemedText type="small" themeColor="textSecondary">No merchant transition is available from this lifecycle state.</ThemedText>
            )}
          </AppCard>
        </View>
      ) : null}
    </ScreenShell>
  );
}

function InfoCell({ label, value, supporting }: { label: string; value: string; supporting?: string }) {
  return (
    <View style={styles.summaryCell}>
      <ThemedText type="small" themeColor="textSecondary">{label}</ThemedText>
      <ThemedText type="smallBold">{value}</ThemedText>
      {supporting ? <ThemedText type="small" themeColor="textSecondary">{supporting}</ThemedText> : null}
    </View>
  );
}

function MoneyRow({ label, value }: { label: string; value: number }) {
  return (
    <View style={styles.moneyRow}>
      <ThemedText type="small" themeColor="textSecondary">{label}</ThemedText>
      <ThemedText type="smallBold">{formatCurrency(value)}</ThemedText>
    </View>
  );
}

function TimelineRow({ label, value }: { label: string; value?: string | null }) {
  return (
    <View style={styles.moneyRow}>
      <ThemedText type="smallBold">{label}</ThemedText>
      <ThemedText type="small" themeColor="textSecondary">{value ? formatDateTime(value) : 'Not reached'}</ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  page: { gap: spacing.x3 },
  card: { padding: spacing.x4, gap: spacing.x3 },
  headerRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.x3 },
  title: { ...typography.title, fontSize: 18, lineHeight: 24 },
  summary: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x4, padding: spacing.x3, borderRadius: radii.compact },
  summaryCell: { flexGrow: 1, minWidth: 150, gap: spacing.x1 },
  itemRow: { minHeight: 52, flexDirection: 'row', alignItems: 'center', gap: spacing.x3, borderBottomWidth: StyleSheet.hairlineWidth, paddingVertical: spacing.x2 },
  moneyRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: spacing.x3 },
  totalRow: { borderTopWidth: StyleSheet.hairlineWidth, paddingTop: spacing.x3, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  totalLabel: { ...typography.title, fontSize: 18 },
  amount: { ...typography.title, fontSize: 20, lineHeight: 26 },
  historyRow: { minHeight: 58, flexDirection: 'row', alignItems: 'flex-start', gap: spacing.x3, borderBottomWidth: StyleSheet.hairlineWidth, paddingVertical: spacing.x2 },
  note: { minHeight: 96, borderWidth: 1, borderRadius: radii.compact, padding: spacing.x3, textAlignVertical: 'top', ...typography.body },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  action: { flexGrow: 1, flexBasis: 180 },
});
