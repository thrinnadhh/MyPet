import React, { useEffect, useState } from 'react';
import { Alert, Pressable, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';

import { AppIcon } from '@/components/app-icon';
import { AppBar, PrimaryAction, StateView, StatusBadge } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { OrderFlowTracker } from '@/components/order-flow-tracker';
import { ThemedText } from '@/components/themed-text';
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

export default function OrderDetailRoute() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { t } = useTranslation();
  const theme = useTheme();
  const router = useRouter();
  const { session } = useAuth();

  const [order, setOrder] = useState<CustomerOrderRecord | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  useEffect(() => {
    if (!id || !session) return;
    setLoading(true);
    fetchOrderDetails(id, session.access_token)
      .then((data) => {
        setOrder(data);
        setError(null);
      })
      .catch((err) => {
        setError(err.message || 'Could not load order details');
      })
      .finally(() => setLoading(false));
  }, [id, session]);

  const handleCancel = async () => {
    if (!order || !session) return;
    setActionLoading(true);
    try {
      await cancelOrder(order.id, 'Cancelled from order detail', session.access_token);
      Alert.alert(t('common.success'), 'Order cancelled successfully.');
      router.back();
    } catch (err: any) {
      Alert.alert(t('common.error'), err.message || 'Could not cancel order.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleReorder = async () => {
    if (!order || !session) return;
    setActionLoading(true);
    try {
      const result = await reorderItems(order.id, session.access_token);
      if (result.canReorder) {
        Alert.alert('Reorder Ready', 'All items revalidated. Proceed to cart?', [
          { text: 'Cancel', style: 'cancel' },
          { text: 'Cart', onPress: () => router.push('/cart' as any) },
        ]);
      } else {
        Alert.alert('Reorder Warning', 'Some items are unavailable or out of stock.');
      }
    } catch (err: any) {
      Alert.alert(t('common.error'), err.message || 'Could not revalidate reorder.');
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <ScreenShell
      header={
        <AppBar
          title={`Order #${id?.slice(0, 8)}`}
          subtitle="Live Order Tracking & Details"
          action={
            <Pressable onPress={() => router.back()} style={styles.backBtn}>
              <ThemedText style={{ color: theme.text, fontWeight: '700' }}>✕</ThemedText>
            </Pressable>
          }
        />
      }
    >
      {loading ? (
        <StateView kind="loading" title={t('states.loading')} message={t('states.loadingMessage')} />
      ) : error || !order ? (
        <StateView
          kind="error"
          title={t('states.error')}
          message={error || 'Order not found'}
          actionLabel={t('common.back')}
          onAction={() => router.back()}
        />
      ) : (
        <View style={styles.container}>
          {/* Order Header & Status Badge */}
          <View
            style={[
              styles.card,
              shadows.card,
              { backgroundColor: theme.backgroundElement, borderColor: theme.border },
            ]}
          >
            <View style={styles.headerRow}>
              <View style={styles.flex}>
                <ThemedText style={styles.storeName}>{order.providerName}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  Placed on {new Date(order.orderedAt).toLocaleString()}
                </ThemedText>
              </View>
              <StatusBadge label={order.status} tone={order.status === 'DELIVERED' ? 'success' : 'warning'} />
            </View>

            {/* Live Tracking Timeline */}
            <ThemedText style={styles.sectionTitle}>Live Tracking</ThemedText>
            <View style={[styles.trackerBox, { backgroundColor: theme.primarySoft }]}>
              <OrderFlowTracker currentStep={order.flowStep} />
            </View>
          </View>

          {/* Items Summary */}
          <View
            style={[
              styles.card,
              shadows.card,
              { backgroundColor: theme.backgroundElement, borderColor: theme.border },
            ]}
          >
            <ThemedText style={styles.sectionTitle}>Order Items</ThemedText>
            {order.items.map((item, idx) => (
              <View key={idx} style={styles.itemRow}>
                <AppIcon name="paw" size={16} color={theme.primary} />
                <ThemedText style={styles.itemText}>{item}</ThemedText>
              </View>
            ))}

            <View style={[styles.divider, { backgroundColor: theme.border }]} />

            <View style={styles.priceRow}>
              <ThemedText style={styles.totalLabel}>Total Paid</ThemedText>
              <ThemedText style={styles.totalValue}>{order.total}</ThemedText>
            </View>
          </View>

          {/* Actions */}
          <View style={styles.actions}>
            {['PLACED', 'ACCEPTED'].includes(order.status) ? (
              <Pressable style={[styles.cancelBtn, { borderColor: theme.danger }]} onPress={() => void handleCancel()}>
                <ThemedText style={{ color: theme.danger, fontWeight: '700' }}>Cancel Order</ThemedText>
              </Pressable>
            ) : null}

            {['DELIVERED', 'COMPLETED', 'CANCELLED'].includes(order.status) ? (
              <PrimaryAction label="Reorder Items" onPress={() => void handleReorder()} loading={actionLoading} />
            ) : null}
          </View>
        </View>
      )}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  backBtn: { padding: spacing.x2 },
  container: { padding: spacing.x4, gap: spacing.x4 },
  card: { borderWidth: StyleSheet.hairlineWidth, borderRadius: radii.card, padding: spacing.x4, gap: spacing.x3 },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  storeName: { ...typography.title },
  sectionTitle: { ...typography.label, fontWeight: '700', marginTop: spacing.x1 },
  trackerBox: { padding: spacing.x3, borderRadius: radii.compact },
  itemRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.x2, paddingVertical: spacing.x1 },
  itemText: { ...typography.body },
  divider: { height: StyleSheet.hairlineWidth, marginVertical: spacing.x2 },
  priceRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  totalLabel: { ...typography.label },
  totalValue: { ...typography.title, color: '#10B981' },
  actions: { gap: spacing.x3, marginTop: spacing.x2 },
  cancelBtn: {
    height: 48,
    borderWidth: 1,
    borderRadius: radii.compact,
    alignItems: 'center',
    justifyContent: 'center',
  },
});

