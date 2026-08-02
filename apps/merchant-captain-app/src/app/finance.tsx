import React, { useCallback, useEffect, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';

import {
  ActionButton,
  AppBar,
  FeedbackBanner,
  FilterChip,
  MetricCard,
  RoleBadge,
  StateView,
  StatusBadge,
} from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { useAuth } from '@/context/AuthContext';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import {
  fetchMerchantFinance,
  type MerchantFinanceSummary,
} from '@/services/merchant-finance';
import {
  fetchMerchantProviders,
  type MerchantProvider,
} from '@/services/merchant-appointments';
import {
  formatCurrency,
  formatDate,
  formatPercentage,
  formatStatusLabel,
} from '@/utils/formatters';

function payoutTone(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'PAID') return 'success';
  if (status === 'FAILED' || status === 'REVERSED') return 'danger';
  if (status === 'PROCESSING' || status === 'PENDING') return 'warning';
  return 'info';
}

export default function MerchantFinanceScreen() {
  const theme = useTheme();
  const { activeRole } = useAuth();
  const [providers, setProviders] = useState<MerchantProvider[]>([]);
  const [selectedProviderId, setSelectedProviderId] = useState<string | null>(null);
  const [summary, setSummary] = useState<MerchantFinanceSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadProviders = useCallback(async () => {
    const liveProviders = await fetchMerchantProviders();
    setProviders(liveProviders);
    setSelectedProviderId((current) =>
      liveProviders.some((provider) => provider.providerId === current)
        ? current
        : liveProviders[0]?.providerId ?? null,
    );
  }, []);

  const loadSummary = useCallback(async (providerId: string) => {
    setLoading(true);
    try {
      setSummary(await fetchMerchantFinance(providerId));
      setError(null);
    } catch (cause: unknown) {
      setSummary(null);
      setError(cause instanceof Error ? cause.message : 'Could not load merchant finance.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (activeRole !== 'PROVIDER') {
      setLoading(false);
      return;
    }
    void loadProviders().catch((cause: unknown) => {
      setError(cause instanceof Error ? cause.message : 'Could not load providers.');
      setLoading(false);
    });
  }, [activeRole, loadProviders]);

  useEffect(() => {
    if (selectedProviderId) void loadSummary(selectedProviderId);
  }, [loadSummary, selectedProviderId]);

  if (activeRole !== 'PROVIDER') {
    return (
      <ScreenShell scroll={false} header={<AppBar title="Finance" />}>
        <StateView kind="unauthorized" title="Merchant access required" message="Captain earnings remain available in the Earnings tab." />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell
      header={
        <AppBar
          eyebrow="Merchant finance"
          title="Revenue & payouts"
          subtitle="Provider revenue with merchant-account payout context"
          action={<RoleBadge role="merchant" />}
        />
      }
    >
      {providers.length > 0 ? (
        <View style={styles.section}>
          <ThemedText style={styles.sectionTitle}>Provider</ThemedText>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.providerRow}>
            {providers.map((provider) => (
              <FilterChip
                key={provider.providerId}
                label={provider.name}
                selected={selectedProviderId === provider.providerId}
                onPress={() => setSelectedProviderId(provider.providerId)}
                icon={provider.providerType === 'VET_HOSPITAL' ? 'medical' : 'groom'}
              />
            ))}
          </ScrollView>
        </View>
      ) : null}

      {loading ? (
        <StateView kind="loading" title="Calculating finance" message="Reading delivered orders, completed appointments and payout records…" />
      ) : error ? (
        <StateView
          kind="error"
          title="Finance unavailable"
          message={error}
          actionLabel="Retry"
          onAction={() => selectedProviderId && void loadSummary(selectedProviderId)}
        />
      ) : providers.length === 0 ? (
        <StateView kind="empty" title="No appointment provider" message="Activate a vet or grooming provider to view finance." />
      ) : summary ? (
        <>
          <FeedbackBanner
            title="Payout scope"
            message="Revenue cards are provider-specific. Paid and processing payout totals are for the full merchant account because one transfer can combine multiple providers."
            tone="info"
            icon="wallet"
          />

          <View style={styles.metricsGrid}>
            <MetricCard
              label="Gross revenue"
              value={formatCurrency(summary.totalGrossRevenue)}
              icon="wallet"
              hint={`${summary.deliveredOrderCount} delivered orders · ${summary.completedAppointmentCount} completed appointments`}
              style={styles.metric}
            />
            <MetricCard
              label="Net revenue"
              value={formatCurrency(summary.totalNetRevenue)}
              icon="check"
              tone="success"
              hint="After order commission; appointments currently pay at full service value"
              style={styles.metric}
            />
            <MetricCard
              label="Order commission"
              value={formatCurrency(summary.orderCommission)}
              icon="percent"
              tone="warning"
              hint={`${formatPercentage(summary.commissionPercent, 2)} configured commission`}
              style={styles.metric}
            />
            <MetricCard
              label="Appointment revenue"
              value={formatCurrency(summary.appointmentRevenue)}
              icon="calendar"
              tone="accent"
              hint={`${summary.completedAppointmentCount} completed services`}
              style={styles.metric}
            />
            <MetricCard
              label="Account paid out"
              value={formatCurrency(summary.accountPaidOut)}
              icon="wallet"
              tone="success"
              hint="All providers under this merchant account"
              style={styles.metric}
            />
            <MetricCard
              label="Payout in flight"
              value={formatCurrency(summary.accountPayoutInFlight)}
              icon="clock"
              tone="warning"
              hint="Pending and processing account payouts"
              style={styles.metric}
            />
          </View>

          <View style={styles.section}>
            <View style={styles.sectionHeader}>
              <ThemedText style={styles.sectionTitle}>Payout history</ThemedText>
              <ActionButton
                label="Refresh"
                variant="ghost"
                icon="history"
                onPress={() => selectedProviderId && void loadSummary(selectedProviderId)}
              />
            </View>

            {summary.payouts.length === 0 ? (
              <StateView kind="empty" title="No payouts yet" message="Payout records appear after a finance cycle is calculated." />
            ) : summary.payouts.map((payout) => (
              <View
                key={payout.payoutId}
                style={[
                  styles.payoutCard,
                  shadows.card,
                  { backgroundColor: theme.backgroundElement, borderColor: theme.border },
                ]}
              >
                <View style={styles.sectionHeader}>
                  <View style={styles.flex}>
                    <ThemedText style={styles.payoutAmount}>{formatCurrency(payout.amount)}</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      {formatDate(payout.periodStart)} – {formatDate(payout.periodEnd)}
                    </ThemedText>
                  </View>
                  <StatusBadge label={formatStatusLabel(payout.status)} tone={payoutTone(payout.status)} />
                </View>
                <ThemedText type="small" themeColor="textSecondary">
                  Created {formatDate(payout.createdAt)}{payout.paidAt ? ` · Paid ${formatDate(payout.paidAt)}` : ''}
                </ThemedText>
              </View>
            ))}
          </View>
        </>
      ) : null}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  section: { gap: spacing.x3 },
  sectionTitle: { ...typography.title },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.x3 },
  providerRow: { gap: spacing.x2, paddingRight: spacing.x4 },
  metricsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x3 },
  metric: { flexBasis: '47%', flexGrow: 1 },
  payoutCard: { borderWidth: StyleSheet.hairlineWidth, borderRadius: radii.card, padding: spacing.x4, gap: spacing.x2 },
  payoutAmount: { ...typography.title, fontWeight: '800' },
});
