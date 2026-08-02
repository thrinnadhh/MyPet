import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { AppIcon } from '@/components/app-icon';
import { AppBar, PrimaryAction, StateView, StatusBadge } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { OrderFlowTracker } from '@/components/order-flow-tracker';
import { ThemedText } from '@/components/themed-text';
import { activeOrderPollInterval, type CustomerPaymentStatus } from '@/contracts/customer-payment';
import { useAuth } from '@/context/AuthContext';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { useTranslation } from '@/i18n';
import {
  cancelOrder,
  fetchOrderDetails,
  reorderItems,
  type CustomerOrderRecord,
} from '@/services/customer-orders';
import {
  fetchOrderPaymentStatus,
  initiateOrderPayment,
  openRazorpayOrder,
  reconcilePaidOrder,
  waitForPaymentOutcome,
} from '@/services/customer-payments';

export default function OrderDetailRoute() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { t } = useTranslation();
  const theme = useTheme();
  const router = useRouter();
  const { user, session } = useAuth();

  const [order, setOrder] = useState<CustomerOrderRecord | null>(null);
  const [paymentStatus, setPaymentStatus] = useState<CustomerPaymentStatus>('NOT_STARTED');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  const loadOrder = useCallback(async (showLoading = false) => {
    if (!id || !session) return;
    if (showLoading) setLoading(true);
    try {
      let next = await fetchOrderDetails(id, session.access_token);
      if (next.paymentMethod && next.paymentMethod !== 'COD') {
        try {
          const payment = next.status === 'PLACED'
            ? await reconcilePaidOrder(id)
            : await fetchOrderPaymentStatus(id);
          setPaymentStatus(payment.status);
          if (payment.status === 'SUCCESS' && next.status === 'PLACED') {
            next = await fetchOrderDetails(id, session.access_token);
          }
        } catch {
          setPaymentStatus('NOT_STARTED');
        }
      } else {
        setPaymentStatus(next.paymentStatus === 'COD_PENDING' ? 'PENDING' : 'NOT_STARTED');
      }
      setOrder(next);
      setError(null);
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : 'Could not load order details');
    } finally {
      if (showLoading) setLoading(false);
    }
  }, [id, session]);

  useEffect(() => {
    void loadOrder(true);
  }, [loadOrder]);

  useEffect(() => {
    if (!order) return;
    const interval = activeOrderPollInterval(order.status);
    if (!interval) return;
    const timer = setInterval(() => void loadOrder(false), interval);
    return () => clearInterval(timer);
  }, [loadOrder, order]);

  const handleCancel = async () => {
    if (!order || !session) return;
    setActionLoading(true);
    try {
      await cancelOrder(order.id, 'Cancelled from customer order detail', session.access_token);
      await loadOrder(false);
      Alert.alert(t('common.success'), 'Order cancelled successfully.');
    } catch (cause: unknown) {
      Alert.alert(t('common.error'), cause instanceof Error ? cause.message : 'Could not cancel order.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleReorder = async () => {
    if (!order || !session) return;
    setActionLoading(true);
    try {
      const result = await reorderItems(order.id, session.access_token);
      Alert.alert(
        result.canReorder ? 'Reorder ready' : 'Reorder unavailable',
        result.canReorder
          ? 'All items are currently available. Add them from the provider catalog to continue.'
          : 'One or more items are unavailable or outside the service area.',
      );
    } catch (cause: unknown) {
      Alert.alert(t('common.error'), cause instanceof Error ? cause.message : 'Could not revalidate reorder.');
    } finally {
      setActionLoading(false);
    }
  };

  const handlePayment = async () => {
    if (!order || !user) return;
    setActionLoading(true);
    try {
      const initialization = await initiateOrderPayment(user.id, order.id, order.rawTotal);
      await openRazorpayOrder(initialization);
      const payment = await waitForPaymentOutcome(order.id);
      setPaymentStatus(payment.status);
      if (payment.status === 'SUCCESS') {
        await loadOrder(false);
        Alert.alert('Payment confirmed', 'The server verified your payment and confirmed the order.');
      } else if (payment.status === 'PENDING') {
        Alert.alert('Confirmation pending', 'Razorpay webhook confirmation is still pending. This screen will keep checking.');
      } else {
        Alert.alert('Payment not completed', 'No successful payment was confirmed. You can retry safely.');
      }
    } catch (cause: unknown) {
      Alert.alert('Payment unavailable', cause instanceof Error ? cause.message : 'Could not open secure payment.');
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <ScreenShell
      header={
        <AppBar
          title={`Order #${id?.slice(0, 8)}`}
          subtitle="Live server status"
          action={
            <Pressable onPress={() => router.back()} style={styles.backButton}>
              <ThemedText style={{ color: theme.text, fontWeight: '700' }}>✕</ThemedText>
            </Pressable>
          }
        />
      }
    >
      {loading ? (
        <StateView kind="loading" title={t('states.loading')} message="Loading order and payment state…" />
      ) : error || !order ? (
        <StateView
          kind="error"
          title={t('states.error')}
          message={error || 'Order not found'}
          actionLabel={t('states.retry')}
          onAction={() => void loadOrder(true)}
        />
      ) : (
        <View style={styles.container}>
          <View style={[styles.card, shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
            <View style={styles.headerRow}>
              <View style={styles.flex}>
                <ThemedText style={styles.storeName}>{order.providerName}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  Placed {new Date(order.orderedAt).toLocaleString()}
                </ThemedText>
              </View>
              <StatusBadge label={order.status} tone={order.status === 'DELIVERED' ? 'success' : 'warning'} />
            </View>
            <ThemedText style={styles.sectionTitle}>Order progress</ThemedText>
            <View style={[styles.trackerBox, { backgroundColor: theme.primarySoft }]}>
              <OrderFlowTracker currentStep={order.flowStep} />
            </View>
            {activeOrderPollInterval(order.status) ? (
              <ThemedText type="small" themeColor="textSecondary">
                Refreshing automatically every 8 seconds.
              </ThemedText>
            ) : null}
          </View>

          {order.paymentMethod && order.paymentMethod !== 'COD' ? (
            <View style={[styles.card, shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
              <View style={styles.headerRow}>
                <ThemedText style={styles.sectionTitle}>Online payment</ThemedText>
                <StatusBadge
                  label={paymentStatus}
                  tone={paymentStatus === 'SUCCESS' ? 'success' : 'warning'}
                />
              </View>
              <ThemedText type="small" themeColor="textSecondary">
                Method: {order.paymentMethod}. Only webhook-confirmed success can advance this order.
              </ThemedText>
              {order.status === 'PLACED' && paymentStatus !== 'SUCCESS' ? (
                <PrimaryAction
                  label={paymentStatus === 'PENDING' ? 'Open payment / check again' : 'Pay securely'}
                  onPress={() => void handlePayment()}
                  loading={actionLoading}
                />
              ) : null}
            </View>
          ) : null}

          <View style={[styles.card, shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
            <ThemedText style={styles.sectionTitle}>Order items</ThemedText>
            {order.items.map((item, index) => (
              <View key={`${item}-${index}`} style={styles.itemRow}>
                <AppIcon name="paw" size={16} color={theme.primary} />
                <ThemedText style={styles.itemText}>{item}</ThemedText>
              </View>
            ))}
            <View style={[styles.divider, { backgroundColor: theme.border }]} />
            <View style={styles.headerRow}>
              <ThemedText style={styles.totalLabel}>Order total</ThemedText>
              <ThemedText style={[styles.totalValue, { color: theme.primary }]}>{order.total}</ThemedText>
            </View>
          </View>

          {order.statusHistory && order.statusHistory.length > 0 ? (
            <View style={[styles.card, shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
              <ThemedText style={styles.sectionTitle}>Status history</ThemedText>
              {order.statusHistory.map((entry, index) => (
                <View key={`${entry.toStatus}-${entry.changedAt}-${index}`} style={styles.historyRow}>
                  <ThemedText style={{ fontWeight: '700' }}>{entry.toStatus}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {new Date(entry.changedAt).toLocaleString()}{entry.note ? ` · ${entry.note}` : ''}
                  </ThemedText>
                </View>
              ))}
            </View>
          ) : null}

          <View style={styles.actions}>
            {['PLACED', 'ACCEPTED'].includes(order.status) ? (
              <Pressable style={[styles.cancelButton, { borderColor: theme.danger }]} onPress={() => void handleCancel()}>
                <ThemedText style={{ color: theme.danger, fontWeight: '700' }}>Cancel order</ThemedText>
              </Pressable>
            ) : null}
            {['DELIVERED', 'COMPLETED', 'CANCELLED'].includes(order.status) ? (
              <PrimaryAction label="Revalidate reorder" onPress={() => void handleReorder()} loading={actionLoading} />
            ) : null}
          </View>
        </View>
      )}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  backButton: { padding: spacing.x2 },
  container: { padding: spacing.x4, gap: spacing.x4, paddingBottom: spacing.x8 },
  card: { borderWidth: StyleSheet.hairlineWidth, borderRadius: radii.card, padding: spacing.x4, gap: spacing.x3 },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: spacing.x2 },
  storeName: { ...typography.title },
  sectionTitle: { ...typography.label, fontWeight: '700' },
  trackerBox: { padding: spacing.x3, borderRadius: radii.compact },
  itemRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.x2, paddingVertical: spacing.x1 },
  itemText: { ...typography.body, flex: 1 },
  divider: { height: StyleSheet.hairlineWidth, marginVertical: spacing.x1 },
  totalLabel: { ...typography.label },
  totalValue: { ...typography.title, fontWeight: '800' },
  historyRow: { gap: spacing.x1, paddingVertical: spacing.x1 },
  actions: { gap: spacing.x3 },
  cancelButton: { height: 48, borderWidth: 1, borderRadius: radii.compact, alignItems: 'center', justifyContent: 'center' },
});
