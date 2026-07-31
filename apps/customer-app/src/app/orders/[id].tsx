import { useLocalSearchParams, useRouter } from 'expo-router';
import React, { useEffect, useState } from 'react';
import { Alert, Image, Linking, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { ThemedText } from '@/components/themed-text';
import { PrimaryButton } from '@/components/ui/primary-button';
import { ScreenHeader } from '@/components/ui/screen-header';
import { StatusBadge } from '@/components/ui/status-badge';

import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { fetchOrderByIdData, TRACKING_STEPS, type Order, type OrderStatus } from '@/services/orders-data';

export default function OrderDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const theme = useTheme();

  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    void fetchOrderByIdData(id).then(setOrder).finally(() => setLoading(false));
  }, [id]);

  if (loading) {
    return (
      <View style={[styles.container, { backgroundColor: theme.background, justifyContent: 'center', alignItems: 'center' }]}>
        <ThemedText style={{ color: theme.textSecondary }}>Loading order tracking details...</ThemedText>
      </View>
    );
  }

  if (!order) {
    return (
      <View style={[styles.container, { backgroundColor: theme.background, justifyContent: 'center', alignItems: 'center', gap: 12 }]}>
        <AppIcon name="warning" color={theme.error} size={48} />
        <ThemedText style={[styles.title, { color: theme.text }]}>Order Not Found</ThemedText>
        <PrimaryButton label="Back to Orders" onPress={() => router.back()} />
      </View>
    );
  }

  const getStepIndex = (status: OrderStatus) => {
    switch (status) {
      case 'ORDER_PLACED':
        return 0;
      case 'CONFIRMED':
        return 1;
      case 'PREPARING':
        return 2;
      case 'OUT_FOR_DELIVERY':
        return 3;
      case 'DELIVERED':
        return 4;
      default:
        return -1;
    }
  };

  const currentStep = getStepIndex(order.status);

  const handleCallCaptain = () => {
    if (order.captain?.phone) {
      void Linking.openURL(`tel:${order.captain.phone}`);
    }
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <ScreenHeader title={order.orderNumber} subtitle={order.providerName} />

      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        {/* Status Card Banner */}
        <View style={[styles.bannerCard, { backgroundColor: theme.primarySoft, borderColor: theme.primary }]}>
          <View style={{ flex: 1, gap: 4 }}>
            <ThemedText style={{ fontSize: 12, color: theme.primary, fontWeight: '700' }}>LIVE ORDER STATUS</ThemedText>
            <ThemedText style={[styles.bannerStatusText, { color: theme.primary }]}>{order.statusText}</ThemedText>
            <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>Est. Delivery: {order.estimatedDelivery}</ThemedText>
          </View>
          <StatusBadge label={order.status} color={theme.primary} />
        </View>

        {/* Order Progress Flow Tracker */}
        {order.status !== 'CANCELLED' && (
          <View style={[styles.card, shadows.raised, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
            <ThemedText style={[styles.cardTitle, { color: theme.text }]}>Tracking Timeline</ThemedText>
            <View style={styles.timeline}>
              {TRACKING_STEPS.map((step, idx) => {
                const isDone = idx <= currentStep;
                const isCurrent = idx === currentStep;
                return (
                  <View key={step.key} style={styles.timelineRow}>
                    <View style={styles.timelineCol}>
                      <View
                        style={[
                          styles.dot,
                          {
                            backgroundColor: isDone ? theme.primary : theme.muted,
                            borderColor: isCurrent ? theme.accent : theme.border,
                            borderWidth: isCurrent ? 3 : 0,
                          },
                        ]}
                      />
                      {idx < TRACKING_STEPS.length - 1 && (
                        <View
                          style={[
                            styles.line,
                            { backgroundColor: idx < currentStep ? theme.primary : theme.border },
                          ]}
                        />
                      )}
                    </View>

                    <View style={{ flex: 1, paddingBottom: 16 }}>
                      <ThemedText
                        style={{
                          fontSize: 14,
                          fontWeight: isDone ? '700' : '500',
                          color: isDone ? theme.text : theme.textSecondary,
                        }}
                      >
                        {step.title}
                      </ThemedText>
                      <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>{step.subtitle}</ThemedText>
                    </View>
                  </View>
                );
              })}
            </View>
          </View>
        )}

        {/* Delivery Captain Info */}
        {order.captain && (
          <View style={[styles.card, shadows.raised, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
            <ThemedText style={[styles.cardTitle, { color: theme.text }]}>Delivery Captain</ThemedText>
            <View style={styles.captainRow}>
              <View style={[styles.captainAvatar, { backgroundColor: theme.primarySoft }]}>
                <AppIcon name="store" color={theme.primary} size={24} />
              </View>
              <View style={{ flex: 1 }}>
                <ThemedText style={{ fontSize: 14, fontWeight: '700', color: theme.text }}>{order.captain.name}</ThemedText>
                <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>
                  {order.captain.vehicleNumber} • ⭐ {order.captain.rating}
                </ThemedText>
              </View>
              <Pressable onPress={handleCallCaptain} style={[styles.callBtn, { backgroundColor: theme.success }]}>
                <AppIcon name="support" color="#FFFFFF" size={16} />
                <ThemedText style={{ fontSize: 12, fontWeight: '700', color: '#FFFFFF' }}>Call</ThemedText>
              </Pressable>
            </View>
          </View>
        )}

        {/* Delivery Address */}
        <View style={[styles.card, shadows.raised, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <ThemedText style={[styles.cardTitle, { color: theme.text }]}>Delivery Address</ThemedText>
          <ThemedText style={{ fontSize: 14, fontWeight: '700', color: theme.text }}>{order.deliveryAddress.name}</ThemedText>
          <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>
            {order.deliveryAddress.street}, {order.deliveryAddress.city} - {order.deliveryAddress.pincode}
          </ThemedText>
        </View>

        {/* Itemized Receipt */}
        <View style={[styles.card, shadows.raised, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <ThemedText style={[styles.cardTitle, { color: theme.text }]}>Order Items ({order.items.length})</ThemedText>
          {order.items.map((item) => (
            <View key={item.id} style={styles.itemRow}>
              <Image source={{ uri: item.imageUrl }} style={styles.itemThumb} resizeMode="cover" />
              <View style={{ flex: 1 }}>
                <ThemedText style={{ fontSize: 13, fontWeight: '700', color: theme.text }}>{item.name}</ThemedText>
                {item.variant && <ThemedText style={{ fontSize: 11, color: theme.textSecondary }}>{item.variant}</ThemedText>}
              </View>
              <ThemedText style={{ fontSize: 13, fontWeight: '700', color: theme.text }}>
                {item.quantity}x ₹{item.price}
              </ThemedText>
            </View>
          ))}

          <View style={[styles.divider, { backgroundColor: theme.border }]} />

          <View style={styles.summaryRow}>
            <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>Item Subtotal</ThemedText>
            <ThemedText style={{ fontSize: 13, color: theme.text }}>₹{order.totalAmount + order.discount - order.deliveryFee - order.tax}</ThemedText>
          </View>
          <View style={styles.summaryRow}>
            <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>Delivery Fee</ThemedText>
            <ThemedText style={{ fontSize: 13, color: theme.text }}>₹{order.deliveryFee}</ThemedText>
          </View>
          {order.discount > 0 && (
            <View style={styles.summaryRow}>
              <ThemedText style={{ fontSize: 13, color: theme.success }}>Discount Coupon</ThemedText>
              <ThemedText style={{ fontSize: 13, color: theme.success }}>-₹{order.discount}</ThemedText>
            </View>
          )}
          <View style={styles.summaryRow}>
            <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>Taxes & Charges</ThemedText>
            <ThemedText style={{ fontSize: 13, color: theme.text }}>₹{order.tax}</ThemedText>
          </View>

          <View style={[styles.divider, { backgroundColor: theme.border }]} />

          <View style={styles.summaryRow}>
            <ThemedText style={{ fontSize: 15, fontWeight: '800', color: theme.text }}>Total Paid</ThemedText>
            <ThemedText style={{ fontSize: 16, fontWeight: '800', color: theme.primary }}>₹{order.totalAmount}</ThemedText>
          </View>
        </View>

        {/* Action Buttons */}
        {order.invoiceUrl && (
          <Pressable
            onPress={() => Alert.alert('Download Invoice', `Downloading PDF invoice for ${order.orderNumber}`)}
            style={[styles.invoiceBtn, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}
          >
            <AppIcon name="medical" color={theme.primary} size={18} />
            <ThemedText style={{ fontSize: 13, fontWeight: '700', color: theme.primary }}>Download Tax Invoice (PDF)</ThemedText>
          </Pressable>
        )}


        <PrimaryButton label="Need Help with this Order?" onPress={() => router.push('/chat' as never)} />
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: spacing.x3 },
  scrollContent: { gap: spacing.x4, paddingBottom: spacing.x6 },
  title: { ...typography.headline, fontSize: 18, fontWeight: '800' },
  bannerCard: { padding: spacing.x4, borderRadius: radii.card, borderWidth: 1, flexDirection: 'row', alignItems: 'center' },
  bannerStatusText: { ...typography.headline, fontSize: 16, fontWeight: '800' },
  card: { borderRadius: radii.card, borderWidth: 1, padding: spacing.x4, gap: spacing.x3 },
  cardTitle: { ...typography.headline, fontSize: 15, fontWeight: '700' },
  timeline: { paddingTop: 8 },
  timelineRow: { flexDirection: 'row', gap: 12 },
  timelineCol: { alignItems: 'center', width: 24 },
  dot: { width: 14, height: 14, borderRadius: 7 },
  line: { width: 2, flex: 1 },
  captainRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.x3 },
  captainAvatar: { width: 44, height: 44, borderRadius: 22, alignItems: 'center', justifyContent: 'center' },
  callBtn: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 12, paddingVertical: 8, borderRadius: radii.pill },
  itemRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.x3 },
  itemThumb: { width: 36, height: 36, borderRadius: 6 },
  divider: { height: 1, width: '100%', marginVertical: 4 },
  summaryRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  invoiceBtn: {
    height: 44,
    borderRadius: radii.card,
    borderWidth: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
});
