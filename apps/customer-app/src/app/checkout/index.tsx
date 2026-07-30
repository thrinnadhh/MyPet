import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { AppIcon } from '@/components/app-icon';
import { AppBar, FilterChip, PrimaryAction, StateView, StatusBadge } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { useAuth } from '@/context/AuthContext';
import { useAuthIntent } from '@/context/AuthIntentContext';
import { useCart } from '@/context/CartContext';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { useTranslation } from '@/i18n';
import { fetchDefaultAddress, isOfflineError, type CustomerAddress } from '@/services/customer-profile';
import {
  createCustomerOrder,
  fetchCheckoutQuote,
  type CheckoutQuoteOutput,
} from '@/services/customer-orders';

export default function CheckoutScreen() {
  const router = useRouter();
  const { t } = useTranslation();
  const theme = useTheme();
  const { user, session } = useAuth();
  const { requireAuth } = useAuthIntent();
  const { items, providerId, clearCart, loading: cartLoading } = useCart();
  const checkoutItems = useMemo(() => {
    const quantities = new Map<string, number>();
    items.forEach((item) => {
      quantities.set(item.product.id, (quantities.get(item.product.id) ?? 0) + item.quantity);
    });
    return Array.from(quantities, ([offeringId, quantity]) => ({ offeringId, quantity }));
  }, [items]);

  const [address, setAddress] = useState<CustomerAddress | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<'CARD' | 'UPI' | 'COD'>('CARD');
  const [couponCodeInput, setCouponCodeInput] = useState('');
  const [appliedCoupon, setAppliedCoupon] = useState<string | null>(null);

  const [quote, setQuote] = useState<CheckoutQuoteOutput | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'offline' | 'error'>('loading');
  const [quoting, setQuoting] = useState(false);
  const [placing, setPlacing] = useState(false);

  const loadData = useCallback(async () => {
    if (!user || !session || cartLoading) return;
    if (!providerId || checkoutItems.length === 0) {
      setQuote(null);
      setState('ready');
      return;
    }
    setState('loading');
    try {
      const defAddr = await fetchDefaultAddress(session.access_token);
      setAddress(defAddr);

      if (defAddr) {
        const quoteRes = await fetchCheckoutQuote(
          {
            customerId: user.id,
            providerId,
            deliveryAddressId: defAddr.addressId,
            items: checkoutItems,
            couponCode: appliedCoupon,
            paymentMethod,
            city: defAddr.city,
            latitude: defAddr.geoLat,
            longitude: defAddr.geoLng,
          },
          session.access_token,
        );
        setQuote(quoteRes);
      }
      setState('ready');
    } catch (error) {
      setState(isOfflineError(error) ? 'offline' : 'error');
    }
  }, [appliedCoupon, cartLoading, checkoutItems, paymentMethod, providerId, session, user]);

  useEffect(() => {
    if (user && session) void loadData();
  }, [loadData, session, user]);

  const handleApplyCoupon = async () => {
    if (!couponCodeInput.trim() || !user || !session || !address || !providerId || checkoutItems.length === 0) {
      return;
    }
    setQuoting(true);
    try {
      const codeToApply = couponCodeInput.trim().toUpperCase();
      const newQuote = await fetchCheckoutQuote(
        {
          customerId: user.id,
          providerId,
          deliveryAddressId: address.addressId,
          items: checkoutItems,
          couponCode: codeToApply,
          paymentMethod,
          city: address.city,
          latitude: address.geoLat,
          longitude: address.geoLng,
        },
        session.access_token,
      );
      setQuote(newQuote);
      setAppliedCoupon(codeToApply);
      setCouponCodeInput('');
      Alert.alert('Coupon Applied', `Coupon ${codeToApply} applied successfully!`);
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Could not apply coupon code.';
      Alert.alert('Invalid Coupon', message);
    } finally {
      setQuoting(false);
    }
  };

  const handleRemoveCoupon = () => {
    setAppliedCoupon(null);
  };

  const handlePlaceOrder = async () => {
    if (!user || !session || !address || !quote || !providerId || checkoutItems.length === 0) return;
    if (paymentMethod === 'COD' && !quote.isCodAvailable) {
      Alert.alert('COD Not Allowed', quote.codRejectionReason || 'Order total exceeds COD limit.');
      return;
    }

    setPlacing(true);
    try {
      const created = await createCustomerOrder(
        {
          customerId: user.id,
          providerId,
          deliveryAddressId: address.addressId,
          items: checkoutItems,
          couponCode: appliedCoupon,
          paymentMethod,
          quoteToken: quote.quoteToken,
          city: address.city,
          latitude: address.geoLat,
          longitude: address.geoLng,
        },
        session.access_token,
      );

      await clearCart();
      Alert.alert('Order Placed!', `Your order #${created.id.slice(0, 8)} has been placed successfully.`);
      router.replace(`/orders/${created.id}` as any);
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Could not complete order placement.';
      Alert.alert('Checkout Failed', message);
    } finally {
      setPlacing(false);
    }
  };

  if (!user || !session) {
    return (
      <ScreenShell scroll={false} header={<AppBar title={t('routes.checkout')} />}>
        <StateView
          kind="unauthenticated"
          title={t('states.unauthenticated')}
          message={t('routes.checkoutSignIn')}
          actionLabel={t('common.signIn')}
          onAction={() => void requireAuth({ action: 'CHECKOUT', returnTo: '/checkout' })}
        />
      </ScreenShell>
    );
  }

  if (state === 'loading' || cartLoading) {
    return (
      <ScreenShell scroll={false} header={<AppBar title={t('routes.checkout')} />}>
        <StateView kind="loading" title={t('states.loading')} message="Fetching server breakdown..." />
      </ScreenShell>
    );
  }

  if (state === 'offline' || state === 'error') {
    const isOffline = state === 'offline';
    return (
      <ScreenShell scroll={false} header={<AppBar title={t('routes.checkout')} />}>
        <StateView
          kind={state}
          title={t(isOffline ? 'states.offline' : 'states.error')}
          message={t(isOffline ? 'states.offlineMessage' : 'states.errorMessage')}
          actionLabel={t('states.retry')}
          onAction={() => void loadData()}
        />
      </ScreenShell>
    );
  }

  if (!providerId || checkoutItems.length === 0) {
    return (
      <ScreenShell scroll={false} header={<AppBar title={t('routes.checkout')} />}>
        <StateView
          kind="empty"
          title="Your cart is empty"
          message="Add an in-stock product before starting checkout."
          actionLabel="Browse products"
          onAction={() => router.replace('/(tabs)' as never)}
        />
      </ScreenShell>
    );
  }

  if (!address) {
    return (
      <ScreenShell scroll={false} header={<AppBar title={t('routes.checkout')} />}>
        <StateView
          kind="error"
          title="Delivery Address Required"
          message="Please add a valid delivery address in your customer profile before checking out."
          actionLabel="Go to Profile"
          onAction={() => router.push('/(tabs)/profile' as any)}
        />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell header={<AppBar title={t('routes.checkout')} subtitle="Server-Authoritative Pricing & Review" />}>
      <View style={styles.container}>
        {/* Delivery Address Snapshot */}
        <View
          style={[
            styles.card,
            shadows.card,
            { backgroundColor: theme.backgroundElement, borderColor: theme.border },
          ]}
        >
          <View style={styles.cardHeader}>
            <AppIcon name="location" size={18} color={theme.primary} />
            <ThemedText style={styles.cardTitle}>Delivery Address</ThemedText>
            <StatusBadge label={address.label || 'Home'} tone="success" />
          </View>
          <ThemedText style={styles.addressLine}>{address.line1}</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            {address.city}, {address.state} - {address.pincode}
          </ThemedText>
        </View>

        {/* Coupon Code Section */}
        <View
          style={[
            styles.card,
            shadows.card,
            { backgroundColor: theme.backgroundElement, borderColor: theme.border },
          ]}
        >
          <ThemedText style={styles.cardTitle}>Promotions & Coupons</ThemedText>
          {appliedCoupon ? (
            <View style={[styles.appliedCouponRow, { backgroundColor: theme.primarySoft }]}>
              <View style={styles.flexRow}>
                <AppIcon name="sparkle" size={16} color={theme.primary} />
                <ThemedText style={{ color: theme.primary, fontWeight: '700' }}>
                  {appliedCoupon} APPLIED
                </ThemedText>
              </View>
              <Pressable onPress={handleRemoveCoupon}>
                <ThemedText style={{ color: theme.danger, fontWeight: '700' }}>Remove</ThemedText>
              </Pressable>
            </View>
          ) : (
            <View style={styles.couponInputRow}>
              <TextInput
                value={couponCodeInput}
                onChangeText={setCouponCodeInput}
                placeholder="Enter coupon code (e.g. SAVE50)"
                placeholderTextColor={theme.textSecondary}
                style={[styles.input, { color: theme.text, borderColor: theme.border }]}
                autoCapitalize="characters"
              />
              <Pressable
                style={[styles.applyBtn, { backgroundColor: theme.primary }]}
                onPress={() => void handleApplyCoupon()}
              >
                <ThemedText style={{ color: '#FFF', fontWeight: '700' }}>Apply</ThemedText>
              </Pressable>
            </View>
          )}
        </View>

        {/* Payment Method Selector */}
        <View
          style={[
            styles.card,
            shadows.card,
            { backgroundColor: theme.backgroundElement, borderColor: theme.border },
          ]}
        >
          <ThemedText style={styles.cardTitle}>Payment Method</ThemedText>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.tabsScroll}>
            <FilterChip
              label="Card"
              selected={paymentMethod === 'CARD'}
              onPress={() => setPaymentMethod('CARD')}
            />
            <FilterChip
              label="UPI"
              selected={paymentMethod === 'UPI'}
              onPress={() => setPaymentMethod('UPI')}
            />
            <FilterChip
              label="Cash on Delivery (COD)"
              selected={paymentMethod === 'COD'}
              onPress={() => setPaymentMethod('COD')}
            />
          </ScrollView>

          {paymentMethod === 'COD' && quote && !quote.isCodAvailable ? (
            <View style={[styles.warningBox, { backgroundColor: theme.primarySoft }]}>
              <AppIcon name="warning" size={16} color={theme.danger} />
              <ThemedText type="small" style={{ color: theme.danger, flex: 1 }}>
                {quote.codRejectionReason || 'COD is not available for this order total.'}
              </ThemedText>
            </View>
          ) : null}
        </View>

        {/* Server Authoritative Price Breakdown */}
        {quote ? (
          <View
            style={[
              styles.card,
              shadows.card,
              { backgroundColor: theme.backgroundElement, borderColor: theme.border },
            ]}
          >
            <ThemedText style={styles.cardTitle}>Server Price Breakdown</ThemedText>

            <View style={styles.breakdownRow}>
              <ThemedText themeColor="textSecondary">Items Subtotal</ThemedText>
              <ThemedText style={styles.valueText}>₹{quote.subtotal}</ThemedText>
            </View>

            {quote.couponDiscount > 0 ? (
              <View style={styles.breakdownRow}>
                <ThemedText style={{ color: theme.primary }}>Coupon Discount</ThemedText>
                <ThemedText style={{ color: theme.primary, fontWeight: '700' }}>
                  -₹{quote.couponDiscount}
                </ThemedText>
              </View>
            ) : null}

            <View style={styles.breakdownRow}>
              <ThemedText themeColor="textSecondary">Delivery Fee</ThemedText>
              <ThemedText style={styles.valueText}>₹{quote.deliveryFee}</ThemedText>
            </View>

            <View style={styles.breakdownRow}>
              <ThemedText themeColor="textSecondary">Taxes (GST)</ThemedText>
              <ThemedText style={styles.valueText}>₹{quote.tax}</ThemedText>
            </View>

            <View style={[styles.divider, { backgroundColor: theme.border }]} />

            <View style={styles.breakdownRow}>
              <ThemedText style={styles.totalLabel}>Payable Total</ThemedText>
              <ThemedText style={styles.totalValue}>₹{quote.payableTotal}</ThemedText>
            </View>
          </View>
        ) : null}

        {/* Placement CTA */}
        <PrimaryAction
          label={`Place Order (${paymentMethod})`}
          onPress={() => void handlePlaceOrder()}
          loading={placing || quoting}
        />
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: { padding: spacing.x4, gap: spacing.x3, paddingBottom: spacing.x8 },
  card: { borderWidth: StyleSheet.hairlineWidth, borderRadius: radii.card, padding: spacing.x4, gap: spacing.x2 },
  cardHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing.x2 },
  cardTitle: { ...typography.label, fontWeight: '700' },
  addressLine: { ...typography.body, marginTop: spacing.x1 },
  appliedCouponRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: spacing.x3,
    borderRadius: radii.compact,
  },
  flexRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.x2 },
  couponInputRow: { flexDirection: 'row', gap: spacing.x2, marginTop: spacing.x1 },
  input: { flex: 1, borderWidth: 1, borderRadius: radii.compact, paddingHorizontal: spacing.x3, height: 40 },
  applyBtn: { paddingHorizontal: spacing.x4, height: 40, borderRadius: radii.compact, alignItems: 'center', justifyContent: 'center' },
  tabsScroll: { flexDirection: 'row', gap: spacing.x2, marginTop: spacing.x1 },
  warningBox: { flexDirection: 'row', alignItems: 'center', gap: spacing.x2, padding: spacing.x3, borderRadius: radii.compact, marginTop: spacing.x2 },
  breakdownRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: spacing.x1 },
  valueText: { ...typography.body, fontWeight: '600' },
  divider: { height: StyleSheet.hairlineWidth, marginVertical: spacing.x2 },
  totalLabel: { ...typography.title },
  totalValue: { ...typography.title, color: '#10B981', fontWeight: '800' },
});
