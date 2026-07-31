import { useRouter } from 'expo-router';
import React from 'react';
import { Alert, Image, Pressable, RefreshControl, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { ThemedText } from '@/components/themed-text';
import { PrimaryButton } from '@/components/ui/primary-button';
import { ScreenHeader } from '@/components/ui/screen-header';
import { StatusBadge } from '@/components/ui/status-badge';

import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useOrders } from '@/hooks/useOrders';
import { useTheme } from '@/hooks/use-theme';
import { type Order, type OrderStatus, type OrderTab } from '@/services/orders-data';

export default function OrdersScreen() {
  const theme = useTheme();
  const router = useRouter();

  const { tab, setTab, searchQuery, setSearchQuery, orders, loading, refreshing, onRefresh, cancelOrder } = useOrders('active');

  const getStatusColor = (status: OrderStatus) => {
    switch (status) {
      case 'OUT_FOR_DELIVERY':
        return theme.primary;
      case 'DELIVERED':
        return theme.success;
      case 'PREPARING':
      case 'CONFIRMED':
      case 'ORDER_PLACED':
        return theme.warning;
      case 'CANCELLED':
        return theme.error;
      default:
        return theme.primary;
    }
  };

  const handleCancel = (order: Order) => {
    Alert.alert('Cancel Order', `Are you sure you want to cancel order ${order.orderNumber}?`, [
      { text: 'No', style: 'cancel' },
      {
        text: 'Yes, Cancel',
        style: 'destructive',
        onPress: () => cancelOrder(order.id),
      },
    ]);
  };

  const handleReorder = (order: Order) => {
    Alert.alert('Reorder Items', `Added items from ${order.orderNumber} to your cart.`, [
      { text: 'Go to Cart', onPress: () => router.push('/cart' as never) },
      { text: 'OK' },
    ]);
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <ScreenHeader title="My Orders" subtitle="Track deliveries & order history" />

      {/* Search Input */}
      <View style={[styles.searchContainer, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
        <AppIcon name="search" color={theme.textSecondary} size={18} />
        <TextInput
          placeholder="Search by store, product, or order ID..."
          placeholderTextColor={theme.textSecondary}
          value={searchQuery}
          onChangeText={setSearchQuery}
          style={[styles.searchInput, { color: theme.text }]}
        />
        {searchQuery.length > 0 && (
          <Pressable onPress={() => setSearchQuery('')}>
            <ThemedText style={{ color: theme.textSecondary, fontWeight: '700' }}>✕</ThemedText>
          </Pressable>
        )}
      </View>

      {/* Tab Switcher */}
      <View style={styles.tabContainer}>
        {(['active', 'past', 'subscription'] as OrderTab[]).map((t) => {
          const selected = tab === t;
          return (
            <Pressable
              key={t}
              onPress={() => setTab(t)}
              style={[
                styles.tabChip,
                {
                  backgroundColor: selected ? theme.primary : theme.backgroundElement,
                  borderColor: theme.border,
                },
              ]}
            >
              <ThemedText style={[styles.tabLabel, { color: selected ? '#FFFFFF' : theme.text }]}>
                {t === 'active' ? 'Active' : t === 'past' ? 'Past Orders' : 'Subscriptions'}
              </ThemedText>
            </Pressable>
          );
        })}
      </View>

      {/* Orders List */}
      <ScrollView
        contentContainerStyle={styles.listContent}
        showsVerticalScrollIndicator={false}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void onRefresh()} tintColor={theme.primary} />}
      >
        {orders.length === 0 && !loading && (
          <View style={styles.emptyState}>
            <AppIcon name="cart" color={theme.textSecondary} size={48} />

            <ThemedText style={[styles.emptyTitle, { color: theme.text }]}>No Orders Found</ThemedText>
            <ThemedText style={{ fontSize: 13, color: theme.textSecondary, textAlign: 'center' }}>
              {"You don't have any " + tab + " orders matching your filter."}
            </ThemedText>

            <PrimaryButton label="Explore Pet Shop" onPress={() => router.push('/commerce' as never)} />
          </View>
        )}

        {orders.map((order) => (
          <Pressable
            key={order.id}
            onPress={() => router.push(`/orders/${order.id}` as never)}
            style={[styles.orderCard, shadows.raised, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}
          >
            {/* Header: Logo, Store & Status */}
            <View style={styles.cardHeader}>
              <Image source={{ uri: order.providerLogoUrl }} style={styles.providerLogo} resizeMode="cover" />
              <View style={{ flex: 1, gap: 2 }}>
                <ThemedText style={[styles.providerName, { color: theme.text }]}>{order.providerName}</ThemedText>
                <ThemedText style={{ fontSize: 11, color: theme.textSecondary }}>{order.placedAt}</ThemedText>
              </View>
              <StatusBadge label={order.statusText} color={getStatusColor(order.status)} />
            </View>

            {/* Items Summary */}
            <View style={[styles.itemsBox, { backgroundColor: theme.background }]}>
              {order.items.map((item) => (
                <View key={item.id} style={styles.itemRow}>
                  <Image source={{ uri: item.imageUrl }} style={styles.itemThumb} resizeMode="cover" />
                  <View style={{ flex: 1 }}>
                    <ThemedText style={[styles.itemName, { color: theme.text }]} numberOfLines={1}>
                      {item.name}
                    </ThemedText>
                    {item.variant && <ThemedText style={{ fontSize: 11, color: theme.textSecondary }}>{item.variant}</ThemedText>}
                  </View>
                  <ThemedText style={{ fontSize: 13, fontWeight: '700', color: theme.text }}>
                    {item.quantity}x ₹{item.price}
                  </ThemedText>
                </View>
              ))}
            </View>

            {/* Price & Order Footer */}
            <View style={styles.cardFooter}>
              <View>
                <ThemedText style={{ fontSize: 11, color: theme.textSecondary }}>Total Amount ({order.paymentMethod})</ThemedText>
                <ThemedText style={[styles.totalPrice, { color: theme.primary }]}>₹{order.totalAmount}</ThemedText>
              </View>

              {/* Action CTAs */}
              <View style={styles.actionsRow}>
                {order.status === 'OUT_FOR_DELIVERY' || order.status === 'PREPARING' || order.status === 'CONFIRMED' ? (
                  <Pressable
                    onPress={() => router.push(`/orders/${order.id}` as never)}
                    style={[styles.actionBtn, { backgroundColor: theme.primary }]}
                  >
                    <ThemedText style={{ fontSize: 12, fontWeight: '700', color: '#FFFFFF' }}>Live Track</ThemedText>
                  </Pressable>
                ) : null}

                {order.canCancel && (
                  <Pressable
                    onPress={() => handleCancel(order)}
                    style={[styles.actionBtn, { backgroundColor: theme.errorSoft, borderColor: theme.error, borderWidth: 1 }]}
                  >
                    <ThemedText style={{ fontSize: 12, fontWeight: '700', color: theme.error }}>Cancel</ThemedText>
                  </Pressable>
                )}

                {order.canReorder && (
                  <Pressable
                    onPress={() => handleReorder(order)}
                    style={[styles.actionBtn, { backgroundColor: theme.primarySoft, borderColor: theme.primary, borderWidth: 1 }]}
                  >
                    <ThemedText style={{ fontSize: 12, fontWeight: '700', color: theme.primary }}>Reorder</ThemedText>
                  </Pressable>
                )}

                <Pressable
                  onPress={() => router.push('/chat' as never)}
                  style={[styles.actionBtn, { backgroundColor: theme.muted, borderColor: theme.border, borderWidth: 1 }]}
                >
                  <ThemedText style={{ fontSize: 12, fontWeight: '700', color: theme.text }}>Help</ThemedText>
                </Pressable>
              </View>
            </View>
          </Pressable>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: spacing.x3 },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.x3,
    height: 44,
    borderRadius: radii.pill,
    borderWidth: 1,
    gap: spacing.x2,
    marginBottom: spacing.x3,
  },
  searchInput: { flex: 1, fontSize: 13 },
  tabContainer: { flexDirection: 'row', gap: spacing.x2, marginBottom: spacing.x3 },
  tabChip: {
    flex: 1,
    height: 38,
    borderRadius: radii.compact,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tabLabel: { fontSize: 12, fontWeight: '700' },
  listContent: { gap: spacing.x4, paddingBottom: spacing.x6 },
  emptyState: { alignItems: 'center', justifyContent: 'center', padding: spacing.x6, gap: spacing.x3 },
  emptyTitle: { ...typography.headline, fontSize: 16, fontWeight: '700' },
  orderCard: { borderRadius: radii.card, borderWidth: 1, padding: spacing.x3, gap: spacing.x3 },
  cardHeader: { flexDirection: 'row', gap: spacing.x3, alignItems: 'center' },
  providerLogo: { width: 36, height: 36, borderRadius: radii.pill },
  providerName: { ...typography.headline, fontSize: 14, fontWeight: '700' },
  itemsBox: { padding: spacing.x2, borderRadius: radii.compact, gap: spacing.x2 },
  itemRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.x2 },
  itemThumb: { width: 32, height: 32, borderRadius: 4 },
  itemName: { fontSize: 12, fontWeight: '600' },
  cardFooter: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingTop: 4 },
  totalPrice: { fontSize: 16, fontWeight: '800' },
  actionsRow: { flexDirection: 'row', gap: 6, alignItems: 'center' },
  actionBtn: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: radii.compact },
});
