import React, { useCallback, useEffect, useState } from 'react';
import { RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

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
import {
  type AdminAuditLog,
  type AdminDispute,
  type AdminOperationsSnapshot,
  type AdminProviderApproval,
  type AdminSection,
  type AdminServiceArea,
  type AdminSupportCase,
  validateServiceAreaDraft,
} from '@/contracts/admin-operations';
import { apiErrorMessage } from '@/contracts/api-error';
import { useAuth } from '@/context/AuthContext';
import { spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { approveCaptain, fetchPendingCaptains } from '@/services/captain-onboarding';
import { fetchBanners, fetchGuideWriters, revokeGuideWriter } from '@/services/content-admin';
import {
  approveProviderFromAdmin,
  fetchAdminAuditLogs,
  fetchAdminDisputes,
  fetchAdminOperationsSnapshot,
  fetchAdminServiceAreas,
  fetchAdminSupportCases,
  fetchPendingProviderApprovals,
  resolveAdminDispute,
  saveAdminServiceArea,
} from '@/services/admin-operations';
import { fetchPromotions, formatPromotionLabel, formatPromotionScope } from '@/services/promotions';
import { formatDateTime, formatDistance, formatStatusLabel } from '@/utils/formatters';

type CaptainApproval = {
  captainId: string;
  vehicleNumber?: string | null;
  status: string;
};

type ContentBanner = { id: string; title: string; active: boolean; durationSec: number };
type GuideWriter = { writerId: string; email: string; accessStatus: string };
type Promotion = {
  promotionId?: string;
  code: string;
  discountType: string;
  discountValue: number;
  providerId: string | null;
  applicableCategory: string | null;
  isActive: boolean;
};

const SECTIONS: Array<{ id: AdminSection | 'content'; label: string }> = [
  { id: 'overview', label: 'Overview' },
  { id: 'approvals', label: 'Approvals' },
  { id: 'service-areas', label: 'Service areas' },
  { id: 'disputes', label: 'Disputes' },
  { id: 'audit', label: 'Audit log' },
  { id: 'content', label: 'Content' },
];

function tone(status: string): 'neutral' | 'success' | 'warning' | 'danger' | 'info' {
  const normalized = status.toUpperCase();
  if (['ACTIVE', 'APPROVED', 'RESOLVED', 'LIVE', 'COMPLETED'].includes(normalized)) return 'success';
  if (['REJECTED', 'FAILED', 'SUSPENDED', 'DISABLED'].includes(normalized)) return 'danger';
  if (['OPEN', 'PENDING_APPROVAL', 'INFO_REQUESTED', 'PROCESSING'].includes(normalized)) return 'warning';
  return 'neutral';
}

function shortId(value?: string | null): string {
  if (!value) return '—';
  return value.length > 14 ? `${value.slice(0, 8)}…${value.slice(-4)}` : value;
}

export default function AdminOperationsScreen() {
  const theme = useTheme();
  const { session, role } = useAuth();
  const token = session?.access_token ?? null;

  const [section, setSection] = useState<AdminSection | 'content'>('overview');
  const [snapshot, setSnapshot] = useState<AdminOperationsSnapshot | null>(null);
  const [providers, setProviders] = useState<AdminProviderApproval[]>([]);
  const [captains, setCaptains] = useState<CaptainApproval[]>([]);
  const [serviceAreas, setServiceAreas] = useState<AdminServiceArea[]>([]);
  const [disputes, setDisputes] = useState<AdminDispute[]>([]);
  const [supportCases, setSupportCases] = useState<AdminSupportCase[]>([]);
  const [auditLogs, setAuditLogs] = useState<AdminAuditLog[]>([]);
  const [banners, setBanners] = useState<ContentBanner[]>([]);
  const [writers, setWriters] = useState<GuideWriter[]>([]);
  const [promotions, setPromotions] = useState<Promotion[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [disputeNotes, setDisputeNotes] = useState<Record<string, string>>({});
  const [areaForm, setAreaForm] = useState({
    pincode: '',
    city: 'Tirupati',
    radius: '8',
    emergencyMessage: '',
    reason: 'Controlled Tirupati pilot configuration',
    enabled: true,
    deliveryEnabled: true,
  });
  const [areaErrors, setAreaErrors] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    if (!token || role !== 'ADMIN') {
      setLoading(false);
      return;
    }
    setError(null);
    try {
      const [
        nextSnapshot,
        nextProviders,
        nextCaptains,
        nextAreas,
        nextDisputes,
        nextSupportCases,
        nextAudit,
        nextBanners,
        nextWriters,
        nextPromotions,
      ] = await Promise.all([
        fetchAdminOperationsSnapshot(token),
        fetchPendingProviderApprovals(token),
        fetchPendingCaptains(token) as Promise<CaptainApproval[]>,
        fetchAdminServiceAreas(token),
        fetchAdminDisputes(token),
        fetchAdminSupportCases(token),
        fetchAdminAuditLogs(token),
        fetchBanners(token) as Promise<ContentBanner[]>,
        fetchGuideWriters(token) as Promise<GuideWriter[]>,
        fetchPromotions(null, token) as Promise<Promotion[]>,
      ]);
      setSnapshot(nextSnapshot);
      setProviders(nextProviders);
      setCaptains(nextCaptains);
      setServiceAreas(nextAreas);
      setDisputes(nextDisputes);
      setSupportCases(nextSupportCases);
      setAuditLogs(nextAudit);
      setBanners(nextBanners);
      setWriters(nextWriters);
      setPromotions(nextPromotions);
    } catch (nextError) {
      setError(nextError);
    } finally {
      setLoading(false);
    }
  }, [role, token]);

  useEffect(() => {
    void load();
  }, [load]);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  }, [load]);

  const approveProvider = useCallback(async (providerId: string) => {
    if (!token) return;
    setBusyId(providerId);
    setNotice(null);
    try {
      await approveProviderFromAdmin(providerId, token);
      setProviders((current) => current.filter((provider) => provider.providerId !== providerId));
      setNotice('Merchant approved. The server activated provider permissions.');
    } catch (nextError) {
      setError(nextError);
    } finally {
      setBusyId(null);
    }
  }, [token]);

  const approveCaptainProfile = useCallback(async (captainId: string) => {
    if (!token) return;
    setBusyId(captainId);
    setNotice(null);
    try {
      await approveCaptain(captainId, token);
      setCaptains((current) => current.filter((captain) => captain.captainId !== captainId));
      setNotice('Captain approved. Availability remains blocked until valid location permission is granted.');
    } catch (nextError) {
      setError(nextError);
    } finally {
      setBusyId(null);
    }
  }, [token]);

  const saveArea = useCallback(async () => {
    if (!token) return;
    const radius = Number(areaForm.radius);
    const draft = {
      pincode: areaForm.pincode,
      city: areaForm.city,
      enabled: areaForm.enabled,
      deliveryEnabled: areaForm.deliveryEnabled,
      serviceRadiusKm: radius,
      emergencyMessage: areaForm.emergencyMessage,
      reason: areaForm.reason,
    };
    const validation = validateServiceAreaDraft(draft);
    setAreaErrors(validation);
    if (Object.keys(validation).length > 0) return;

    setBusyId('service-area');
    setNotice(null);
    try {
      const saved = await saveAdminServiceArea(draft, token);
      setServiceAreas((current) => [saved, ...current.filter((item) => item.pincode !== saved.pincode)]);
      setAuditLogs(await fetchAdminAuditLogs(token));
      setAreaForm((current) => ({ ...current, pincode: '', emergencyMessage: '' }));
      setNotice(`Service area ${saved.pincode} saved and recorded in the immutable audit log.`);
    } catch (nextError) {
      setError(nextError);
    } finally {
      setBusyId(null);
    }
  }, [areaForm, token]);

  const decideDispute = useCallback(async (disputeId: string, decision: 'RESOLVED' | 'REJECTED') => {
    if (!token) return;
    const notes = disputeNotes[disputeId]?.trim();
    if (!notes) {
      setNotice('Enter resolution notes before changing a dispute.');
      return;
    }
    setBusyId(disputeId);
    setNotice(null);
    try {
      const updated = await resolveAdminDispute(disputeId, decision, notes, token);
      setDisputes((current) => current.map((item) => (item.disputeId === disputeId ? updated : item)));
      setNotice(`Dispute ${shortId(disputeId)} updated to ${decision}.`);
    } catch (nextError) {
      setError(nextError);
    } finally {
      setBusyId(null);
    }
  }, [disputeNotes, token]);

  const revokeWriter = useCallback(async (writerId: string) => {
    if (!token) return;
    setBusyId(writerId);
    try {
      await revokeGuideWriter(writerId, token);
      setWriters((current) => current.filter((writer) => writer.writerId !== writerId));
      setNotice('Guide-writer access revoked.');
    } catch (nextError) {
      setError(nextError);
    } finally {
      setBusyId(null);
    }
  }, [token]);

  if (role !== 'ADMIN') {
    return (
      <ScreenShell
        header={<AppBar eyebrow="MY PET OPERATIONS" title="Admin portal" action={<RoleBadge role="admin" />} />}
      >
        <StateView
          kind="unauthorized"
          title="Administrator access required"
          message="This route is protected in the client and must also be authorized by the API gateway and backend."
        />
      </ScreenShell>
    );
  }

  if (loading) {
    return (
      <ScreenShell header={<AppBar eyebrow="MY PET OPERATIONS" title="Admin portal" action={<RoleBadge role="admin" />} />}>
        <StateView kind="loading" title="Loading live operations" message="Reading server-authoritative queues and controls." />
      </ScreenShell>
    );
  }

  if (error && !snapshot) {
    return (
      <ScreenShell header={<AppBar eyebrow="MY PET OPERATIONS" title="Admin portal" action={<RoleBadge role="admin" />} />}>
        <StateView kind="error" title="Admin data unavailable" message={apiErrorMessage(error)} actionLabel="Retry" onAction={() => void load()} />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell
      testID="admin-operations-portal"
      header={
        <AppBar
          eyebrow="MY PET OPERATIONS"
          title="Admin portal"
          subtitle="Approvals, live operations, disputes, city controls and immutable audit history"
          action={<RoleBadge role="admin" />}
        />
      }
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
    >
      {notice ? <FeedbackBanner title="Admin action" message={notice} tone="success" /> : null}
      {error ? <FeedbackBanner title="Latest refresh failed" message={apiErrorMessage(error)} tone="danger" /> : null}

      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filters}>
        {SECTIONS.map((item) => (
          <FilterChip key={item.id} label={item.label} selected={section === item.id} onPress={() => setSection(item.id)} />
        ))}
      </ScrollView>

      {section === 'overview' && snapshot ? (
        <View style={styles.stack}>
          <SectionHeader title="Operations pulse" subtitle={`Generated ${formatDateTime(snapshot.generatedAt)}`} />
          <View style={styles.metricGrid}>
            <MetricCard label="Active orders" value={String(snapshot.activeOrders)} icon="cart" />
            <MetricCard label="Delayed orders" value={String(snapshot.delayedOrders)} icon="truck" tone="warning" />
            <MetricCard label="Failed payments" value={String(snapshot.failedPayments)} icon="dispute" tone="danger" />
            <MetricCard label="Open disputes" value={String(snapshot.openDisputes)} icon="dispute" tone="danger" />
            <MetricCard label="Open support" value={String(snapshot.openSupportCases)} icon="support" tone="warning" />
            <MetricCard label="Service areas" value={String(serviceAreas.filter((item) => item.enabled).length)} icon="store" tone="success" />
          </View>
          <AppCard style={styles.cardStack}>
            <ThemedText style={styles.cardTitle}>Release controls</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              Production data is never replaced by local demo queues. Every service-area mutation requires a reason and emits an audit record with the request trace ID.
            </ThemedText>
          </AppCard>
        </View>
      ) : null}

      {section === 'approvals' ? (
        <View style={styles.stack}>
          <SectionHeader title="Merchant approvals" subtitle={`${providers.length} waiting`} />
          {providers.length === 0 ? <StateView kind="empty" title="No merchant approvals" message="Submitted providers will appear here." /> : providers.map((provider) => (
            <AppCard key={provider.providerId} style={styles.cardStack}>
              <View style={styles.rowBetween}>
                <View style={styles.flex}>
                  <ThemedText style={styles.cardTitle}>{provider.name}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {formatStatusLabel(provider.providerType)} · {provider.city} {provider.pincode}
                  </ThemedText>
                </View>
                <StatusBadge label={formatStatusLabel(provider.status)} tone={tone(provider.status)} />
              </View>
              <ThemedText type="small" themeColor="textSecondary">
                License {provider.licenseNumber ?? 'not supplied'} · Commission {provider.commissionPct}% · Owner {shortId(provider.ownerUserId)}
              </ThemedText>
              <ActionButton label="Approve merchant" icon="check" loading={busyId === provider.providerId} onPress={() => void approveProvider(provider.providerId)} />
            </AppCard>
          ))}

          <SectionHeader title="Captain approvals" subtitle={`${captains.length} waiting`} />
          {captains.length === 0 ? <StateView kind="empty" title="No captain approvals" message="Verified captain submissions will appear here." /> : captains.map((captain) => (
            <AppCard key={captain.captainId} style={styles.cardStack}>
              <View style={styles.rowBetween}>
                <View style={styles.flex}>
                  <ThemedText style={styles.cardTitle}>Captain {shortId(captain.captainId)}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">Vehicle {captain.vehicleNumber ?? 'not supplied'}</ThemedText>
                </View>
                <StatusBadge label={formatStatusLabel(captain.status)} tone={tone(captain.status)} />
              </View>
              <ActionButton label="Approve captain" icon="check" loading={busyId === captain.captainId} onPress={() => void approveCaptainProfile(captain.captainId)} />
            </AppCard>
          ))}
        </View>
      ) : null}

      {section === 'service-areas' ? (
        <View style={styles.stack}>
          <SectionHeader title="City and service-area controls" subtitle="Pincode-level launch and delivery policy" />
          <AppCard style={styles.cardStack}>
            <TextField label="Pincode" keyboardType="number-pad" maxLength={6} value={areaForm.pincode} onChangeText={(pincode) => setAreaForm((current) => ({ ...current, pincode }))} error={areaErrors.pincode} />
            <TextField label="City" value={areaForm.city} onChangeText={(city) => setAreaForm((current) => ({ ...current, city }))} error={areaErrors.city} />
            <TextField label="Service radius (km)" keyboardType="decimal-pad" value={areaForm.radius} onChangeText={(radius) => setAreaForm((current) => ({ ...current, radius }))} error={areaErrors.serviceRadiusKm} />
            <TextField label="Emergency message" value={areaForm.emergencyMessage} onChangeText={(emergencyMessage) => setAreaForm((current) => ({ ...current, emergencyMessage }))} multiline />
            <TextField label="Reason for change" value={areaForm.reason} onChangeText={(reason) => setAreaForm((current) => ({ ...current, reason }))} error={areaErrors.reason} multiline />
            <View style={styles.wrapRow}>
              <FilterChip label="Area enabled" selected={areaForm.enabled} onPress={() => setAreaForm((current) => ({ ...current, enabled: !current.enabled }))} />
              <FilterChip label="Delivery enabled" selected={areaForm.deliveryEnabled} onPress={() => setAreaForm((current) => ({ ...current, deliveryEnabled: !current.deliveryEnabled }))} />
            </View>
            <ActionButton label="Save and audit" icon="shield" loading={busyId === 'service-area'} onPress={() => void saveArea()} />
          </AppCard>

          {serviceAreas.length === 0 ? <StateView kind="empty" title="No configured service areas" message="Add the Tirupati pilot pincodes before beta." /> : serviceAreas.map((area) => (
            <AppCard key={area.pincode} style={styles.cardStack}>
              <View style={styles.rowBetween}>
                <View style={styles.flex}>
                  <ThemedText style={styles.cardTitle}>{area.city} · {area.pincode}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    Radius {formatDistance(Number(area.serviceRadiusKm) * 1000)} · Updated {formatDateTime(area.updatedAt)}
                  </ThemedText>
                </View>
                <StatusBadge label={area.enabled ? 'Enabled' : 'Disabled'} tone={area.enabled ? 'success' : 'danger'} />
              </View>
              <ThemedText type="small" themeColor="textSecondary">Delivery {area.deliveryEnabled ? 'available' : 'paused'}{area.emergencyMessage ? ` · ${area.emergencyMessage}` : ''}</ThemedText>
            </AppCard>
          ))}
        </View>
      ) : null}

      {section === 'disputes' ? (
        <View style={styles.stack}>
          <SectionHeader title="Disputes and support" subtitle={`${disputes.filter((item) => item.status === 'OPEN').length} open disputes · ${supportCases.filter((item) => item.status === 'OPEN').length} open support cases`} />
          {disputes.length === 0 ? <StateView kind="empty" title="No disputes" message="Customer dispute submissions will appear here." /> : disputes.map((dispute) => (
            <AppCard key={dispute.disputeId} style={styles.cardStack}>
              <View style={styles.rowBetween}>
                <View style={styles.flex}>
                  <ThemedText style={styles.cardTitle}>Order {shortId(dispute.orderId)}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">Opened {formatDateTime(dispute.createdAt ?? undefined)}</ThemedText>
                </View>
                <StatusBadge label={formatStatusLabel(dispute.status)} tone={tone(dispute.status)} />
              </View>
              <ThemedText>{dispute.reason}</ThemedText>
              {dispute.status === 'OPEN' ? (
                <>
                  <TextField label="Resolution notes" value={disputeNotes[dispute.disputeId] ?? ''} onChangeText={(value) => setDisputeNotes((current) => ({ ...current, [dispute.disputeId]: value }))} multiline />
                  <View style={styles.actionRow}>
                    <ActionButton label="Resolve" icon="check" loading={busyId === dispute.disputeId} onPress={() => void decideDispute(dispute.disputeId, 'RESOLVED')} style={styles.flex} />
                    <ActionButton label="Reject" icon="xmark" variant="destructive" disabled={busyId === dispute.disputeId} onPress={() => void decideDispute(dispute.disputeId, 'REJECTED')} style={styles.flex} />
                  </View>
                </>
              ) : dispute.resolutionNotes ? <ThemedText type="small" themeColor="textSecondary">Resolution: {dispute.resolutionNotes}</ThemedText> : null}
            </AppCard>
          ))}

          <SectionHeader title="Support queue" />
          {supportCases.slice(0, 10).map((item) => (
            <AppCard key={item.supportCaseId} style={styles.cardStack}>
              <View style={styles.rowBetween}>
                <ThemedText style={[styles.cardTitle, styles.flex]}>{item.title}</ThemedText>
                <StatusBadge label={formatStatusLabel(item.status)} tone={tone(item.status)} />
              </View>
              <ThemedText type="small" themeColor="textSecondary">{item.detail}</ThemedText>
            </AppCard>
          ))}
        </View>
      ) : null}

      {section === 'audit' ? (
        <View style={styles.stack}>
          <SectionHeader title="Administrative audit log" subtitle="Newest 50 immutable records" />
          {auditLogs.length === 0 ? <StateView kind="empty" title="No administrative writes recorded" message="Audited service-area changes will appear here." /> : auditLogs.map((audit) => (
            <AppCard key={audit.auditId} style={styles.cardStack}>
              <View style={styles.rowBetween}>
                <View style={styles.flex}>
                  <ThemedText style={styles.cardTitle}>{formatStatusLabel(audit.action)}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">{audit.entityType} {audit.entityId ?? ''} · {formatDateTime(audit.createdAt)}</ThemedText>
                </View>
                <StatusBadge label="Recorded" tone="info" />
              </View>
              <ThemedText type="small">Reason: {audit.reason}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">Admin {shortId(audit.adminUserId)} · Trace {shortId(audit.traceId)}</ThemedText>
            </AppCard>
          ))}
        </View>
      ) : null}

      {section === 'content' ? (
        <View style={styles.stack}>
          <SectionHeader title="Content and promotions" subtitle="Customer-home publishing controls" />
          <SectionHeader title="Banners" subtitle={`${banners.length} configured`} />
          {banners.map((banner) => (
            <AppCard key={banner.id} style={styles.rowBetween}>
              <View style={styles.flex}>
                <ThemedText style={styles.cardTitle}>{banner.title}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">{banner.durationSec}s rotation slot</ThemedText>
              </View>
              <StatusBadge label={banner.active ? 'Live' : 'Paused'} tone={banner.active ? 'success' : 'neutral'} />
            </AppCard>
          ))}
          <SectionHeader title="Promotions" subtitle={`${promotions.length} configured`} />
          {promotions.map((promotion) => (
            <AppCard key={promotion.promotionId ?? promotion.code} style={styles.rowBetween}>
              <View style={styles.flex}>
                <ThemedText style={styles.cardTitle}>{promotion.code}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">{formatPromotionLabel(promotion)} · {formatPromotionScope(promotion)}</ThemedText>
              </View>
              <StatusBadge label={promotion.isActive ? 'Active' : 'Paused'} tone={promotion.isActive ? 'success' : 'neutral'} />
            </AppCard>
          ))}
          <SectionHeader title="Guide writers" subtitle={`${writers.length} active`} />
          {writers.map((writer) => (
            <AppCard key={writer.writerId} style={styles.rowBetween}>
              <View style={styles.flex}>
                <ThemedText style={styles.cardTitle}>{writer.email}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">{formatStatusLabel(writer.accessStatus)}</ThemedText>
              </View>
              <ActionButton label="Revoke" variant="destructive" loading={busyId === writer.writerId} onPress={() => void revokeWriter(writer.writerId)} />
            </AppCard>
          ))}
        </View>
      ) : null}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  stack: { gap: spacing.x4 },
  filters: { gap: spacing.x2, paddingRight: spacing.x4 },
  metricGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x3 },
  cardStack: { gap: spacing.x3 },
  cardTitle: { ...typography.title },
  rowBetween: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.x3 },
  wrapRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  actionRow: { flexDirection: 'row', gap: spacing.x3 },
  flex: { flex: 1 },
});
