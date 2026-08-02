import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { AppIcon } from '@/components/app-icon';
import { AppBar, PrimaryAction, StateView, StatusBadge } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import type { CustomerPaymentMethod } from '@/contracts/customer-payment';
import { useAuth } from '@/context/AuthContext';
import { useAuthIntent } from '@/context/AuthIntentContext';
import { useCart } from '@/context/CartContext';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { useTranslation } from '@/i18n';
import {
  createCustomerOrder,
  fetchCheckoutQuote,
  type CheckoutQuoteOutput,
  type CustomerOrderRecord,
} from '@/services/customer-orders';
import {
  initiateOrderPayment,
  openRazorpayOrder,
  waitForPaymentOutcome,
} from '@/services/customer-payments';
import { fetchDefaultAddress, isOfflineError, type CustomerAddress } from '@/services/customer-profile';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const PAYMENT_METHODS: Array<{ id: CustomerPaymentMethod; label: string }> = [
  { id: 'COD', label: 'Cash on delivery' },
  { id: 'UPI', label: 'UPI' },
  { id: 'CARD', label: 'Card' },
];

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

  const hasPreviewItems = !providerId
    || !UUID_PATTERN.test(providerId)
    || checkoutItems.some((item) => !UUID_PATTERN.test(item.offeringId));

  const [address, setAddress] = useState<CustomerAddress | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<CustomerPaymentMethod>('COD');
  const [couponCodeInput, setCouponCodeInput] = useState('');
  const [appliedCoupon, setAppliedCoupon] = useState<string | null>(null);
  const [quote, setQuote] = useState<CheckoutQuoteOutput | null>(null);
  const [pendingOrder, setPendingOrder] = useState<CustomerOrderRecord | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'offline' | 'error'>('loading');
  const [placing, setPlacing] = useState(false);

  const loadData = useCallback(async () => {
    if (!user || !session || cartLoading) return;
    if (!providerId || checkoutItems.length === 0 || hasPreviewItems) {
      setQuote(null);
      setState('ready');
      return;
    }

    setState('loading');
    try {
      const defaultAddress = await fetchDefaultAddress(session.access_token);
      setAddress(defaultAddress);
      if (defaultAddress) {
        setQuote(await fetchCheckoutQuote({
          customerId: user.id,
          providerId,
          deliveryAddressId: defaultAddress.addressId,
          items: checkoutItems,
          couponCode: appliedCoupon,
          paymentMethod,
          city: defaultAddress.city,
          latitude: defaultAddress.geoLat,
          longitude: defaultAddress.geoLng,
        }, session.access_token));
      }
      setState('ready');
    } catch (error) {
      setState(isOfflineError(error) ? 'offline' : 'error');
    }
  }, [appliedCoupon, cartLoading, checkoutItems, hasPreviewItems, paymentMethod, providerId, session, user]);

  useEffect(() => {
    if (user && session) void loadData();
  }, [loadData, session, user]);

  const handleApplyCoupon = () => {
    const code = couponCodeInput.trim().toUpperCase();
    if (!code) return;
    setAppliedCoupon(code);
    setCouponCodeInput('');
  };

  const createOrder = async (): Promise<CustomerOrderRecord> => {
    if (!user || !session || !address || !quote || !providerId) {
      throw new Error('Checkout is not ready.');
    }
    if (pendingOrder) return pendingOrder;

    const created = await createCustomerOrder({
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
    }, session.access_token);
    setPendingOrder(created);
    return created;
  };

  const handlePlaceOrder = async () => {
    if (!user || !session || !address || !quote || !providerId || checkoutItems.length === 0) return;
    if (paymentMethod === 'COD' && !quote.isCodAvailable) {
      Alert.alert('COD unavailable', quote.codRejectionReason || 'Choose UPI or card for this order.');
      return;
    }

    setPlacing(true);
    try {
      const order = await createOrder();
      if (paymentMethod === 'COD') {
        await clearCart();
        router.replace(`/orders/${order.id}` as never);
        return;
      }

      const initialization = await initiateOrderPayment(user.id, order.id, order.rawTotal);
      await openRazorpayOrder(initialization);
      const payment = await waitForPaymentOutcome(order.id);

      if (payment.status === 'SUCCESS') {
        await clearCart();
        Alert.alert('Payment confirmed', 'Your order is now confirmed by the MyPet server.');
        router.replace(`/orders/${order.id}` as never);
      } else if (payment.status === 'PENDING') {
        Alert.alert(
          'Payment confirmation pending',
          'Razorpay has not confirmed the payment yet. Track or retry from the order screen.',
          [{ text: 'View order', onPress: () => router.replace(`/orders/${order.id}` as never) }],
        );
      } else {
        Alert.alert('Payment not completed', 'No successful payment was confirmed. You can retry safely.');
      }
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Could not complete checkout.';
      Alert.alert('Checkout failed', message);
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
        <StateView kind="loading" title={t('states.loading')} message="Fetching the server-authoritative total…" />
      </ScreenShell>
    );
  }

  if (state === 'offline' || state === 'error') {
    return (
      <ScreenShell scroll={false} header={<AppBar title={t('routes.checkout')} />}>
        <StateView
          kind={state}
          title={state === 'offline' ? t('states.offline') : t('states.error')}
          message={state === 'offline' ? t('states.offlineMessage') : t('states.errorMessage')}
          actionLabel={t('states.retry')}
          onAction={() => void loadData()}
        />
      </ScreenShell>
    );
  }

  if (!providerId || checkoutItems.length === 0) {
    return (
      <ScreenShell scroll={false} header={<AppBar title={t('routes.checkout')} />}>
        <StateView kind="empty" title="Your cart is empty" message="Add an in-stock product before checkout." />
      </ScreenShell>
    );
  }

  if (hasPreviewItems) {
    return (
      <ScreenShell scroll={false} header={<AppBar title={t('routes.checkout')} />}>
        <StateView
          kind="error"
          title="Preview products cannot be ordered"
          message="Clear sample catalog data and add products from a live provider."
          actionLabel="Clear preview cart"
          onAction={() => void clearCart()}
        />
      </ScreenShell>
    );
  }

  if (!address) {
    return (
      <ScreenShell scroll={false} header={<AppBar title={t('routes.checkout')} />}>
        <StateView
          kind="error"
          title="Delivery address required"
          message="Add a valid default address before checkout."
          actionLabel="Go to profile"
          onAction={() => router.push('/(tabs)/profile' as never)}
        />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell header={<AppBar title={t('routes.checkout')} subtitle="Secure server-authoritative checkout" />}>
      <View style={styles.container}>
        <View style={[styles.card, shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <View style={styles.headerRow}>
            <AppIcon name="location" size={18} color={theme.primary} />
            <ThemedText style={styles.cardTitle}>Delivery address</ThemedText>
            <StatusBadge label={address.label || 'Default'} tone="success" />
          </View>
          <ThemedText>{address.line1}</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            {address.city}, {address.state} – {address.pincode}
          </ThemedText>
        </View>

        <View style={[styles.card, shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <ThemedText style={styles.cardTitle}>Payment method</ThemedText>
          <View style={styles.methodRow}>
            {PAYMENT_METHODS.map((method) => {
              const selected = paymentMethod === method.id;
              return (
                <Pressable
                  key={method.id}
                  accessibilityRole="button"
                  accessibilityState={{ selected }}
                  onPress={() => {
                    setPaymentMethod(method.id);
                    setPendingOrder(null);
                  }}
                  style={[
                    styles.method,
                    { borderColor: selected ? theme.primary : theme.border, backgroundColor: selected ? theme.primarySoft : theme.background },
                  ]}
                >
                  <ThemedText style={{ fontWeight: '700', color: selected ? theme.primary : theme.text }}>
                    {method.label}
                  </ThemedText>
                </Pressable>
              );
            })}
          </View>
          <ThemedText type="small" themeColor="textSecondary">
            Online payment success is accepted only after Razorpay webhook verification.
          </ThemedText>
          {paymentMethod === 'COD' && quote && !quote.isCodAvailable ? (
            <View style={styles.warningRow}>
              <AppIcon name="warning" size={16} color={theme.danger} />
              <ThemedText type="small" style={{ color: theme.danger, flex: 1 }}>
                {quote.codRejectionReason || 'COD is unavailable for this order.'}
              </ThemedText>
            </View>
          ) : null}
        </View>

        <View style={[styles.card, shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <ThemedText style={styles.cardTitle}>Coupon</ThemedText>
          {appliedCoupon ? (
            <View style={styles.headerRow}>
              <StatusBadge label={`${appliedCoupon} applied`} tone="success" />
              <Pressable onPress={() => setAppliedCoupon(null)}>
                <ThemedText style={{ color: theme.danger, fontWeight: '700' }}>Remove</ThemedText>
              </Pressable>
            </View>
          ) : (
            <View style={styles.couponRow}>
              <TextInput
                value={couponCodeInput}
                onChangeText={setCouponCodeInput}
                placeholder="Enter coupon code"
                placeholderTextColor={theme.textSecondary}
                autoCapitalize="characters"
                style={[styles.input, { color: theme.text, borderColor: theme.border }]}
              />
              <Pressable style={[styles.applyButton, { backgroundColor: theme.primary }]} onPress={handleApplyCoupon}>
                <ThemedText style={styles.applyText}>Apply</ThemedText>
              </Pressable>
            </View>
          )}
        </View>

        {quote ? (
          <View style={[styles.card, shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
            <ThemedText style={styles.cardTitle}>Price breakdown</ThemedText>
            <PriceRow label="Items" value={quote.subtotal} />
            {quote.couponDiscount > 0 ? <PriceRow label="Coupon" value={-quote.couponDiscount} /> : null}
            {quote.loyaltyDiscount > 0 ? <PriceRow label="Loyalty" value={-quote.loyaltyDiscount} /> : null}
            <PriceRow label="Delivery" value={quote.deliveryFee} />
            <PriceRow label="Tax" value={quote.tax} />
            <View style={[styles.divider, { backgroundColor: theme.border }]} />
            <View style={styles.headerRow}>
              <ThemedText style={styles.totalLabel}>Payable total</ThemedText>
              <ThemedText style={[styles.totalValue, { color: theme.primary }]}>₹{quote.payableTotal.toFixed(2)}</ThemedText>
            </View>
          </View>
        ) : null}

        {pendingOrder && paymentMethod !== 'COD' ? (
          <View style={[styles.notice, { backgroundColor: theme.primarySoft }]}>
            <ThemedText style={{ fontWeight: '700' }}>Payment retry for order #{pendingOrder.id.slice(0, 8)}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              Retrying reuses the existing order and does not reserve stock twice.
            </ThemedText>
          </View>
        ) : null}

        <PrimaryAction
          label={paymentMethod === 'COD' ? 'Place COD order' : `Pay securely with ${paymentMethod}`}
          onPress={() => void handlePlaceOrder()}
          loading={placing}
        />
      </View>
    </ScreenShell>
  );
}

function PriceRow({ label, value }: { label: string; value: number }) {
  return (
    <View style={styles.headerRow}>
      <ThemedText themeColor="textSecondary">{label}</ThemedText>
      <ThemedText style={styles.priceValue}>{value < 0 ? '-' : ''}₹{Math.abs(value).toFixed(2)}</ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { padding: spacing.x4, gap: spacing.x3, paddingBottom: spacing.x8 },
  card: { borderWidth: StyleSheet.hairlineWidth, borderRadius: radii.card, padding: spacing.x4, gap: spacing.x3 },
  cardTitle: { ...typography.label, fontWeight: '700' },
  headerRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.x2 },
  methodRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  method: { borderWidth: 1, borderRadius: radii.compact, paddingHorizontal: spacing.x3, paddingVertical: spacing.x2 },
  warningRow: { flexDirection: 'row', gap: spacing.x2, alignItems: 'flex-start' },
  couponRow: { flexDirection: 'row', gap: spacing.x2 },
  input: { flex: 1, height: 42, borderWidth: 1, borderRadius: radii.compact, paddingHorizontal: spacing.x3 },
  applyButton: { minWidth: 76, borderRadius: radii.compact, alignItems: 'center', justifyContent: 'center' },
  applyText: { color: '#fff', fontWeight: '700' },
  divider: { height: StyleSheet.hairlineWidth },
  priceValue: { fontWeight: '700' },
  totalLabel: { ...typography.label, fontWeight: '800' },
  totalValue: { ...typography.title, fontWeight: '800' },
  notice: { borderRadius: radii.compact, padding: spacing.x3, gap: spacing.x1 },
});
