import React, { useState, useEffect, useCallback } from 'react';
import { 
  StyleSheet, 
  View, 
  FlatList, 
  ActivityIndicator, 
  useColorScheme, 
  TouchableOpacity,
  Modal,
  TextInput,
  ScrollView,
  Alert
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing, Colors } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';
import { appConfig } from '@/utils/app-config';

const DEMO_PROVIDERS = [
  {
    id: 'e1b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
    label: '🏬 Pet Store',
    fulfillmentType: 'DELIVERY',
  },
  {
    id: 'e2b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
    label: '✂️ Groomer',
    fulfillmentType: 'APPOINTMENT',
  },
];

interface EarningRecord {
  earningId: string;
  captainId: string;
  orderId: string;
  amount: number;
  earnedAt: string;
  payoutId?: string | null;
}

interface PayoutRecord {
  payoutId: string;
  payeeUserId: string;
  payeeRole: string;
  amount: number;
  status: string;
  periodStart: string;
  periodEnd: string;
  paidAt: string | null;
  createdAt: string;
}

interface Promotion {
  promotionId?: string;
  providerId: string | null;
  code: string;
  discountType: string;
  discountValue: number;
  maxDiscountAmount: number | null;
  minOrderValue: number | null;
  applicableCategory: string | null;
  validFrom: string;
  validUntil: string;
  isActive: boolean;
}

interface ProviderOption {
  id: string;
  label: string;
  fulfillmentType: string;
}

interface ProviderResponse {
  providerId: string;
  providerType: 'PET_STORE' | 'VET_HOSPITAL' | 'GROOMING_CENTER';
  fulfillmentType: string;
  name: string;
}

const MOCK_EARNINGS: EarningRecord[] = [
  {
    earningId: 'earn-1',
    captainId: 'd3b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
    orderId: 'order-101',
    amount: 150.00,
    earnedAt: new Date(Date.now() - 3600 * 1000 * 3).toISOString(),
    payoutId: null
  },
  {
    earningId: 'earn-2',
    captainId: 'd3b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
    orderId: 'order-102',
    amount: 150.00,
    earnedAt: new Date(Date.now() - 3600 * 1000 * 24).toISOString(),
    payoutId: 'payout-demo'
  }
];

const MOCK_PAYOUTS: PayoutRecord[] = [
  {
    payoutId: 'payout-1',
    payeeUserId: 'e1b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
    payeeRole: 'MERCHANT',
    amount: 1200.00,
    status: 'PAID',
    periodStart: '2026-06-01',
    periodEnd: '2026-06-15',
    paidAt: new Date(Date.now() - 3600 * 1000 * 48).toISOString(),
    createdAt: new Date(Date.now() - 3600 * 1000 * 48).toISOString()
  },
  {
    payoutId: 'payout-2',
    payeeUserId: 'e1b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
    payeeRole: 'MERCHANT',
    amount: 850.00,
    status: 'PENDING',
    periodStart: '2026-06-16',
    periodEnd: '2026-06-30',
    paidAt: null,
    createdAt: new Date().toISOString()
  }
];

const MOCK_PROMOTIONS: Promotion[] = [
  {
    promotionId: 'promo-1',
    providerId: 'e1b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
    code: 'DROOLS10',
    discountType: 'PERCENTAGE',
    discountValue: 10,
    maxDiscountAmount: 100,
    minOrderValue: 500,
    applicableCategory: 'Drools',
    validFrom: '2026-06-01T00:00:00Z',
    validUntil: '2026-07-31T23:59:59Z',
    isActive: true
  }
];

export default function EarningsScreen() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];
  const { user, session, activeRole } = useAuth();

  // Common State
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [screenError, setScreenError] = useState('');

  // Captain View State
  const [earnings, setEarnings] = useState<EarningRecord[]>([]);

  // Provider (Merchant) View State
  const [providers, setProviders] = useState<ProviderOption[]>(appConfig.allowDemoMode ? DEMO_PROVIDERS : []);
  const [selectedProvider, setSelectedProvider] = useState<ProviderOption | null>(appConfig.allowDemoMode ? DEMO_PROVIDERS[0] : null);
  const [payouts, setPayouts] = useState<PayoutRecord[]>([]);
  const [promotions, setPromotions] = useState<Promotion[]>([]);
  
  // Promotion Creation Form State
  const [showPromoModal, setShowPromoModal] = useState(false);
  const [promoCode, setPromoCode] = useState('');
  const [discountType, setDiscountType] = useState<'PERCENTAGE' | 'FLAT'>('PERCENTAGE');
  const [discountValue, setDiscountValue] = useState('');
  const [minOrderValue, setMinOrderValue] = useState('');
  const [maxDiscountAmount, setMaxDiscountAmount] = useState('');
  const [applicableCategory, setApplicableCategory] = useState('');
  const [errorMsg, setErrorMsg] = useState('');

  const authHeaders = useCallback((roleOverride?: string) => {
    const headers: Record<string, string> = {};
    if (session?.access_token) {
      headers.Authorization = `Bearer ${session.access_token}`;
    }
    if (user?.id) {
      headers['X-User-Id'] = user.id;
    }
    headers['X-User-Role'] = roleOverride ?? (activeRole === 'PROVIDER' ? 'MERCHANT' : activeRole ?? 'CAPTAIN');
    return headers;
  }, [activeRole, session, user]);

  // Fetch Providers list for Merchant
  const fetchProviders = useCallback(async () => {
    if (!user || activeRole !== 'PROVIDER') return;
    if (appConfig.allowDemoMode) {
      setProviders(DEMO_PROVIDERS);
      setSelectedProvider((current) => current ?? DEMO_PROVIDERS[0]);
      return;
    }
    setScreenError('');
    try {
      const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/providers?ownerUserId=${user.id}`, {
        headers: authHeaders('MERCHANT'),
      });
      if (response.ok) {
        const data: ProviderResponse[] = await response.json();
        if (Array.isArray(data) && data.length > 0) {
          const mapped = data.map((p) => ({
            id: p.providerId,
            label: p.providerType === 'PET_STORE' ? `Store ${p.name}` : p.providerType === 'VET_HOSPITAL' ? `Vet ${p.name}` : `Groom ${p.name}`,
            fulfillmentType: p.fulfillmentType,
          }));
          setProviders(mapped);
          setSelectedProvider((current) => mapped.find((item) => item.id === current?.id) ?? mapped[0]);
        } else {
          setProviders([]);
          setSelectedProvider(null);
        }
      } else {
        setScreenError(`Could not load providers (${response.status}).`);
        setProviders([]);
        setSelectedProvider(null);
      }
    } catch (err) {
      setScreenError('Network error while loading providers.');
      setProviders([]);
      setSelectedProvider(null);
    }
  }, [activeRole, authHeaders, user]);

  // Fetch Captain Earnings
  const fetchCaptainEarnings = useCallback(async (showLoader = true) => {
    if (!user) return;
    if (showLoader) setLoading(true);
    setScreenError('');
    try {
      const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/captains/${user.id}/earnings`, {
        headers: authHeaders('CAPTAIN'),
      });
      const data = await response.json();
      if (response.ok) {
        setEarnings(data);
      } else if (appConfig.allowDemoMode) {
        setEarnings(MOCK_EARNINGS);
      } else {
        setScreenError(`Could not load captain earnings (${response.status}).`);
        setEarnings([]);
      }
    } catch (err) {
      if (appConfig.allowDemoMode) {
        setEarnings(MOCK_EARNINGS);
      } else {
        setScreenError('Network error while loading captain earnings.');
        setEarnings([]);
      }
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [authHeaders, user]);

  // Fetch Merchant Payouts & Promotions
  const fetchMerchantData = useCallback(async (showLoader = true) => {
    if (!user || !selectedProvider) return;
    if (showLoader) setLoading(true);
    setScreenError('');
    try {
      // 1. Fetch Payout History
      const payoutResponse = await fetch(`${appConfig.apiBaseUrl}/api/v1/payments/payouts/user/${user.id}`, {
        headers: authHeaders('MERCHANT')
      });
      if (payoutResponse.ok) {
        const data = await payoutResponse.json();
        setPayouts(data);
      } else if (appConfig.allowDemoMode) {
        setPayouts(MOCK_PAYOUTS);
      } else {
        setScreenError(`Could not load payout history (${payoutResponse.status}).`);
        setPayouts([]);
      }

      // 2. Fetch promotions
      const promoResponse = await fetch(`${appConfig.apiBaseUrl}/api/v1/payments/promotions?providerId=${selectedProvider.id}`, {
        headers: authHeaders('MERCHANT'),
      });
      if (promoResponse.ok) {
        const data = await promoResponse.json();
        setPromotions(data);
      } else if (appConfig.allowDemoMode) {
        setPromotions(MOCK_PROMOTIONS);
      } else {
        setScreenError((current) => current || `Could not load promotions (${promoResponse.status}).`);
        setPromotions([]);
      }
    } catch (err) {
      if (appConfig.allowDemoMode) {
        setPayouts(MOCK_PAYOUTS);
        setPromotions(MOCK_PROMOTIONS);
      } else {
        setScreenError('Network error while loading payout and promotion data.');
        setPayouts([]);
        setPromotions([]);
      }
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [authHeaders, selectedProvider, user]);

  useEffect(() => {
    if (activeRole === 'PROVIDER') {
      fetchProviders();
    } else {
      fetchCaptainEarnings();
    }
  }, [activeRole, fetchCaptainEarnings, fetchProviders, user]);

  useEffect(() => {
    if (activeRole === 'PROVIDER') {
      fetchMerchantData();
    }
  }, [activeRole, fetchMerchantData, selectedProvider]);

  const handleRefresh = () => {
    setRefreshing(true);
    if (activeRole === 'PROVIDER') {
      fetchMerchantData(false);
    } else {
      fetchCaptainEarnings(false);
    }
  };

  const handleCreatePromotion = async () => {
    setErrorMsg('');
    if (!selectedProvider) return setErrorMsg('Create a provider before adding a coupon');
    const val = parseFloat(discountValue);
    const minOrd = minOrderValue ? parseFloat(minOrderValue) : null;
    const maxDisc = maxDiscountAmount ? parseFloat(maxDiscountAmount) : null;

    if (!promoCode.trim()) return setErrorMsg('Coupon code is required');
    if (isNaN(val) || val <= 0) return setErrorMsg('Invalid discount value');

    // Client-side Discount War Prevention rules
    if (discountType === 'FLAT') {
      if (minOrd === null || isNaN(minOrd) || minOrd <= 0) {
        return setErrorMsg('Minimum order value is required for flat discounts');
      }
      if (val > minOrd * 0.3) {
        return setErrorMsg('Flat discount cannot exceed 30% of minimum order value');
      }
      if (minOrd < val * 1.5) {
        return setErrorMsg('Minimum order value must be at least 1.5x the discount value');
      }
    } else {
      if (val > 30) {
        return setErrorMsg('Percentage discount cannot exceed 30%');
      }
      if (maxDisc !== null && minOrd !== null && minOrd < maxDisc * 1.5) {
        return setErrorMsg('Minimum order value must be at least 1.5x the maximum discount amount');
      }
    }

    const payload: Promotion = {
      code: promoCode.trim().toUpperCase(),
      discountType,
      discountValue: val,
      minOrderValue: minOrd,
      maxDiscountAmount: maxDisc,
      applicableCategory: applicableCategory.trim() || null,
      providerId: selectedProvider.id,
      validFrom: new Date().toISOString(),
      validUntil: new Date(Date.now() + 30 * 24 * 3600 * 1000).toISOString(), // 30 days
      isActive: true
    };

    try {
      setLoading(true);
      const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/payments/promotions`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...authHeaders('MERCHANT')
        },
        body: JSON.stringify(payload)
      });
      if (response.ok) {
        setShowPromoModal(false);
        setPromoCode('');
        setDiscountValue('');
        setMinOrderValue('');
        setMaxDiscountAmount('');
        setApplicableCategory('');
        fetchMerchantData(false);
        Alert.alert('Success', 'Promotion coupon created successfully 🎉');
      } else {
        const errorData = await response.json();
        setErrorMsg(errorData.error || 'Failed to create coupon');
      }
    } catch (err) {
      setErrorMsg('Network error, could not create promotion');
    } finally {
      setLoading(false);
    }
  };

  // Rendering Helper for Captain View
  if (activeRole !== 'PROVIDER') {
    const totalEarnings = earnings.reduce((sum, item) => sum + item.amount, 0);
    const totalDeliveries = earnings.length;

    return (
      <ThemedView style={styles.container}>
        <SafeAreaView style={styles.safeArea}>
          <View style={styles.header}>
            <ThemedText type="subtitle">Captain Earnings</ThemedText>
            <ThemedText type="small" style={{ color: colors.textSecondary }}>
              Track your delivery payouts and stats 📈
            </ThemedText>
          </View>

          <View style={styles.statsRow}>
            <View style={[styles.statBox, { backgroundColor: colors.backgroundElement }]}>
              <ThemedText type="small" style={{ color: colors.textSecondary }}>Total Earnings</ThemedText>
              <ThemedText style={[styles.statValue, { color: colors.cta }]}>₹{totalEarnings.toFixed(2)}</ThemedText>
            </View>

            <View style={[styles.statBox, { backgroundColor: colors.backgroundElement }]}>
              <ThemedText type="small" style={{ color: colors.textSecondary }}>Deliveries</ThemedText>
              <ThemedText style={[styles.statValue, { color: colors.primary }]}>{totalDeliveries}</ThemedText>
            </View>
          </View>

          {screenError ? (
            <View style={[styles.errorBanner, { borderColor: colors.warning }]}>
              <ThemedText style={{ color: colors.warning, fontWeight: '700' }}>{screenError}</ThemedText>
            </View>
          ) : null}

          <View style={styles.listHeader}>
            <ThemedText style={{ fontWeight: '700' }}>Recent Delivery Transactions</ThemedText>
          </View>

          {loading ? (
            <View style={styles.loadingContainer}>
              <ActivityIndicator size="large" />
            </View>
          ) : (
            <FlatList
              data={earnings}
              keyExtractor={(item) => item.earningId}
              onRefresh={handleRefresh}
              refreshing={refreshing}
              renderItem={({ item }) => (
                <View style={[styles.earningItem, { borderBottomColor: colors.backgroundSelected }]}>
                  <View>
                    <ThemedText style={{ fontWeight: '600' }}>Order #{item.orderId.split('-').pop() || item.orderId}</ThemedText>
                    <ThemedText type="small" style={{ color: colors.textSecondary, marginTop: Spacing.one }}>
                      {new Date(item.earnedAt).toLocaleString()}
                    </ThemedText>
                    <ThemedText type="small" style={{ color: item.payoutId ? colors.cta : colors.textSecondary, marginTop: Spacing.one }}>
                      {item.payoutId ? `Payout linked #${item.payoutId.split('-').pop()}` : 'Awaiting payout batch'}
                    </ThemedText>
                  </View>
                  <ThemedText style={{ fontWeight: '700', color: colors.cta }}>
                    +₹{item.amount.toFixed(2)}
                  </ThemedText>
                </View>
              )}
              ListEmptyComponent={
                <View style={styles.centered}>
                  <ThemedText style={{ color: colors.textSecondary }}>No earnings recorded yet.</ThemedText>
                </View>
              }
            />
          )}
        </SafeAreaView>
      </ThemedView>
    );
  }

  // Rendering for Merchant/Provider View
  const merchantTotalPayout = payouts.reduce((sum, item) => sum + item.amount, 0);

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        {/* Provider Selector Header */}
        <View style={styles.providerHeader}>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.providersScroll}>
            {providers.map((p) => (
              <TouchableOpacity
                key={p.id}
                onPress={() => setSelectedProvider(p)}
                style={[
                  styles.providerTab,
                  { backgroundColor: selectedProvider?.id === p.id ? colors.primary : colors.backgroundElement }
                ]}>
                <ThemedText style={{ color: selectedProvider?.id === p.id ? '#fff' : colors.text, fontWeight: '600' }}>
                  {p.label}
                </ThemedText>
              </TouchableOpacity>
            ))}
            {providers.length === 0 ? (
              <ThemedText style={{ color: colors.textSecondary }}>No approved providers found.</ThemedText>
            ) : null}
          </ScrollView>
        </View>

        <View style={styles.header}>
          <ThemedText type="subtitle">Merchant Payouts & Coupons</ThemedText>
          <ThemedText type="small" style={{ color: colors.textSecondary }}>
            Manage payouts history and discount war prevention coupons
          </ThemedText>
        </View>

        <View style={styles.statsRow}>
          <View style={[styles.statBox, { backgroundColor: colors.backgroundElement }]}>
            <ThemedText type="small" style={{ color: colors.textSecondary }}>Total Payouts</ThemedText>
            <ThemedText style={[styles.statValue, { color: colors.cta }]}>₹{merchantTotalPayout.toFixed(2)}</ThemedText>
          </View>
          <View style={[styles.statBox, { backgroundColor: colors.backgroundElement }]}>
            <ThemedText type="small" style={{ color: colors.textSecondary }}>Active Coupons</ThemedText>
            <ThemedText style={[styles.statValue, { color: colors.primary }]}>{promotions.length}</ThemedText>
          </View>
        </View>

        {screenError ? (
          <View style={[styles.errorBanner, { borderColor: colors.warning }]}>
            <ThemedText style={{ color: colors.warning, fontWeight: '700' }}>{screenError}</ThemedText>
          </View>
        ) : null}

        <View style={styles.listHeaderRow}>
          <ThemedText style={{ fontWeight: '700' }}>Payout History</ThemedText>
        </View>

        {loading ? (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" />
          </View>
        ) : (
          <ScrollView contentContainerStyle={{ paddingBottom: Spacing.four }}>
            {payouts.length === 0 ? (
              <View style={styles.centered}>
                <ThemedText style={{ color: colors.textSecondary }}>No payouts recorded.</ThemedText>
              </View>
            ) : (
              payouts.map((item) => (
                <View key={item.payoutId} style={[styles.earningItem, { borderBottomColor: colors.backgroundSelected }]}>
                  <View>
                    <ThemedText style={{ fontWeight: '600' }}>Payout #{item.payoutId.split('-').pop()}</ThemedText>
                    <ThemedText type="small" style={{ color: colors.textSecondary, marginTop: Spacing.one }}>
                      Period: {item.periodStart} to {item.periodEnd}
                    </ThemedText>
                  </View>
                  <View style={{ alignItems: 'flex-end' }}>
                    <ThemedText style={{ fontWeight: '700', color: colors.cta }}>₹{item.amount.toFixed(2)}</ThemedText>
                    <ThemedText type="small" style={{ color: item.status === 'PAID' ? colors.cta : '#eab308' }}>
                      {item.status}
                    </ThemedText>
                  </View>
                </View>
              ))
            )}

            <View style={styles.listHeaderRow}>
              <ThemedText style={{ fontWeight: '700' }}>Active Coupons (Discount War Prevention)</ThemedText>
              <TouchableOpacity onPress={() => setShowPromoModal(true)} style={[styles.addButton, { backgroundColor: colors.primary }]}>
                <ThemedText style={{ color: '#fff', fontSize: 13, fontWeight: '700' }}>+ Create Coupon</ThemedText>
              </TouchableOpacity>
            </View>

            {promotions.length === 0 ? (
              <View style={styles.centered}>
                <ThemedText style={{ color: colors.textSecondary }}>No active promotions.</ThemedText>
              </View>
            ) : (
              promotions.map((promo) => (
                <View key={promo.promotionId ?? promo.code} style={[styles.earningItem, { borderBottomColor: colors.backgroundSelected }]}>
                  <View>
                    <ThemedText style={{ fontWeight: '700', color: colors.primary }}>{promo.code}</ThemedText>
                    <ThemedText type="small" style={{ color: colors.textSecondary }}>
                      {promo.discountType === 'PERCENTAGE' ? `${promo.discountValue}% Off` : `₹${promo.discountValue} Off`}
                      {promo.applicableCategory ? ` on ${promo.applicableCategory}` : ''}
                    </ThemedText>
                    <ThemedText type="small" style={{ color: colors.textSecondary }}>
                      Min Order: ₹{promo.minOrderValue}
                    </ThemedText>
                  </View>
                  <View style={{ alignItems: 'flex-end' }}>
                    <ThemedText type="small" style={{ color: colors.cta }}>ACTIVE</ThemedText>
                  </View>
                </View>
              ))
            )}
          </ScrollView>
        )}

        {/* Create Promotion Modal */}
        <Modal visible={showPromoModal} animationType="slide" transparent>
          <View style={styles.modalOverlay}>
            <ThemedView style={[styles.modalContent, { backgroundColor: colors.background }]}>
              <ThemedText type="subtitle" style={{ marginBottom: Spacing.three }}>Create Promotion Coupon</ThemedText>
              
              {errorMsg ? (
                <ThemedText style={{ color: '#ef4444', marginBottom: Spacing.two, fontWeight: '600' }}>⚠️ {errorMsg}</ThemedText>
              ) : null}

              <ScrollView style={{ width: '100%' }}>
                <ThemedText type="small" style={{ color: colors.textSecondary, marginBottom: Spacing.one }}>Coupon Code (e.g. DROOLS10)</ThemedText>
                <TextInput
                  value={promoCode}
                  onChangeText={setPromoCode}
                  style={[styles.input, { borderColor: colors.backgroundSelected, color: colors.text }]}
                  placeholder="CODE"
                  placeholderTextColor={colors.textSecondary}
                  autoCapitalize="characters"
                />

                <ThemedText type="small" style={{ color: colors.textSecondary, marginBottom: Spacing.one }}>Discount Type</ThemedText>
                <View style={styles.typeSelectorRow}>
                  <TouchableOpacity
                    onPress={() => setDiscountType('PERCENTAGE')}
                    style={[styles.typeButton, { borderColor: colors.primary, backgroundColor: discountType === 'PERCENTAGE' ? colors.primary : 'transparent' }]}>
                    <ThemedText style={{ color: discountType === 'PERCENTAGE' ? '#fff' : colors.text }}>Percentage</ThemedText>
                  </TouchableOpacity>
                  <TouchableOpacity
                    onPress={() => setDiscountType('FLAT')}
                    style={[styles.typeButton, { borderColor: colors.primary, backgroundColor: discountType === 'FLAT' ? colors.primary : 'transparent' }]}>
                    <ThemedText style={{ color: discountType === 'FLAT' ? '#fff' : colors.text }}>Flat Amount</ThemedText>
                  </TouchableOpacity>
                </View>

                <ThemedText type="small" style={{ color: colors.textSecondary, marginBottom: Spacing.one }}>Discount Value (Max 30% / 30% of min order)</ThemedText>
                <TextInput
                  value={discountValue}
                  onChangeText={setDiscountValue}
                  style={[styles.input, { borderColor: colors.backgroundSelected, color: colors.text }]}
                  placeholder="e.g. 10"
                  placeholderTextColor={colors.textSecondary}
                  keyboardType="numeric"
                />

                <ThemedText type="small" style={{ color: colors.textSecondary, marginBottom: Spacing.one }}>Minimum Order Value (Required, must be at least 1.5x discount)</ThemedText>
                <TextInput
                  value={minOrderValue}
                  onChangeText={setMinOrderValue}
                  style={[styles.input, { borderColor: colors.backgroundSelected, color: colors.text }]}
                  placeholder="e.g. 500"
                  placeholderTextColor={colors.textSecondary}
                  keyboardType="numeric"
                />

                <ThemedText type="small" style={{ color: colors.textSecondary, marginBottom: Spacing.one }}>Max Discount Amount (Optional for percentage)</ThemedText>
                <TextInput
                  value={maxDiscountAmount}
                  onChangeText={setMaxDiscountAmount}
                  style={[styles.input, { borderColor: colors.backgroundSelected, color: colors.text }]}
                  placeholder="e.g. 100"
                  placeholderTextColor={colors.textSecondary}
                  keyboardType="numeric"
                />

                <ThemedText type="small" style={{ color: colors.textSecondary, marginBottom: Spacing.one }}>Applicable Category Filter (Optional, e.g. Drools)</ThemedText>
                <TextInput
                  value={applicableCategory}
                  onChangeText={setApplicableCategory}
                  style={[styles.input, { borderColor: colors.backgroundSelected, color: colors.text }]}
                  placeholder="e.g. Drools"
                  placeholderTextColor={colors.textSecondary}
                />
              </ScrollView>

              <View style={styles.modalActions}>
                <TouchableOpacity onPress={() => setShowPromoModal(false)} style={[styles.cancelButton, { backgroundColor: colors.backgroundElement }]}>
                  <ThemedText style={{ color: colors.text, fontWeight: '700' }}>Cancel</ThemedText>
                </TouchableOpacity>
                <TouchableOpacity onPress={handleCreatePromotion} style={[styles.submitButton, { backgroundColor: colors.primary }]}>
                  <ThemedText style={{ color: '#fff', fontWeight: '700' }}>Create</ThemedText>
                </TouchableOpacity>
              </View>
            </ThemedView>
          </View>
        </Modal>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
  },
  header: {
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.two,
    marginBottom: Spacing.three,
  },
  providerHeader: {
    paddingTop: Spacing.three,
    paddingBottom: Spacing.two,
    borderBottomWidth: 1,
    borderBottomColor: '#ccc',
  },
  providersScroll: {
    paddingHorizontal: Spacing.four,
    gap: Spacing.two,
    flexDirection: 'row',
  },
  providerTab: {
    paddingVertical: Spacing.one * 1.5,
    paddingHorizontal: Spacing.three,
    borderRadius: 20,
  },
  statsRow: {
    flexDirection: 'row',
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    marginBottom: Spacing.four,
  },
  statBox: {
    flex: 1,
    padding: Spacing.three,
    borderRadius: Spacing.two,
    gap: Spacing.one,
  },
  statValue: {
    fontSize: 22,
    fontWeight: 'bold',
  },
  listHeader: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.two,
  },
  listHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two,
    marginTop: Spacing.two,
  },
  addButton: {
    paddingVertical: Spacing.one * 1.5,
    paddingHorizontal: Spacing.two,
    borderRadius: Spacing.one,
  },
  earningItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.three,
    borderBottomWidth: 1,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  centered: {
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.six,
  },
  errorBanner: {
    marginHorizontal: Spacing.four,
    marginBottom: Spacing.three,
    borderWidth: 1,
    borderRadius: Spacing.one,
    padding: Spacing.two,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: Spacing.four,
  },
  modalContent: {
    width: '100%',
    maxHeight: '80%',
    borderRadius: Spacing.two,
    padding: Spacing.four,
    alignItems: 'center',
  },
  input: {
    width: '100%',
    height: 48,
    borderWidth: 1,
    borderRadius: Spacing.one,
    paddingHorizontal: Spacing.two,
    marginBottom: Spacing.three,
    fontSize: 16,
  },
  typeSelectorRow: {
    flexDirection: 'row',
    gap: Spacing.two,
    marginBottom: Spacing.three,
    width: '100%',
  },
  typeButton: {
    flex: 1,
    height: 44,
    borderWidth: 1,
    borderRadius: Spacing.one,
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalActions: {
    flexDirection: 'row',
    width: '100%',
    gap: Spacing.three,
    marginTop: Spacing.four,
  },
  cancelButton: {
    flex: 1,
    height: 48,
    borderRadius: Spacing.one,
    justifyContent: 'center',
    alignItems: 'center',
  },
  submitButton: {
    flex: 1,
    height: 48,
    borderRadius: Spacing.one,
    justifyContent: 'center',
    alignItems: 'center',
  }
});
