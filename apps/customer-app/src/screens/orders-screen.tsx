import React, { useState } from 'react';
import { Alert, Modal, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { AppIcon } from '@/components/app-icon';
import { AppBar, FilterChip, PrimaryAction, StateView, StatusBadge } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { useAuthIntent } from '@/context/AuthIntentContext';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useOrders } from '@/hooks/use-orders';
import { useTheme } from '@/hooks/use-theme';
import { useTranslation } from '@/i18n';
import type { CustomerOrderRecord } from '@/services/customer-orders';

export default function OrdersScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const router = useRouter();
  const { requireAuth } = useAuthIntent();

  const {
    user,
    session,
    filteredOrders,
    state,
    activeTab,
    setActiveTab,
    searchQuery,
    setSearchQuery,
    actionLoading,
    reload,
    cancel,
    reorder,
  } = useOrders();

  const [selectedOrderForCancel, setSelectedOrderForCancel] = useState<CustomerOrderRecord | null>(null);
  const [cancelReason, setCancelReason] = useState('');

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

  const handleCancelSubmit = async () => {
    if (!selectedOrderForCancel) return;
    try {
      await cancel(selectedOrderForCancel.id, cancelReason || 'Cancelled by customer');
      setSelectedOrderForCancel(null);
      setCancelReason('');
      Alert.alert(t('common.success'), 'Order cancelled successfully.');
    } catch (err: any) {
      Alert.alert(t('common.error'), err.message || 'Could not cancel order.');
    }
  };

  const handleReorder = async (orderId: string) => {
    try {
      const result = await reorder(orderId);
      if (result && result.canReorder) {
        Alert.alert(
          'Reorder Validated',
          'All items are available at current prices. Proceed to cart?',
          [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Go to Cart', onPress: () => router.push('/cart' as any) },
          ],
        );
      } else if (result) {
        const unavailable = result.items.filter((i) => !i.isAvailable).map((i) => `${i.offeringName}: ${i.message}`).join('\n');
        Alert.alert('Reorder Unavailable', `Some items cannot be reordered:\n${unavailable}`);
      }
    } catch (err: any) {
      Alert.alert(t('common.error'), err.message || 'Could not revalidate reorder.');
    }
  };

  return (
    <ScreenShell
      header={<AppBar title={t('ordersFoundation.title')} subtitle={t('ordersFoundation.subtitle')} />}
      testID="orders-screen"
    >
      {/* 4-Tab / Segmented Navigation Header */}
      <View style={styles.tabsContainer}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.tabsScroll}>
          <FilterChip
            label="Active"
            selected={activeTab === 'active'}
            onPress={() => setActiveTab('active')}
          />
          <FilterChip
            label="Past"
            selected={activeTab === 'past'}
            onPress={() => setActiveTab('past')}
          />
          <FilterChip
            label="Subscriptions"
            selected={activeTab === 'subscription'}
            onPress={() => setActiveTab('subscription')}
          />
        </ScrollView>

        {/* Search Field inside screen */}
        <View style={[styles.searchBox, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <AppIcon name="search" size={16} color={theme.textSecondary} />
          <TextInput
            value={searchQuery}
            onChangeText={setSearchQuery}
            placeholder="Search orders by item or store..."
            placeholderTextColor={theme.textSecondary}
            style={[styles.searchInput, { color: theme.text }]}
          />
          {searchQuery ? (
            <Pressable onPress={() => setSearchQuery('')}>
              <ThemedText style={{ color: theme.textSecondary, fontWeight: '700' }}>✕</ThemedText>
            </Pressable>
          ) : null}
        </View>
      </View>

      {/* Screen States */}
      {state === 'loading' || state === 'idle' ? (
        <StateView kind="loading" title={t('states.loading')} message={t('states.loadingMessage')} />
      ) : null}
      {state === 'offline' ? (
        <StateView kind="offline" title={t('states.offline')} message={t('states.offlineMessage')} actionLabel={t('states.retry')} onAction={() => void reload()} />
      ) : null}
      {state === 'error' ? (
        <StateView kind="error" title={t('states.error')} message={t('ordersFoundation.loadError')} actionLabel={t('states.retry')} onAction={() => void reload()} />
      ) : null}
      {state === 'ready' && filteredOrders.length === 0 ? (
        <StateView kind="empty" title={t('ordersFoundation.emptyTitle')} message={t('ordersFoundation.emptyMessage')} />
      ) : null}

      {/* Order List */}
      {state === 'ready' && filteredOrders.length > 0 ? (
        <View style={styles.list}>
          {filteredOrders.map((order) => {
            const isCancellable = ['PLACED', 'ACCEPTED'].includes(order.status);
            const isPast = ['DELIVERED', 'COMPLETED', 'CANCELLED'].includes(order.status);

            return (
              <View
                key={order.id}
                style={[
                  styles.orderCard,
                  shadows.card,
                  { backgroundColor: theme.backgroundElement, borderColor: theme.border },
                ]}
              >
                {/* Header row */}
                <View style={styles.cardHeader}>
                  <View style={styles.flex}>
                    <ThemedText style={styles.storeName}>{order.providerName}</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      Order #{order.id.slice(0, 8)} · {new Date(order.orderedAt).toLocaleDateString()}
                    </ThemedText>
                  </View>
                  <StatusBadge
                    label={order.status}
                    tone={
                      order.status === 'DELIVERED' || order.status === 'COMPLETED'
                        ? 'success'
                        : order.status === 'CANCELLED'
                        ? 'error'
                        : 'warning'
                    }
                  />
                </View>

                {/* Items summary */}
                <ThemedText style={styles.itemsSummary} numberOfLines={2}>
                  {order.items.join(' · ')}
                </ThemedText>

                {/* Amount */}
                <View style={styles.amountRow}>
                  <ThemedText type="small" themeColor="textSecondary">
                    Total Amount
                  </ThemedText>
                  <ThemedText style={styles.amountText}>{order.total}</ThemedText>
                </View>

                {/* Action Row */}
                <View style={styles.actionRow}>
                  <Pressable
                    style={[styles.outlineBtn, { borderColor: theme.primary }]}
                    onPress={() => router.push(`/orders/${order.id}` as any)}
                  >
                    <AppIcon name="location" size={14} color={theme.primary} />
                    <ThemedText type="small" style={{ color: theme.primary, fontWeight: '700' }}>
                      Track Order
                    </ThemedText>
                  </Pressable>

                  {isCancellable ? (
                    <Pressable
                      style={[styles.outlineBtn, { borderColor: theme.danger }]}
                      onPress={() => setSelectedOrderForCancel(order)}
                    >
                      <ThemedText type="small" style={{ color: theme.danger, fontWeight: '700' }}>
                        Cancel
                      </ThemedText>
                    </Pressable>
                  ) : null}

                  {isPast ? (
                    <Pressable
                      style={[styles.solidBtn, { backgroundColor: theme.primarySoft }]}
                      onPress={() => void handleReorder(order.id)}
                    >
                      <AppIcon name="cart" size={14} color={theme.primary} />
                      <ThemedText type="small" style={{ color: theme.primary, fontWeight: '700' }}>
                        Reorder
                      </ThemedText>
                    </Pressable>
                  ) : null}
                </View>
              </View>
            );
          })}
        </View>
      ) : null}

      {/* Cancellation Reason Modal */}
      <Modal visible={Boolean(selectedOrderForCancel)} transparent animationType="slide">
        <View style={styles.modalOverlay}>
          <View style={[styles.modalBox, { backgroundColor: theme.backgroundElement }]}>
            <ThemedText type="title">Cancel Order</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              Are you sure you want to cancel order #{selectedOrderForCancel?.id.slice(0, 8)}?
            </ThemedText>

            <TextInput
              value={cancelReason}
              onChangeText={setCancelReason}
              placeholder="Reason for cancellation (optional)..."
              placeholderTextColor={theme.textSecondary}
              style={[styles.reasonInput, { color: theme.text, borderColor: theme.border }]}
              multiline
            />

            <View style={styles.modalActions}>
              <Pressable style={styles.cancelModalBtn} onPress={() => setSelectedOrderForCancel(null)}>
                <ThemedText style={{ color: theme.textSecondary }}>Keep Order</ThemedText>
              </Pressable>
              <PrimaryAction
                label="Confirm Cancel"
                onPress={() => void handleCancelSubmit()}
                loading={actionLoading}
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
  tabsContainer: { paddingHorizontal: spacing.x4, gap: spacing.x3, marginBottom: spacing.x3 },
  tabsScroll: { flexDirection: 'row', gap: spacing.x2 },
  searchBox: {
    height: 40,
    borderWidth: 1,
    borderRadius: radii.compact,
    paddingHorizontal: spacing.x3,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.x2,
  },
  searchInput: { flex: 1, ...typography.body, paddingVertical: 0 },
  list: { paddingHorizontal: spacing.x4, gap: spacing.x3, paddingBottom: spacing.x6 },
  orderCard: {
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: radii.card,
    padding: spacing.x4,
    gap: spacing.x3,
  },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  storeName: { ...typography.label },
  itemsSummary: { ...typography.body },
  amountRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  amountText: { ...typography.label, fontWeight: '700' },
  actionRow: { flexDirection: 'row', gap: spacing.x2, justifyContent: 'flex-end', marginTop: spacing.x1 },
  outlineBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.x1,
    paddingHorizontal: spacing.x3,
    paddingVertical: spacing.x2,
    borderWidth: 1,
    borderRadius: radii.compact,
  },
  solidBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.x1,
    paddingHorizontal: spacing.x3,
    paddingVertical: spacing.x2,
    borderRadius: radii.compact,
  },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', padding: spacing.x4 },
  modalBox: { borderRadius: radii.card, padding: spacing.x6, gap: spacing.x3 },
  reasonInput: { borderWidth: 1, borderRadius: radii.compact, padding: spacing.x3, height: 72, textAlignVertical: 'top' },
  modalActions: { flexDirection: 'row', justifyContent: 'flex-end', alignItems: 'center', gap: spacing.x3 },
  cancelModalBtn: { padding: spacing.x3 },
});
