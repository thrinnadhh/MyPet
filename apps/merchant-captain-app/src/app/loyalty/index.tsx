import React, { useCallback, useEffect, useState } from 'react';
import { Alert, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

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
import { TextField } from '@/components/ui/text-field';
import { apiErrorMessage } from '@/contracts/api-error';
import { useAuth } from '@/context/AuthContext';
import { spacing, typography } from '@/design/tokens';
import { fetchMerchantProviders, type MerchantProvider } from '@/services/merchant-inventory';
import {
  fetchMerchantLoyaltyAudit,
  fetchMerchantLoyaltyProgram,
  saveMerchantLoyaltyProgram,
  type MerchantLoyaltyAudit,
  type MerchantLoyaltyProgram,
} from '@/services/merchant-loyalty';
import { formatCurrency, formatDateTime, formatStatusLabel } from '@/utils/formatters';

const REWARD_AMOUNTS = [50, 100, 150, 200] as const;
const EXPIRY_OPTIONS = [30, 60, 90] as const;

export default function MerchantLoyaltyScreen() {
  const { role } = useAuth();
  const [providers, setProviders] = useState<MerchantProvider[]>([]);
  const [selectedProviderId, setSelectedProviderId] = useState<string | null>(null);
  const [program, setProgram] = useState<MerchantLoyaltyProgram | null>(null);
  const [audit, setAudit] = useState<MerchantLoyaltyAudit[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const loadProviders = useCallback(async () => {
    const owned = await fetchMerchantProviders();
    setProviders(owned);
    setSelectedProviderId((current) => current ?? owned[0]?.providerId ?? null);
  }, []);

  const loadProgram = useCallback(async (providerId: string) => {
    setError(null);
    const [nextProgram, nextAudit] = await Promise.all([
      fetchMerchantLoyaltyProgram(providerId),
      fetchMerchantLoyaltyAudit(providerId),
    ]);
    setProgram({ ...nextProgram, providerId, targetStars: 10, isStackable: true });
    setAudit(nextAudit);
  }, []);

  const load = useCallback(async () => {
    if (role !== 'PROVIDER') {
      setLoading(false);
      return;
    }
    try {
      await loadProviders();
    } catch (nextError) {
      setError(nextError);
    } finally {
      setLoading(false);
    }
  }, [loadProviders, role]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!selectedProviderId) return;
    setLoading(true);
    loadProgram(selectedProviderId)
      .catch(setError)
      .finally(() => setLoading(false));
  }, [loadProgram, selectedProviderId]);

  const refresh = useCallback(async () => {
    if (!selectedProviderId) return;
    setRefreshing(true);
    try {
      await Promise.all([loadProviders(), loadProgram(selectedProviderId)]);
    } catch (nextError) {
      setError(nextError);
    } finally {
      setRefreshing(false);
    }
  }, [loadProgram, loadProviders, selectedProviderId]);

  const save = useCallback(async () => {
    if (!program) return;
    if (!REWARD_AMOUNTS.includes(program.rewardAmount as (typeof REWARD_AMOUNTS)[number])) {
      Alert.alert('Invalid reward', 'Choose ₹50, ₹100, ₹150 or ₹200.');
      return;
    }
    if (program.minOrderValue < 0) {
      Alert.alert('Invalid minimum order', 'Minimum order value cannot be negative.');
      return;
    }
    setSaving(true);
    setNotice(null);
    try {
      const updated = await saveMerchantLoyaltyProgram(program);
      setProgram(updated);
      setAudit(await fetchMerchantLoyaltyAudit(program.providerId));
      setNotice('Loyalty settings saved for the selected store. The change was recorded in the server audit log.');
    } catch (nextError) {
      setError(nextError);
    } finally {
      setSaving(false);
    }
  }, [program]);

  if (role !== 'PROVIDER') {
    return (
      <ScreenShell header={<AppBar title="Store loyalty" action={<RoleBadge role="merchant" />} />}>
        <StateView kind="unauthorized" title="Merchant access required" message="Only an authenticated provider owner may change store loyalty settings." />
      </ScreenShell>
    );
  }

  if (loading && !program) {
    return (
      <ScreenShell header={<AppBar title="Store loyalty" action={<RoleBadge role="merchant" />} />}>
        <StateView kind="loading" title="Loading owned store settings" />
      </ScreenShell>
    );
  }

  if (error && !program) {
    return (
      <ScreenShell header={<AppBar title="Store loyalty" action={<RoleBadge role="merchant" />} />}>
        <StateView kind="error" title="Loyalty settings unavailable" message={apiErrorMessage(error)} actionLabel="Retry" onAction={() => void load()} />
      </ScreenShell>
    );
  }

  if (!selectedProviderId || providers.length === 0 || !program) {
    return (
      <ScreenShell header={<AppBar title="Store loyalty" action={<RoleBadge role="merchant" />} />}>
        <StateView kind="empty" title="No owned store found" message="Complete merchant onboarding and approval before configuring loyalty." />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell
      testID="merchant-loyalty-workspace"
      header={
        <AppBar
          eyebrow="MERCHANT GROWTH"
          title="Store loyalty"
          subtitle="One welcome star, purchase-earned stars and a configurable 10-star reward"
          action={<RoleBadge role="merchant" />}
        />
      }
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
    >
      {notice ? <FeedbackBanner title="Settings updated" message={notice} tone="success" /> : null}
      {error ? <FeedbackBanner title="Latest request failed" message={apiErrorMessage(error)} tone="danger" /> : null}

      <SectionHeader title="Owned provider" subtitle="The backend verifies provider ownership on every write." />
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.row}>
        {providers.map((provider) => (
          <FilterChip
            key={provider.providerId}
            label={provider.name}
            selected={selectedProviderId === provider.providerId}
            onPress={() => setSelectedProviderId(provider.providerId)}
          />
        ))}
      </ScrollView>

      <View style={styles.metrics}>
        <MetricCard label="Target" value="10 stars" icon="sparkle" />
        <MetricCard label="Reward" value={formatCurrency(program.rewardAmount)} icon="cart" tone="success" />
        <MetricCard label="Minimum order" value={formatCurrency(program.minOrderValue)} icon="inventory" />
      </View>

      <AppCard style={styles.card}>
        <SectionHeader title="Program status" />
        <View style={styles.row}>
          <FilterChip label="Active" selected={program.isActive} onPress={() => setProgram((current) => current ? { ...current, isActive: true } : current)} />
          <FilterChip label="Paused" selected={!program.isActive} onPress={() => setProgram((current) => current ? { ...current, isActive: false } : current)} />
          <FilterChip label="Welcome star enabled" selected={program.welcomeStarPolicy} onPress={() => setProgram((current) => current ? { ...current, welcomeStarPolicy: !current.welcomeStarPolicy } : current)} />
        </View>
        <ThemedText type="small" themeColor="textSecondary">
          The first star may be claimed once. All later stars require a completed qualifying order or appointment.
        </ThemedText>
      </AppCard>

      <AppCard style={styles.card}>
        <SectionHeader title="10-star reward" />
        <View style={styles.row}>
          {REWARD_AMOUNTS.map((amount) => (
            <FilterChip
              key={amount}
              label={`${formatCurrency(amount)} reward`}
              selected={program.rewardAmount === amount}
              onPress={() => setProgram((current) => current ? { ...current, rewardAmount: amount } : current)}
            />
          ))}
        </View>
        <ThemedText type="small" themeColor="textSecondary">
          The special coupon issued after 10 stars may be combined with one normal coupon. Ordinary loyalty discounts remain non-stackable.
        </ThemedText>
      </AppCard>

      <AppCard style={styles.card}>
        <TextField
          label="Minimum qualifying order (₹)"
          keyboardType="decimal-pad"
          value={String(program.minOrderValue)}
          onChangeText={(value) => setProgram((current) => current ? { ...current, minOrderValue: Number(value) || 0 } : current)}
        />
        <SectionHeader title="Reward expiry" />
        <View style={styles.row}>
          {EXPIRY_OPTIONS.map((days) => (
            <FilterChip key={days} label={`${days} days`} selected={program.expiryDays === days} onPress={() => setProgram((current) => current ? { ...current, expiryDays: days } : current)} />
          ))}
        </View>
      </AppCard>

      <ActionButton label="Save loyalty program" icon="check" loading={saving} onPress={() => void save()} />

      <SectionHeader title="Audit history" subtitle={`${audit.length} recorded changes`} />
      {audit.length === 0 ? (
        <StateView kind="empty" title="No loyalty changes recorded" />
      ) : audit.slice(0, 20).map((entry) => (
        <AppCard key={entry.auditId ?? `${entry.actorId}-${entry.createdAt}`} style={styles.card}>
          <View style={styles.headerRow}>
            <View style={styles.flex}>
              <ThemedText style={styles.title}>{formatStatusLabel(entry.action)}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">{formatDateTime(entry.createdAt)}</ThemedText>
            </View>
            <StatusBadge label="Recorded" tone="info" />
          </View>
          {entry.afterJson ? <ThemedText type="small" themeColor="textSecondary">{entry.afterJson}</ThemedText> : null}
        </AppCard>
      ))}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  metrics: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x3 },
  card: { gap: spacing.x3 },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: spacing.x3 },
  flex: { flex: 1 },
  title: { ...typography.title },
});
