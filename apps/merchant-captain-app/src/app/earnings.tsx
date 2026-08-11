import React, { useState, useEffect, useCallback } from 'react';
import { 
  StyleSheet, 
  View, 
  FlatList, 
  ActivityIndicator, 
  TouchableOpacity,
  Modal,
  TextInput,
  ScrollView,
  Alert
} from 'react-native';

import { AppIcon } from '@/components/app-icon';
import {
  ActionButton,
  AppBar,
  FeedbackBanner,
  FilterChip,
  MetricCard,
  RoleBadge,
  SectionHeader,
  StateView,
  StatusBadge,
} from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { AppCard } from '@/components/ui/app-card';
import { BottomTabInset } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';
import { radii, shadows, spacing, touchTarget, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { appConfig } from '@/utils/app-config';
import { formatCurrency, formatDateTime } from '@/utils/formatters';

const DEMO_PROVIDERS = [
  {
    id: 'e1b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
    label: 'Pet Store',
    fulfillmentType: 'DELIVERY',
  },
  {
    id: 'e2b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
    label: 'Groomer',
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
    payoutId: 'payout-demo-1001',
    payeeUserId: 'usr-1',
    payeeRole: 'MERCHANT',
    amount: 4850.00,
    status: 'PAID',
    periodStart: new Date(Date.now() - 3600 * 1000 * 24 * 14).toISOString(),
    periodEnd: new Date(Date.now() - 3600 * 1000 * 24 * 7).toISOString(),
    paidAt: new Date(Date.now() - 3600 * 1000 * 24 * 5).toISOString(),
    createdAt: new Date(Date.now() - 3600 * 1000 * 24 * 7).toISOString(),
  },
];

const MOCK_PROMOTIONS: Promotion[] = [
  {
    promotionId: 'promo-1',
    providerId: 'demo-provider',
    code: 'PET10',
    discountType: 'PERCENTAGE',
    discountValue: 10,
    maxDiscountAmount: 100,
    minOrderValue: 500,
    applicableCategory: 'Food',
    validFrom: new Date().toISOString(),
    validUntil: new Date(Date.now() + 30 * 24 * 3600 * 1000).toISOString(),
    isActive: true,
  },
];

export default function EarningsScreen() {
  const theme = useTheme();
  const { user, session, activeRole } = useAuth();
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [screenError, setScreenError] = useState('');

  const [earnings, setEarnings] = useState<EarningRecord[]>([]);

  const [providers, setProviders] = useState<ProviderOption[]>(appConfig.allowDemoMode ? DEMO_PROVIDERS : []);
  const [selectedProvider, setSelectedProvider] = useState<ProviderOption | null>(appConfig.allowDemoMode ? DEMO_PROVIDERS[0] : null);
  const [payouts, setPayouts] = useState<PayoutRecord[]>([]);
  const [promotions, setPromotions] = useState<Promotion[]>([]);
  
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

  const fetchMerchantData = useCallback(async (showLoader = true) => {
    if (!user || !selectedProvider) return;
    if (showLoader) setLoading(true);
    setScreenError('');
    try {
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
      validUntil: new Date(Date.now() + 30 * 24 * 3600 * 1000).toISOString(),
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

  if (activeRole !== 'PROVIDER') {
    const totalEarnings = earnings.reduce((sum, item) => sum + item.amount, 0);
    const totalDeliveries = earnings.length;

    return (
      <ScreenShell
        header={
          <AppBar
            eyebrow="CAPTAIN WORKSPACE"
            title="Earnings & Payouts"
            subtitle="Track delivery payouts and settlement status"
            action={<RoleBadge role="captain" />}
          />
        }
        testID="captain-earnings"
      >
        <View style={styles.metricGrid}>
          <MetricCard
            label="Total Earnings"
            value={formatCurrency(totalEarnings)}
            icon="wallet"
            tone="primary"
            style={styles.metricHalf}
          />
          <MetricCard
            label="Completed Deliveries"
            value={String(totalDeliveries)}
            icon="truck"
            tone="success"
            style={styles.metricHalf}
          />
        </View>

        {screenError ? (
          <FeedbackBanner tone="warning" title="Earnings notice" message={screenError} icon="dispute" />
        ) : null}

        <AppCard style={styles.sectionCard}>
          <SectionHeader title="Recent delivery payouts" subtitle="Automatic weekly settlements" />
          {loading ? (
            <StateView kind="loading" title="Loading earnings" message="Fetching delivery ledger..." />
          ) : earnings.length === 0 ? (
            <StateView kind="empty" title="No earnings recorded" message="Complete delivery trips to see your earnings here." />
          ) : (
            <View style={styles.historyList}>
              {earnings.map((item) => (
                <View key={item.earningId} style={[styles.historyRow, { borderColor: theme.border }]}>
                  <View style={styles.historyMeta}>
                    <ThemedText type="smallBold">Order #{item.orderId.split('-').pop() || item.orderId}</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">{formatDateTime(item.earnedAt)}</ThemedText>
                  </View>
                  <View style={styles.historyPrice}>
                    <ThemedText type="smallBold" style={{ color: theme.success }}>+{formatCurrency(item.amount)}</ThemedText>
                    <StatusBadge label={item.payoutId ? 'Paid out' : 'Pending Batch'} tone={item.payoutId ? 'success' : 'warning'} />
                  </View>
                </View>
              ))}
            </View>
          )}
        </AppCard>
      </ScreenShell>
    );
  }

  const merchantTotalPayout = payouts.reduce((sum, item) => sum + item.amount, 0);

  return (
    <ScreenShell
      header={
        <AppBar
          eyebrow="MERCHANT WORKSPACE"
          title="Payouts & Coupons"
          subtitle="Manage settlement history and GST Section 52 compliance"
          action={<RoleBadge role="merchant" />}
        />
      }
      testID="merchant-earnings"
    >
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.providersScroll}>
        {providers.map((p) => (
          <FilterChip
            key={p.id}
            label={p.label}
            selected={selectedProvider?.id === p.id}
            onPress={() => setSelectedProvider(p)}
          />
        ))}
      </ScrollView>

      <FeedbackBanner
        tone="info"
        title="Platform GST & TCS Section 52 Notice"
        message="1% TCS (0.5% CGST + 0.5% SGST) is deducted at source on net taxable supplies. Monthly GSTR-8 reports are generated for compliance."
        icon="shield"
      />

      <View style={styles.metricGrid}>
        <MetricCard
          label="Total Payouts"
          value={formatCurrency(merchantTotalPayout)}
          icon="wallet"
          tone="primary"
          style={styles.metricHalf}
        />
        <MetricCard
          label="Active Coupons"
          value={String(promotions.length)}
          icon="sparkle"
          tone="accent"
          style={styles.metricHalf}
        />
      </View>

      {screenError ? (
        <FeedbackBanner tone="warning" title="Payout notice" message={screenError} icon="dispute" />
      ) : null}

      <AppCard style={styles.sectionCard}>
        <SectionHeader title="Payout History" subtitle="Verified bank transfers and payouts" />
        {loading ? (
          <StateView kind="loading" title="Loading payouts" message="Fetching settlement ledger..." />
        ) : payouts.length === 0 ? (
          <StateView kind="empty" title="No payouts recorded" message="Payout settlements will appear here after order completion." />
        ) : (
          <View style={styles.historyList}>
            {payouts.map((item) => (
              <View key={item.payoutId} style={[styles.historyRow, { borderColor: theme.border }]}>
                <View style={styles.historyMeta}>
                  <ThemedText type="smallBold">Payout #{item.payoutId.split('-').pop()}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    Period: {formatDateTime(item.periodStart)} to {formatDateTime(item.periodEnd)}
                  </ThemedText>
                </View>
                <View style={styles.historyPrice}>
                  <ThemedText type="smallBold" style={{ color: theme.primary }}>{formatCurrency(item.amount)}</ThemedText>
                  <StatusBadge label={item.status} tone={item.status === 'PAID' ? 'success' : 'warning'} />
                </View>
              </View>
            ))}
          </View>
        )}
      </AppCard>

      <AppCard style={styles.sectionCard}>
        <SectionHeader
          title="Active Coupons"
          subtitle="Discount war prevention rules (Max 30%)"
          actionLabel="+ Create Coupon"
          onAction={() => setShowPromoModal(true)}
        />
        {promotions.length === 0 ? (
          <StateView kind="empty" title="No active promotions" message="Create coupons with minimum order values to boost sales." />
        ) : (
          <View style={styles.historyList}>
            {promotions.map((promo) => (
              <View key={promo.promotionId ?? promo.code} style={[styles.historyRow, { borderColor: theme.border }]}>
                <View style={styles.historyMeta}>
                  <ThemedText type="smallBold" style={{ color: theme.primary }}>{promo.code}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {promo.discountType === 'PERCENTAGE' ? `${promo.discountValue}% Off` : `₹${promo.discountValue} Off`}
                    {promo.applicableCategory ? ` on ${promo.applicableCategory}` : ''} · Min Order: {formatCurrency(promo.minOrderValue ?? 0)}
                  </ThemedText>
                </View>
                <StatusBadge label="ACTIVE" tone="success" />
              </View>
            ))}
          </View>
        )}
      </AppCard>

      {/* Create Promotion Modal */}
      <Modal visible={showPromoModal} animationType="slide" transparent onRequestClose={() => setShowPromoModal(false)}>
        <View style={styles.modalOverlay}>
          <View style={[styles.modalCard, { backgroundColor: theme.backgroundElement }]}>
            <SectionHeader title="Create Promotion Coupon" subtitle="Set discount rules & minimum order threshold" />

            {errorMsg ? (
              <FeedbackBanner tone="danger" title="Invalid promotion settings" message={errorMsg} icon="dispute" />
            ) : null}

            <ScrollView style={{ maxHeight: 380 }}>
              <View style={styles.formGroup}>
                <ThemedText type="smallBold">Coupon Code (e.g. PET10)</ThemedText>
                <TextInput
                  value={promoCode}
                  onChangeText={setPromoCode}
                  style={[styles.input, { borderColor: theme.border, color: theme.text, backgroundColor: theme.muted }]}
                  placeholder="CODE"
                  placeholderTextColor={theme.textSecondary}
                  autoCapitalize="characters"
                />

                <ThemedText type="smallBold">Discount Value</ThemedText>
                <TextInput
                  value={discountValue}
                  onChangeText={setDiscountValue}
                  style={[styles.input, { borderColor: theme.border, color: theme.text, backgroundColor: theme.muted }]}
                  placeholder="Percentage (e.g. 10)"
                  placeholderTextColor={theme.textSecondary}
                  keyboardType="numeric"
                />

                <ThemedText type="smallBold">Minimum Order Value (Required)</ThemedText>
                <TextInput
                  value={minOrderValue}
                  onChangeText={setMinOrderValue}
                  style={[styles.input, { borderColor: theme.border, color: theme.text, backgroundColor: theme.muted }]}
                  placeholder="Min order amount in ₹ (e.g. 500)"
                  placeholderTextColor={theme.textSecondary}
                  keyboardType="numeric"
                />
              </View>
            </ScrollView>

            <View style={styles.modalActions}>
              <ActionButton label="Cancel" variant="secondary" onPress={() => setShowPromoModal(false)} style={{ flex: 1 }} />
              <ActionButton label="Create Coupon" onPress={handleCreatePromotion} style={{ flex: 1 }} />
            </View>
          </View>
        </View>
      </Modal>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  providersScroll: {
    gap: spacing.x2,
    paddingBottom: spacing.x2,
  },
  metricGrid: {
    flexDirection: 'row',
    gap: spacing.x3,
  },
  metricHalf: {
    flex: 1,
  },
  sectionCard: {
    padding: spacing.x4,
    gap: spacing.x3,
  },
  historyList: {
    gap: spacing.x2,
  },
  historyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: spacing.x3,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  historyMeta: {
    flex: 1,
    gap: 2,
  },
  historyPrice: {
    alignItems: 'flex-end',
    gap: 4,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'flex-end',
  },
  modalCard: {
    borderTopLeftRadius: radii.feature,
    borderTopRightRadius: radii.feature,
    padding: spacing.x6,
    paddingBottom: BottomTabInset + spacing.x6,
    gap: spacing.x4,
  },
  formGroup: {
    gap: spacing.x3,
  },
  input: {
    borderWidth: 1,
    borderRadius: radii.compact,
    padding: spacing.x3,
    minHeight: touchTarget,
    ...typography.body,
  },
  modalActions: {
    flexDirection: 'row',
    gap: spacing.x3,
    marginTop: spacing.x2,
  },
});
