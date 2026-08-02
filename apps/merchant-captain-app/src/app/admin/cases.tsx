import React, { useCallback, useEffect, useState } from 'react';
import { Alert, RefreshControl, StyleSheet, View } from 'react-native';

import {
  ActionButton,
  AppBar,
  RoleBadge,
  SectionHeader,
  StateView,
  StatusBadge,
} from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { AppCard } from '@/components/ui/app-card';
import { TextField } from '@/components/ui/text-field';
import { type AdminDispute } from '@/contracts/admin-operations';
import { apiErrorMessage } from '@/contracts/api-error';
import { useAuth } from '@/context/AuthContext';
import { spacing, typography } from '@/design/tokens';
import { fetchAdminDisputes, resolveAdminDispute } from '@/services/admin-operations';
import { formatDateTime, formatStatusLabel } from '@/utils/formatters';

export default function AdminCustomerCasesScreen() {
  const { role, session } = useAuth();
  const [cases, setCases] = useState<AdminDispute[]>([]);
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);

  const load = useCallback(async () => {
    if (!session || role !== 'ADMIN') {
      setLoading(false);
      return;
    }
    setError(null);
    try {
      setCases(await fetchAdminDisputes(session.access_token));
    } catch (nextError) {
      setError(nextError);
    } finally {
      setLoading(false);
    }
  }, [role, session]);

  useEffect(() => {
    void load();
  }, [load]);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  }, [load]);

  const decide = useCallback(async (
    customerCase: AdminDispute,
    decision: 'RESOLVED' | 'REJECTED',
    issueRefund: boolean,
  ) => {
    if (!session) return;
    const resolutionNotes = notes[customerCase.disputeId]?.trim();
    if (!resolutionNotes) {
      Alert.alert('Resolution notes required', 'Record the evidence review and decision before closing the case.');
      return;
    }
    setBusyId(customerCase.disputeId);
    try {
      const updated = await resolveAdminDispute(
        customerCase.disputeId,
        decision,
        resolutionNotes,
        session.access_token,
        issueRefund,
      );
      setCases((current) => current.map((item) => item.disputeId === updated.disputeId ? updated : item));
      Alert.alert(
        issueRefund ? 'Refund initiated' : 'Case updated',
        issueRefund
          ? 'The payment module accepted the refund request. Track the server refund status before telling the customer it is complete.'
          : `Case status is now ${decision}.`,
      );
    } catch (nextError) {
      Alert.alert('Case update failed', apiErrorMessage(nextError));
    } finally {
      setBusyId(null);
    }
  }, [notes, session]);

  if (role !== 'ADMIN') {
    return (
      <ScreenShell header={<AppBar title="Customer cases" action={<RoleBadge role="admin" />} />}>
        <StateView kind="unauthorized" title="Administrator access required" />
      </ScreenShell>
    );
  }

  if (loading) {
    return (
      <ScreenShell header={<AppBar title="Customer cases" action={<RoleBadge role="admin" />} />}>
        <StateView kind="loading" title="Loading customer cases" />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell
      testID="admin-customer-case-queue"
      header={<AppBar title="Customer cases" subtitle="Evidence review, resolution and refund initiation" action={<RoleBadge role="admin" />} />}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
    >
      {error ? <StateView kind="error" title="Cases unavailable" message={apiErrorMessage(error)} actionLabel="Retry" onAction={() => void load()} /> : null}
      <SectionHeader title="Case queue" subtitle={`${cases.filter((item) => item.status === 'OPEN' || item.status === 'UNDER_REVIEW').length} active`} />
      {!error && cases.length === 0 ? <StateView kind="empty" title="No customer cases" /> : cases.map((customerCase) => (
        <AppCard key={customerCase.disputeId} style={styles.card}>
          <View style={styles.headerRow}>
            <View style={styles.flex}>
              <ThemedText style={styles.title}>{formatStatusLabel(customerCase.caseType ?? 'CUSTOMER_CASE')}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                Order {customerCase.orderId.slice(0, 8).toUpperCase()} · {formatDateTime(customerCase.createdAt ?? undefined)}
              </ThemedText>
            </View>
            <StatusBadge label={formatStatusLabel(customerCase.status)} tone={customerCase.status === 'RESOLVED' ? 'success' : customerCase.status === 'REJECTED' ? 'danger' : 'warning'} />
          </View>
          <ThemedText>{customerCase.reason}</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            Evidence {customerCase.evidenceCount ?? 0} · Refund {formatStatusLabel(customerCase.refundStatus ?? 'NOT_APPLICABLE')}
          </ThemedText>
          {customerCase.status === 'OPEN' || customerCase.status === 'UNDER_REVIEW' ? (
            <>
              <TextField
                label="Evidence review and resolution notes"
                value={notes[customerCase.disputeId] ?? ''}
                onChangeText={(value) => setNotes((current) => ({ ...current, [customerCase.disputeId]: value }))}
                multiline
              />
              <View style={styles.actions}>
                <ActionButton
                  label="Resolve"
                  icon="check"
                  loading={busyId === customerCase.disputeId}
                  onPress={() => void decide(customerCase, 'RESOLVED', false)}
                  style={styles.flex}
                />
                <ActionButton
                  label="Resolve + refund"
                  icon="billing"
                  disabled={busyId === customerCase.disputeId}
                  onPress={() => void decide(customerCase, 'RESOLVED', true)}
                  style={styles.flex}
                />
                <ActionButton
                  label="Reject"
                  icon="xmark"
                  variant="destructive"
                  disabled={busyId === customerCase.disputeId}
                  onPress={() => void decide(customerCase, 'REJECTED', false)}
                  style={styles.flex}
                />
              </View>
            </>
          ) : customerCase.resolutionNotes ? (
            <ThemedText type="small" themeColor="textSecondary">Resolution: {customerCase.resolutionNotes}</ThemedText>
          ) : null}
        </AppCard>
      ))}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing.x3 },
  headerRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.x3 },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x3 },
  flex: { flex: 1 },
  title: { ...typography.title },
});
