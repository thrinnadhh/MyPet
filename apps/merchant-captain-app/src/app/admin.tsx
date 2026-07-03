import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  RefreshControl,
  ScrollView,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  useColorScheme,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AppIcon } from '@/components/app-icon';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Colors, Radius, Shadows, Spacing } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';
import { appConfig } from '@/utils/app-config';
import { approveCaptain, fetchPendingCaptains } from '@/services/captain-onboarding';
import { fetchBanners, fetchGuideWriters, revokeGuideWriter } from '@/services/content-admin';
import { fetchPromotions, formatPromotionLabel, formatPromotionScope } from '@/services/promotions';

type Section = 'approvals' | 'captains' | 'disputes' | 'commission' | 'banners' | 'guides' | 'promocodes' | 'support';

type ProviderStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'ACTIVE' | 'INFO_REQUESTED';

type ProviderApproval = {
  providerId: string;
  ownerUserId: string;
  providerType: string;
  fulfillmentType: string;
  name: string;
  description?: string | null;
  licenseNumber?: string | null;
  licenseDocUrl?: string | null;
  addressLine: string;
  city: string;
  pincode: string;
  status: ProviderStatus;
  ratingAvg?: number | string;
  ratingCount?: number;
  commissionPct: number | string;
};

type Dispute = {
  disputeId: string;
  orderId: string;
  status: 'OPEN' | 'RESOLVED' | 'REJECTED' | string;
  reason: string;
  resolutionNotes?: string | null;
  createdAt?: string;
  resolvedAt?: string | null;
};

type CommissionAudit = {
  id: string;
  providerId: string;
  commissionPct: string;
  reason: string;
  createdAt: string;
};

type SupportCase = {
  supportCaseId: string;
  title: string;
  detail: string;
  actionType: string;
  entityType?: string | null;
  entityId?: string | null;
  status: 'OPEN' | 'RESOLVED' | string;
  createdByUserId?: string | null;
  createdAt: string;
  resolvedAt?: string | null;
  resolutionNotes?: string | null;
};

const SECTIONS: { id: Section; label: string; icon: React.ComponentProps<typeof AppIcon>['name'] }[] = [
  { id: 'approvals', label: 'Merchants', icon: 'shield' },
  { id: 'captains', label: 'Captains', icon: 'truck' },
  { id: 'banners', label: 'Banners', icon: 'sparkle' },
  { id: 'guides', label: 'Guides', icon: 'medical' },
  { id: 'promocodes', label: 'Promos', icon: 'percent' },
  { id: 'disputes', label: 'Disputes', icon: 'dispute' },
  { id: 'commission', label: 'Rates', icon: 'percent' },
  { id: 'support', label: 'Support', icon: 'support' },
];

const DEMO_CAPTAINS = [
  { id: 'cap-001', name: 'Ravi Kumar', vehicle: 'KA 01 AB 1234', status: 'PENDING_APPROVAL' },
  { id: 'cap-002', name: 'Suresh Naidu', vehicle: 'TS 09 CD 5678', status: 'PENDING_APPROVAL' },
];

const DEMO_BANNERS = [
  { id: 'b1', title: 'Free delivery today', durationSec: 5, active: true },
  { id: 'b2', title: 'Grooming week', durationSec: 4, active: true },
  { id: 'b3', title: 'Vet checkup drive', durationSec: 3, active: false },
];

const DEMO_GUIDE_WRITERS = [
  { id: 'gw-1', email: 'dr.anita@carevet.com', access: 'ACTIVE' },
  { id: 'gw-2', email: 'groom.lead@petspa.com', access: 'ACTIVE' },
];

const DEMO_PROMOCODES = [
  { id: 'pc-1', code: 'PAWS10', discount: '10% off', scope: 'Platform' },
  { id: 'pc-2', code: 'FIRST50', discount: '₹50 off', scope: 'New users' },
];

const DEMO_PROVIDERS: ProviderApproval[] = [
  {
    providerId: '11111111-1111-4111-8111-111111111201',
    ownerUserId: '11111111-1111-4111-8111-111111111101',
    providerType: 'PET_STORE',
    fulfillmentType: 'DELIVERY',
    name: 'Happy Tails Koramangala',
    description: 'Pet food, toys, and delivery-ready store.',
    licenseNumber: 'BLR-PET-2049',
    licenseDocUrl: 'https://storage.example.com/license.pdf',
    addressLine: '12 5th Block',
    city: 'Bengaluru',
    pincode: '560095',
    status: 'PENDING_APPROVAL',
    ratingAvg: '4.70',
    ratingCount: 31,
    commissionPct: '15.00',
  },
  {
    providerId: '11111111-1111-4111-8111-111111111202',
    ownerUserId: '11111111-1111-4111-8111-111111111102',
    providerType: 'VET_HOSPITAL',
    fulfillmentType: 'APPOINTMENT',
    name: 'CareVet Whitefield',
    description: 'Clinic onboarding with registration proof attached.',
    licenseNumber: 'KVC-88912',
    licenseDocUrl: 'https://storage.example.com/vet-license.pdf',
    addressLine: '24 ITPL Main Road',
    city: 'Bengaluru',
    pincode: '560066',
    status: 'PENDING_APPROVAL',
    ratingAvg: '0.00',
    ratingCount: 0,
    commissionPct: '18.00',
  },
];

const DEMO_DISPUTES: Dispute[] = [
  {
    disputeId: '22222222-2222-4222-8222-222222222201',
    orderId: '33333333-3333-4333-8333-333333333301',
    status: 'OPEN',
    reason: 'Customer reports damaged food pack.',
    createdAt: new Date().toISOString(),
  },
  {
    disputeId: '22222222-2222-4222-8222-222222222202',
    orderId: '33333333-3333-4333-8333-333333333302',
    status: 'OPEN',
    reason: 'Late delivery refund review requested.',
    createdAt: new Date().toISOString(),
  },
];

const DEMO_SUPPORT_CASES: SupportCase[] = [
  {
    supportCaseId: '44444444-4444-4444-8444-444444444401',
    title: 'Request missing provider document',
    detail: 'Vet hospital must upload updated license proof before approval.',
    actionType: 'INFO_REQUEST',
    entityType: 'PROVIDER',
    entityId: '11111111-1111-4111-8111-111111111202',
    status: 'OPEN',
    createdAt: new Date().toISOString(),
  },
];

type SupportPreset = {
  title: string;
  actionType: string;
  entityType: string;
  detail?: string;
  entityId?: string;
};

const SUPPORT_PRESETS: SupportPreset[] = [
  { title: 'Request missing provider document', actionType: 'INFO_REQUEST', entityType: 'PROVIDER' },
  { title: 'Escalate delayed refund', actionType: 'REFUND_ESCALATION', entityType: 'ORDER' },
  { title: 'Review captain payout claim', actionType: 'PAYOUT_CLAIM_REVIEW', entityType: 'CAPTAIN' },
  { title: 'Create customer callback task', actionType: 'CUSTOMER_CALLBACK', entityType: 'CUSTOMER' },
];

async function parseJson<T>(response: Response): Promise<T> {
  const body = await response.text();
  if (!body) return {} as T;
  return JSON.parse(body) as T;
}

function formatDate(value?: string | null) {
  if (!value) return 'Not recorded';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function compactId(value: string) {
  if (!value) return 'unknown';
  return value.length > 12 ? `${value.slice(0, 8)}...${value.slice(-4)}` : value;
}

export default function SuperAdminScreen() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];
  const { session, role } = useAuth();

  const [section, setSection] = useState<Section>('approvals');
  const [providers, setProviders] = useState<ProviderApproval[]>([]);
  const [disputes, setDisputes] = useState<Dispute[]>([]);
  const [refundMode, setRefundMode] = useState('MANUAL');
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [disputeNotes, setDisputeNotes] = useState<Record<string, string>>({});
  const [supportTitle, setSupportTitle] = useState('');
  const [supportDetail, setSupportDetail] = useState('');
  const [supportActionType, setSupportActionType] = useState('GENERAL');
  const [supportEntityType, setSupportEntityType] = useState('');
  const [supportEntityId, setSupportEntityId] = useState('');
  const [supportResolutionNotes, setSupportResolutionNotes] = useState<Record<string, string>>({});
  const [commissionProviderId, setCommissionProviderId] = useState('');
  const [commissionPct, setCommissionPct] = useState('15.00');
  const [commissionReason, setCommissionReason] = useState('');
  const [commissionAudits, setCommissionAudits] = useState<CommissionAudit[]>([]);
  const [supportCases, setSupportCases] = useState<SupportCase[]>([]);
  const [pendingCaptains, setPendingCaptains] = useState(DEMO_CAPTAINS);
  const [guideWriters, setGuideWriters] = useState(DEMO_GUIDE_WRITERS);
  const [liveBanners, setLiveBanners] = useState(DEMO_BANNERS);
  const [platformPromos, setPlatformPromos] = useState(DEMO_PROMOCODES);

  const isAdmin = role === 'ADMIN';
  const canUseDemo = appConfig.allowDemoMode;

  const request = useCallback(
    async <T,>(path: string, init?: RequestInit) => {
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (session?.access_token) {
        headers.Authorization = `Bearer ${session.access_token}`;
      }

      const response = await fetch(`${appConfig.apiBaseUrl}${path}`, {
        ...init,
        headers: {
          ...headers,
          ...(init?.headers as Record<string, string> | undefined),
        },
      });

      const data = await parseJson<T | { error?: string }>(response);
      if (!response.ok) {
        const errorPayload = data as { error?: string };
        const errorMessage = errorPayload.error ?? `Request failed with ${response.status}`;
        throw new Error(errorMessage);
      }
      return data as T;
    },
    [session],
  );

  const loadAdminData = useCallback(async () => {
    setMessage(null);
    try {
      const [pendingProviders, disputeRows, config, supportRows] = await Promise.all([
        request<ProviderApproval[]>('/api/v1/providers/pending'),
        request<Dispute[]>('/api/v1/orders/disputes'),
        request<{ dispute_refund_mode: string }>('/api/v1/orders/admin/config'),
        request<SupportCase[]>('/api/v1/orders/admin/support-cases'),
      ]);
      setProviders(pendingProviders);
      setDisputes(disputeRows);
      setRefundMode(config.dispute_refund_mode ?? 'MANUAL');
      setSupportCases(supportRows);
    } catch (error) {
      if (canUseDemo) {
        setProviders(DEMO_PROVIDERS);
        setDisputes(DEMO_DISPUTES);
        setSupportCases(DEMO_SUPPORT_CASES);
        setRefundMode('MANUAL');
        setMessage('Showing explicit demo admin data because the local admin APIs are unavailable.');
        return;
      }
      setMessage(error instanceof Error ? error.message : 'Unable to load admin console data.');
    }
  }, [canUseDemo, request]);

  const loadContentSections = useCallback(async () => {
    try {
      const token = session?.access_token;
      const [captains, banners, writers, promos] = await Promise.all([
        fetchPendingCaptains(token),
        fetchBanners(token),
        fetchGuideWriters(token),
        fetchPromotions(null, token),
      ]);
      setPendingCaptains(
        (captains as Array<{ captainId: string; vehicleNumber?: string; status: string }>).map((c) => ({
          id: c.captainId,
          name: `Captain ${c.captainId.slice(0, 8)}`,
          vehicle: c.vehicleNumber ?? '—',
          status: c.status,
        })),
      );
      setLiveBanners(
        (banners as Array<{ id: string; title: string; durationSec: number; active: boolean }>).map((b) => ({
          id: b.id,
          title: b.title,
          durationSec: b.durationSec,
          active: b.active,
        })),
      );
      setGuideWriters(
        (writers as Array<{ writerId: string; email: string; accessStatus: string }>).map((w) => ({
          id: w.writerId,
          email: w.email,
          access: w.accessStatus,
        })),
      );
      setPlatformPromos(
        (promos as Array<{ promotionId?: string; code: string; discountType: string; discountValue: number; providerId: string | null; applicableCategory: string | null; isActive: boolean }>).map((promo) => ({
          id: promo.promotionId ?? promo.code,
          code: promo.code,
          discount: formatPromotionLabel(promo as Parameters<typeof formatPromotionLabel>[0]),
          scope: formatPromotionScope(promo as Parameters<typeof formatPromotionScope>[0]),
          active: promo.isActive,
        })),
      );
    } catch {
      if (canUseDemo) {
        setPendingCaptains(DEMO_CAPTAINS);
        setLiveBanners(DEMO_BANNERS);
        setGuideWriters(DEMO_GUIDE_WRITERS);
        setPlatformPromos(DEMO_PROMOCODES);
      }
    }
  }, [canUseDemo, session?.access_token]);

  useEffect(() => {
    loadAdminData().finally(() => setLoading(false));
  }, [loadAdminData]);

  useEffect(() => {
    if (section === 'captains' || section === 'banners' || section === 'guides' || section === 'promocodes') {
      loadContentSections();
    }
  }, [section, loadContentSections]);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    await Promise.all([loadAdminData(), loadContentSections()]);
    setRefreshing(false);
  }, [loadAdminData, loadContentSections]);

  const approveCaptainProfile = useCallback(
    async (captainId: string) => {
      setBusyId(captainId);
      try {
        await approveCaptain(captainId, session?.access_token);
        setPendingCaptains((current) => current.filter((captain) => captain.id !== captainId));
        Alert.alert('Approved', 'Captain is now active.');
      } catch (error) {
        if (canUseDemo) {
          setPendingCaptains((current) => current.filter((captain) => captain.id !== captainId));
          Alert.alert('Demo approval', 'Captain removed from the local demo queue.');
          return;
        }
        Alert.alert('Approval failed', error instanceof Error ? error.message : 'Unable to approve captain.');
      } finally {
        setBusyId(null);
      }
    },
    [canUseDemo, session?.access_token],
  );

  const revokeWriter = useCallback(
    async (writerId: string) => {
      setBusyId(writerId);
      try {
        await revokeGuideWriter(writerId, session?.access_token);
        setGuideWriters((current) => current.filter((writer) => writer.id !== writerId));
      } catch (error) {
        if (canUseDemo) {
          setGuideWriters((current) => current.filter((writer) => writer.id !== writerId));
          return;
        }
        Alert.alert('Revoke failed', error instanceof Error ? error.message : 'Unable to revoke writer.');
      } finally {
        setBusyId(null);
      }
    },
    [canUseDemo, session?.access_token],
  );

  const approveProvider = useCallback(
    async (providerId: string) => {
      setBusyId(providerId);
      try {
        await request<ProviderApproval>(`/api/v1/providers/${providerId}/approve`, { method: 'POST' });
        setProviders((current) => current.filter((provider) => provider.providerId !== providerId));
        Alert.alert('Approved', 'Provider is now active.');
      } catch (error) {
        if (canUseDemo) {
          setProviders((current) => current.filter((provider) => provider.providerId !== providerId));
          Alert.alert('Demo approval', 'Provider removed from the local demo approval queue.');
          return;
        }
        Alert.alert('Approval failed', error instanceof Error ? error.message : 'Unable to approve provider.');
      } finally {
        setBusyId(null);
      }
    },
    [canUseDemo, request],
  );

  const resolveDispute = useCallback(
    async (disputeId: string, decision: 'RESOLVED' | 'REJECTED') => {
      setBusyId(disputeId);
      const notes = disputeNotes[disputeId] || (decision === 'RESOLVED' ? 'Resolved by Super Admin.' : 'Rejected by Super Admin.');
      try {
        const updated = await request<Dispute>(`/api/v1/orders/disputes/${disputeId}/resolve`, {
          method: 'POST',
          body: JSON.stringify({ decision, resolutionNotes: notes }),
        });
        setDisputes((current) => current.map((dispute) => (dispute.disputeId === disputeId ? updated : dispute)));
      } catch (error) {
        if (canUseDemo) {
          setDisputes((current) =>
            current.map((dispute) =>
              dispute.disputeId === disputeId
                ? { ...dispute, status: decision, resolutionNotes: notes, resolvedAt: new Date().toISOString() }
                : dispute,
            ),
          );
          return;
        }
        Alert.alert('Dispute update failed', error instanceof Error ? error.message : 'Unable to update dispute.');
      } finally {
        setBusyId(null);
      }
    },
    [canUseDemo, disputeNotes, request],
  );

  const saveRefundMode = useCallback(async () => {
    setBusyId('refund-mode');
    try {
      const updated = await request<{ dispute_refund_mode: string }>('/api/v1/orders/admin/config', {
        method: 'POST',
        body: JSON.stringify({ dispute_refund_mode: refundMode }),
      });
      setRefundMode(updated.dispute_refund_mode);
      Alert.alert('Saved', `Dispute refund mode is ${updated.dispute_refund_mode}.`);
    } catch (error) {
      if (canUseDemo) {
        Alert.alert('Demo saved', `Dispute refund mode staged as ${refundMode}.`);
        return;
      }
      Alert.alert('Save failed', error instanceof Error ? error.message : 'Unable to save refund mode.');
    } finally {
      setBusyId(null);
    }
  }, [canUseDemo, refundMode, request]);

  const createSupportCase = useCallback(async (preset?: SupportPreset) => {
    const title = preset?.title ?? supportTitle.trim();
    const actionType = preset?.actionType ?? supportActionType;
    const entityType = preset?.entityType ?? supportEntityType.trim();
    const detail = preset
      ? preset.detail ?? `${preset.title} queued from Super Admin quick action.`
      : supportDetail.trim();
    const entityId = (preset?.entityId ?? supportEntityId.trim()) || null;

    if (!title || !detail) {
      Alert.alert('Missing details', 'Enter a support title and detail before opening a case.');
      return;
    }

    setBusyId(`support-${title}`);
    try {
      const created = await request<SupportCase>('/api/v1/orders/admin/support-cases', {
        method: 'POST',
        body: JSON.stringify({
          title,
          detail,
          actionType,
          entityType: entityType || null,
          entityId,
        }),
      });
      setSupportCases((current) => [created, ...current]);
      setSupportTitle('');
      setSupportDetail('');
      setSupportEntityType('');
      setSupportEntityId('');
      setSupportActionType('GENERAL');
    } catch (error) {
      if (canUseDemo) {
        const created: SupportCase = {
          supportCaseId: `${Date.now()}`,
          title,
          detail,
          actionType,
          entityType: entityType || null,
          entityId,
          status: 'OPEN',
          createdAt: new Date().toISOString(),
        };
        setSupportCases((current) => [created, ...current]);
        setSupportTitle('');
        setSupportDetail('');
        setSupportEntityType('');
        setSupportEntityId('');
        setSupportActionType('GENERAL');
        return;
      }
      Alert.alert('Support action failed', error instanceof Error ? error.message : 'Unable to create support case.');
    } finally {
      setBusyId(null);
    }
  }, [canUseDemo, request, supportActionType, supportDetail, supportEntityId, supportEntityType, supportTitle]);

  const saveCommissionChange = useCallback(async () => {
    const parsed = Number(commissionPct);
    if (!commissionProviderId.trim() || Number.isNaN(parsed) || parsed < 0 || parsed > 50) {
      Alert.alert('Check commission', 'Enter a provider ID and a commission between 0 and 50.');
      return;
    }

    const providerId = commissionProviderId.trim();
    const reason = commissionReason.trim() || 'Admin rate review';
    setBusyId('commission');
    try {
      const updated = await request<ProviderApproval>(`/api/v1/providers/${providerId}/commission`, {
        method: 'PATCH',
        body: JSON.stringify({ commissionPct: parsed, reason }),
      });
      setProviders((current) =>
        current.map((provider) => (provider.providerId === providerId ? updated : provider)),
      );
      setCommissionAudits((current) => [
        {
          id: `${Date.now()}`,
          providerId,
          commissionPct: String(updated.commissionPct),
          reason,
          createdAt: new Date().toISOString(),
        },
        ...current,
      ]);
      setCommissionProviderId('');
      setCommissionPct('15.00');
      setCommissionReason('');
      Alert.alert('Saved', `Commission updated to ${updated.commissionPct}%.`);
    } catch (error) {
      if (canUseDemo) {
        setCommissionAudits((current) => [
          {
            id: `${Date.now()}`,
            providerId,
            commissionPct: parsed.toFixed(2),
            reason,
            createdAt: new Date().toISOString(),
          },
          ...current,
        ]);
        setCommissionProviderId('');
        setCommissionPct('15.00');
        setCommissionReason('');
        Alert.alert('Demo saved', `Commission staged as ${parsed.toFixed(2)}%.`);
        return;
      }
      Alert.alert('Commission update failed', error instanceof Error ? error.message : 'Unable to update commission.');
    } finally {
      setBusyId(null);
    }
  }, [canUseDemo, commissionPct, commissionProviderId, commissionReason, request]);

  const resolveSupportCase = useCallback(async (supportCaseId: string) => {
    const resolutionNotes = supportResolutionNotes[supportCaseId] || 'Completed by Super Admin.';
    setBusyId(supportCaseId);
    try {
      const updated = await request<SupportCase>(`/api/v1/orders/admin/support-cases/${supportCaseId}/resolve`, {
        method: 'POST',
        body: JSON.stringify({ resolutionNotes }),
      });
      setSupportCases((current) => current.map((supportCase) => (
        supportCase.supportCaseId === supportCaseId ? updated : supportCase
      )));
    } catch (error) {
      if (canUseDemo) {
        setSupportCases((current) => current.map((supportCase) => (
          supportCase.supportCaseId === supportCaseId
            ? { ...supportCase, status: 'RESOLVED', resolutionNotes, resolvedAt: new Date().toISOString() }
            : supportCase
        )));
        return;
      }
      Alert.alert('Support case update failed', error instanceof Error ? error.message : 'Unable to resolve support case.');
    } finally {
      setBusyId(null);
    }
  }, [canUseDemo, request, supportResolutionNotes]);

  const openDisputes = disputes.filter((dispute) => dispute.status === 'OPEN').length;
  const stats = [
    { label: 'Pending providers', value: providers.length.toString(), tone: colors.warning, icon: 'shield' as const },
    { label: 'Open disputes', value: openDisputes.toString(), tone: colors.danger, icon: 'dispute' as const },
    { label: 'Refund mode', value: refundMode, tone: colors.cta, icon: 'gear' as const },
    { label: 'Rate changes', value: commissionAudits.length.toString(), tone: colors.accent, icon: 'percent' as const },
    { label: 'Support cases', value: supportCases.filter((item) => item.status === 'OPEN').length.toString(), tone: colors.warning, icon: 'support' as const },
  ];

  return (
    <ThemedView style={[styles.container, { backgroundColor: colors.background }]}>
      <SafeAreaView style={styles.safeArea}>
        <ScrollView
          showsVerticalScrollIndicator={false}
          contentContainerStyle={styles.scrollContent}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}
        >
          <View style={[styles.hero, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
            <View style={styles.heroIcon}>
              <AppIcon name="shield" color={colors.primary} size={30} />
            </View>
            <View style={styles.heroCopy}>
              <ThemedText type="small" style={{ color: colors.textSecondary, fontWeight: '900' }}>
                SUPER ADMIN
              </ThemedText>
              <ThemedText style={[styles.heroTitle, { color: colors.text }]}>Operations Console</ThemedText>
              <ThemedText type="small" style={{ color: colors.textSecondary }}>
                Provider approvals, disputes, commission review, and support actions.
              </ThemedText>
            </View>
          </View>

          {!isAdmin ? (
            <View style={[styles.notice, { backgroundColor: colors.muted, borderColor: colors.border }]}>
              <AppIcon name="shield" color={colors.warning} size={20} />
              <ThemedText type="small" style={{ color: colors.text, flex: 1 }}>
                Viewing console UI as {role ?? 'operator'}. Live admin actions require an ADMIN Supabase role.
              </ThemedText>
            </View>
          ) : null}

          {message ? (
            <View style={[styles.notice, { backgroundColor: colors.muted, borderColor: colors.border }]}>
              <AppIcon name={canUseDemo ? 'sparkle' : 'dispute'} color={canUseDemo ? colors.cta : colors.danger} size={20} />
              <ThemedText type="small" style={{ color: colors.text, flex: 1 }}>{message}</ThemedText>
            </View>
          ) : null}

          <View style={styles.statsGrid}>
            {stats.map((item) => (
              <View key={item.label} style={[styles.statCard, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                <View style={[styles.statIcon, { backgroundColor: colors.muted }]}>
                  <AppIcon name={item.icon} color={item.tone} size={18} />
                </View>
                <ThemedText type="small" style={{ color: colors.textSecondary }}>{item.label}</ThemedText>
                <ThemedText style={[styles.statValue, { color: item.tone }]}>{item.value}</ThemedText>
              </View>
            ))}
          </View>

          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={[styles.segmented, { backgroundColor: colors.muted }]}>
            {SECTIONS.map((item) => {
              const selected = section === item.id;
              return (
                <TouchableOpacity
                  key={item.id}
                  activeOpacity={0.78}
                  onPress={() => setSection(item.id)}
                  style={[styles.segment, selected && { backgroundColor: colors.backgroundElement }]}
                  accessibilityRole="button"
                  accessibilityLabel={item.label}
                >
                  <AppIcon name={item.icon} color={selected ? colors.cta : colors.textSecondary} size={17} />
                  <ThemedText
                    type="small"
                    style={{ color: selected ? colors.text : colors.textSecondary, fontWeight: selected ? '900' : '700' }}
                  >
                    {item.label}
                  </ThemedText>
                </TouchableOpacity>
              );
            })}
          </ScrollView>

          {loading ? (
            <View style={[styles.loadingPanel, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
              <ActivityIndicator />
              <ThemedText type="small" style={{ color: colors.textSecondary }}>Loading admin queues</ThemedText>
            </View>
          ) : null}

          {!loading && section === 'approvals' ? (
            <View style={styles.sectionStack}>
              {providers.length === 0 ? (
                <EmptyState title="No providers waiting" body="New submitted merchants will appear here for review." />
              ) : (
                providers.map((provider) => (
                  <View key={provider.providerId} style={[styles.card, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                    <View style={styles.cardHeader}>
                      <View style={styles.flex}>
                        <ThemedText style={[styles.cardTitle, { color: colors.text }]}>{provider.name}</ThemedText>
                        <ThemedText type="small" style={{ color: colors.textSecondary }}>
                          {provider.providerType.replace('_', ' ')} | {provider.city} {provider.pincode}
                        </ThemedText>
                      </View>
                      <StatusPill label={provider.status} color={colors.warning} />
                    </View>
                    <ThemedText type="small" style={{ color: colors.textSecondary }}>
                      {provider.addressLine}
                    </ThemedText>
                    <View style={styles.metaGrid}>
                      <Meta label="License" value={provider.licenseNumber ?? 'Not provided'} />
                      <Meta label="Commission" value={`${provider.commissionPct}%`} />
                      <Meta label="Provider ID" value={compactId(provider.providerId)} />
                      <Meta label="Owner ID" value={compactId(provider.ownerUserId)} />
                    </View>
                    <View style={styles.actionRow}>
                      <TouchableOpacity
                        style={[styles.primaryButton, { backgroundColor: colors.success }]}
                        activeOpacity={0.78}
                        onPress={() => approveProvider(provider.providerId)}
                        disabled={busyId === provider.providerId}
                      >
                        <AppIcon name="check" color="#FFFFFF" size={18} />
                        <ThemedText type="small" style={styles.buttonText}>
                          {busyId === provider.providerId ? 'Approving' : 'Approve'}
                        </ThemedText>
                      </TouchableOpacity>
                      <TouchableOpacity
                        style={[styles.secondaryButton, { borderColor: colors.border }]}
                        activeOpacity={0.78}
                        onPress={() => createSupportCase({
                          title: `Request more information from ${provider.name}`,
                          actionType: 'INFO_REQUEST',
                          entityType: 'PROVIDER',
                          entityId: provider.providerId,
                          detail: `Provider approval requires more information for ${provider.name}.`,
                        })}
                      >
                        <AppIcon name="support" color={colors.cta} size={18} />
                        <ThemedText type="small" style={{ color: colors.text, fontWeight: '900' }}>Info request</ThemedText>
                      </TouchableOpacity>
                    </View>
                  </View>
                ))
              )}
            </View>
          ) : null}

          {!loading && section === 'captains' ? (
            <View style={styles.sectionStack}>
              {pendingCaptains.map((captain) => (
                <View key={captain.id} style={[styles.card, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                  <View style={styles.cardHeader}>
                    <View style={styles.flex}>
                      <ThemedText style={[styles.cardTitle, { color: colors.text }]}>{captain.name}</ThemedText>
                      <ThemedText type="small" style={{ color: colors.textSecondary }}>Vehicle {captain.vehicle}</ThemedText>
                    </View>
                    <StatusPill label={captain.status} color={colors.warning} />
                  </View>
                  <View style={styles.actionRow}>
                    <TouchableOpacity
                      style={[styles.primaryButton, { backgroundColor: colors.success }]}
                      activeOpacity={0.78}
                      disabled={busyId === captain.id}
                      onPress={() => approveCaptainProfile(captain.id)}
                    >
                      <AppIcon name="check" color="#FFFFFF" size={18} />
                      <ThemedText type="small" style={styles.buttonText}>Approve captain</ThemedText>
                    </TouchableOpacity>
                  </View>
                </View>
              ))}
            </View>
          ) : null}

          {!loading && section === 'banners' ? (
            <View style={styles.sectionStack}>
              <View style={[styles.card, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                <ThemedText style={[styles.cardTitle, { color: colors.text }]}>Banner auction rotation</ThemedText>
                <ThemedText type="small" style={{ color: colors.textSecondary }}>
                  Slot timing: 5s → 4s → 3s → 2s → 1s spotlight on customer home.
                </ThemedText>
              </View>
              {liveBanners.map((banner) => (
                <View key={banner.id} style={[styles.compactCard, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                  <View style={styles.flex}>
                    <ThemedText style={{ color: colors.text, fontWeight: '900' }}>{banner.title}</ThemedText>
                    <ThemedText type="small" style={{ color: colors.textSecondary }}>{banner.durationSec}s slot</ThemedText>
                  </View>
                  <StatusPill label={banner.active ? 'LIVE' : 'PAUSED'} color={banner.active ? colors.success : colors.textSecondary} />
                </View>
              ))}
            </View>
          ) : null}

          {!loading && section === 'guides' ? (
            <View style={styles.sectionStack}>
              <View style={[styles.card, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                <ThemedText style={[styles.cardTitle, { color: colors.text }]}>Guide writer access</ThemedText>
                <ThemedText type="small" style={{ color: colors.textSecondary }}>
                  Grant username/password access for trusted vets and groomers to publish health guides.
                </ThemedText>
              </View>
              {guideWriters.map((writer) => (
                <View key={writer.id} style={[styles.compactCard, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                  <View style={styles.flex}>
                    <ThemedText style={{ color: colors.text, fontWeight: '900' }}>{writer.email}</ThemedText>
                    <ThemedText type="small" style={{ color: colors.textSecondary }}>Guides-only login</ThemedText>
                  </View>
                  <TouchableOpacity
                    style={[styles.secondaryButton, { borderColor: colors.danger }]}
                    disabled={busyId === writer.id}
                    onPress={() => revokeWriter(writer.id)}
                  >
                    <ThemedText type="small" style={{ color: colors.danger, fontWeight: '900' }}>Revoke</ThemedText>
                  </TouchableOpacity>
                </View>
              ))}
            </View>
          ) : null}

          {!loading && section === 'promocodes' ? (
            <View style={styles.sectionStack}>
              {platformPromos.map((promo) => (
                <View key={promo.id} style={[styles.compactCard, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                  <View style={styles.flex}>
                    <ThemedText style={{ color: colors.text, fontWeight: '900' }}>{promo.code}</ThemedText>
                    <ThemedText type="small" style={{ color: colors.textSecondary }}>{promo.discount} · {promo.scope}</ThemedText>
                  </View>
                  <StatusPill label={'active' in promo && promo.active === false ? 'PAUSED' : 'ACTIVE'} color={colors.cta} />
                </View>
              ))}
            </View>
          ) : null}

          {!loading && section === 'disputes' ? (
            <View style={styles.sectionStack}>
              <View style={[styles.card, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                <ThemedText style={[styles.cardTitle, { color: colors.text }]}>Refund Policy Mode</ThemedText>
                <View style={styles.modeRow}>
                  {['MANUAL', 'AUTOMATED'].map((mode) => (
                    <TouchableOpacity
                      key={mode}
                      activeOpacity={0.78}
                      onPress={() => setRefundMode(mode)}
                      style={[
                        styles.modeButton,
                        { borderColor: refundMode === mode ? colors.cta : colors.border },
                        refundMode === mode && { backgroundColor: colors.muted },
                      ]}
                    >
                      <ThemedText type="small" style={{ color: colors.text, fontWeight: '900' }}>{mode}</ThemedText>
                    </TouchableOpacity>
                  ))}
                </View>
                <TouchableOpacity
                  style={[styles.primaryButton, { backgroundColor: colors.cta }]}
                  activeOpacity={0.78}
                  onPress={saveRefundMode}
                  disabled={busyId === 'refund-mode'}
                >
                  <AppIcon name="gear" color="#FFFFFF" size={18} />
                  <ThemedText type="small" style={styles.buttonText}>Save config</ThemedText>
                </TouchableOpacity>
              </View>

              {disputes.length === 0 ? (
                <EmptyState title="No disputes yet" body="Customer and merchant cases will appear here." />
              ) : (
                disputes.map((dispute) => (
                  <View key={dispute.disputeId} style={[styles.card, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                    <View style={styles.cardHeader}>
                      <View style={styles.flex}>
                        <ThemedText style={[styles.cardTitle, { color: colors.text }]}>Order {compactId(dispute.orderId)}</ThemedText>
                        <ThemedText type="small" style={{ color: colors.textSecondary }}>
                          Opened {formatDate(dispute.createdAt)}
                        </ThemedText>
                      </View>
                      <StatusPill
                        label={dispute.status}
                        color={dispute.status === 'OPEN' ? colors.danger : colors.success}
                      />
                    </View>
                    <ThemedText style={{ color: colors.text }}>{dispute.reason}</ThemedText>
                    {dispute.resolutionNotes ? (
                      <ThemedText type="small" style={{ color: colors.textSecondary }}>
                        Resolution: {dispute.resolutionNotes}
                      </ThemedText>
                    ) : null}
                    {dispute.status === 'OPEN' ? (
                      <>
                        <TextInput
                          placeholder="Resolution notes"
                          placeholderTextColor={colors.textSecondary}
                          style={[styles.input, { backgroundColor: colors.muted, color: colors.text, borderColor: colors.border }]}
                          value={disputeNotes[dispute.disputeId] ?? ''}
                          onChangeText={(text) => setDisputeNotes((current) => ({ ...current, [dispute.disputeId]: text }))}
                          multiline
                        />
                        <View style={styles.actionRow}>
                          <TouchableOpacity
                            style={[styles.primaryButton, { backgroundColor: colors.success }]}
                            activeOpacity={0.78}
                            onPress={() => resolveDispute(dispute.disputeId, 'RESOLVED')}
                            disabled={busyId === dispute.disputeId}
                          >
                            <AppIcon name="check" color="#FFFFFF" size={18} />
                            <ThemedText type="small" style={styles.buttonText}>Resolve</ThemedText>
                          </TouchableOpacity>
                          <TouchableOpacity
                            style={[styles.dangerButton, { backgroundColor: colors.danger }]}
                            activeOpacity={0.78}
                            onPress={() => resolveDispute(dispute.disputeId, 'REJECTED')}
                            disabled={busyId === dispute.disputeId}
                          >
                            <AppIcon name="xmark" color="#FFFFFF" size={18} />
                            <ThemedText type="small" style={styles.buttonText}>Reject</ThemedText>
                          </TouchableOpacity>
                        </View>
                      </>
                    ) : null}
                  </View>
                ))
              )}
            </View>
          ) : null}

          {!loading && section === 'commission' ? (
            <View style={styles.sectionStack}>
              <View style={[styles.card, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                <ThemedText style={[styles.cardTitle, { color: colors.text }]}>Commission Change Review</ThemedText>
                <ThemedText type="small" style={{ color: colors.textSecondary }}>
                  Update provider commission through the ADMIN-only provider service endpoint.
                </ThemedText>
                <TextInput
                  placeholder="Provider ID"
                  placeholderTextColor={colors.textSecondary}
                  style={[styles.input, { backgroundColor: colors.muted, color: colors.text, borderColor: colors.border }]}
                  value={commissionProviderId}
                  onChangeText={setCommissionProviderId}
                  autoCapitalize="none"
                />
                <View style={styles.formRow}>
                  <TextInput
                    placeholder="Commission %"
                    placeholderTextColor={colors.textSecondary}
                    keyboardType="decimal-pad"
                    style={[styles.input, styles.flex, { backgroundColor: colors.muted, color: colors.text, borderColor: colors.border }]}
                    value={commissionPct}
                    onChangeText={setCommissionPct}
                  />
                  <TouchableOpacity
                    style={[styles.lookupButton, { borderColor: colors.border }]}
                    activeOpacity={0.78}
                    onPress={() => {
                      const first = providers[0];
                      if (first) {
                        setCommissionProviderId(first.providerId);
                        setCommissionPct(String(first.commissionPct));
                      }
                    }}
                  >
                  <AppIcon name="shield" color={colors.cta} size={18} />
                </TouchableOpacity>
              </View>
                <TextInput
                  placeholder="Reason"
                  placeholderTextColor={colors.textSecondary}
                  style={[styles.input, { backgroundColor: colors.muted, color: colors.text, borderColor: colors.border }]}
                  value={commissionReason}
                  onChangeText={setCommissionReason}
                />
                <TouchableOpacity
                  style={[styles.primaryButton, { backgroundColor: colors.cta }]}
                  activeOpacity={0.78}
                  onPress={saveCommissionChange}
                  disabled={busyId === 'commission'}
                >
                  <AppIcon name="percent" color="#FFFFFF" size={18} />
                  <ThemedText type="small" style={styles.buttonText}>
                    {busyId === 'commission' ? 'Saving rate' : 'Save rate change'}
                  </ThemedText>
                </TouchableOpacity>
              </View>

              {commissionAudits.length === 0 ? (
                <EmptyState title="No rate changes" body="Saved provider commission changes will appear here." />
              ) : (
                commissionAudits.map((audit) => (
                  <View key={audit.id} style={[styles.compactCard, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                    <View style={styles.flex}>
                      <ThemedText style={{ color: colors.text, fontWeight: '900' }}>{audit.commissionPct}%</ThemedText>
                      <ThemedText type="small" style={{ color: colors.textSecondary }}>
                        {compactId(audit.providerId)} | {audit.reason}
                      </ThemedText>
                    </View>
                    <StatusPill label={canUseDemo ? 'DEMO' : 'SAVED'} color={colors.accent} />
                  </View>
                ))
              )}
            </View>
          ) : null}

          {!loading && section === 'support' ? (
            <View style={styles.sectionStack}>
              <View style={[styles.card, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                <ThemedText style={[styles.cardTitle, { color: colors.text }]}>Create Support Case</ThemedText>
                <TextInput
                  placeholder="Title"
                  placeholderTextColor={colors.textSecondary}
                  style={[styles.input, { backgroundColor: colors.muted, color: colors.text, borderColor: colors.border }]}
                  value={supportTitle}
                  onChangeText={setSupportTitle}
                />
                <View style={styles.modeRow}>
                  {['GENERAL', 'INFO_REQUEST'].map((actionType) => (
                    <TouchableOpacity
                      key={actionType}
                      activeOpacity={0.78}
                      onPress={() => setSupportActionType(actionType)}
                      style={[
                        styles.modeButton,
                        { borderColor: supportActionType === actionType ? colors.cta : colors.border },
                        supportActionType === actionType && { backgroundColor: colors.muted },
                      ]}
                    >
                      <ThemedText type="small" style={{ color: colors.text, fontWeight: '900' }}>{actionType}</ThemedText>
                    </TouchableOpacity>
                  ))}
                </View>
                <TextInput
                  placeholder="Detail"
                  placeholderTextColor={colors.textSecondary}
                  style={[styles.input, { backgroundColor: colors.muted, color: colors.text, borderColor: colors.border }]}
                  value={supportDetail}
                  onChangeText={setSupportDetail}
                  multiline
                />
                <View style={styles.formRow}>
                  <TextInput
                    placeholder="Entity type"
                    placeholderTextColor={colors.textSecondary}
                    style={[styles.input, styles.flex, { backgroundColor: colors.muted, color: colors.text, borderColor: colors.border }]}
                    value={supportEntityType}
                    onChangeText={setSupportEntityType}
                    autoCapitalize="characters"
                  />
                  <TextInput
                    placeholder="Entity ID"
                    placeholderTextColor={colors.textSecondary}
                    style={[styles.input, styles.flex, { backgroundColor: colors.muted, color: colors.text, borderColor: colors.border }]}
                    value={supportEntityId}
                    onChangeText={setSupportEntityId}
                    autoCapitalize="none"
                  />
                </View>
                <TouchableOpacity
                  style={[styles.primaryButton, { backgroundColor: colors.cta }]}
                  activeOpacity={0.78}
                  onPress={() => createSupportCase()}
                  disabled={busyId?.startsWith('support-')}
                >
                  <AppIcon name="support" color="#FFFFFF" size={18} />
                  <ThemedText type="small" style={styles.buttonText}>Open support case</ThemedText>
                </TouchableOpacity>
              </View>

              <View style={[styles.card, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                <ThemedText style={[styles.cardTitle, { color: colors.text }]}>Quick Actions</ThemedText>
                <View style={styles.presetGrid}>
                  {SUPPORT_PRESETS.map((preset) => (
                    <TouchableOpacity
                      key={preset.title}
                      style={[styles.presetButton, { borderColor: colors.border, backgroundColor: colors.muted }]}
                      activeOpacity={0.78}
                      onPress={() => createSupportCase(preset)}
                    >
                      <ThemedText type="small" style={{ color: colors.text, fontWeight: '800' }}>{preset.title}</ThemedText>
                    </TouchableOpacity>
                  ))}
                </View>
              </View>

              {supportCases.length === 0 ? (
                <EmptyState title="No support cases" body="Admin support actions will appear here for handoff." />
              ) : (
                supportCases.map((supportCase) => (
                  <View key={supportCase.supportCaseId} style={[styles.card, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                    <View style={styles.cardHeader}>
                      <View style={styles.flex}>
                        <ThemedText style={{ color: colors.text, fontWeight: '900' }}>{supportCase.title}</ThemedText>
                        <ThemedText type="small" style={{ color: colors.textSecondary }}>
                          {supportCase.actionType} | {supportCase.entityType ?? 'GENERAL'} | {formatDate(supportCase.createdAt)}
                        </ThemedText>
                      </View>
                      <StatusPill
                        label={supportCase.status}
                        color={supportCase.status === 'OPEN' ? colors.warning : colors.success}
                      />
                    </View>
                    <ThemedText type="small" style={{ color: colors.text }}>{supportCase.detail}</ThemedText>
                    {supportCase.entityId ? (
                      <Meta label="Entity ID" value={compactId(supportCase.entityId)} />
                    ) : null}
                    {supportCase.resolutionNotes ? (
                      <ThemedText type="small" style={{ color: colors.textSecondary }}>
                        Resolution: {supportCase.resolutionNotes}
                      </ThemedText>
                    ) : null}
                    {supportCase.status === 'OPEN' ? (
                      <>
                        <TextInput
                          placeholder="Resolution notes"
                          placeholderTextColor={colors.textSecondary}
                          style={[styles.input, { backgroundColor: colors.muted, color: colors.text, borderColor: colors.border }]}
                          value={supportResolutionNotes[supportCase.supportCaseId] ?? ''}
                          onChangeText={(text) => setSupportResolutionNotes((current) => ({
                            ...current,
                            [supportCase.supportCaseId]: text,
                          }))}
                          multiline
                        />
                        <TouchableOpacity
                          style={[styles.primaryButton, { backgroundColor: colors.success }]}
                          activeOpacity={0.78}
                          onPress={() => resolveSupportCase(supportCase.supportCaseId)}
                          disabled={busyId === supportCase.supportCaseId}
                        >
                          <AppIcon name="check" color="#FFFFFF" size={18} />
                          <ThemedText type="small" style={styles.buttonText}>Resolve support case</ThemedText>
                        </TouchableOpacity>
                      </>
                    ) : null}
                  </View>
                ))
              )}
            </View>
          ) : null}
        </ScrollView>
      </SafeAreaView>
    </ThemedView>
  );
}

function EmptyState({ title, body }: { title: string; body: string }) {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];
  return (
    <View style={[styles.emptyState, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
      <AppIcon name="sparkle" color={colors.cta} size={22} />
      <View style={styles.flex}>
        <ThemedText style={{ color: colors.text, fontWeight: '900' }}>{title}</ThemedText>
        <ThemedText type="small" style={{ color: colors.textSecondary }}>{body}</ThemedText>
      </View>
    </View>
  );
}

function Meta({ label, value }: { label: string; value: string }) {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];
  return (
    <View style={[styles.metaItem, { backgroundColor: colors.muted }]}>
      <ThemedText type="small" style={{ color: colors.textSecondary }}>{label}</ThemedText>
      <ThemedText type="small" style={{ color: colors.text, fontWeight: '900' }} numberOfLines={1}>
        {value}
      </ThemedText>
    </View>
  );
}

function StatusPill({ label, color }: { label: string; color: string }) {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];
  return (
    <View style={[styles.statusPill, { backgroundColor: colors.muted }]}>
      <View style={[styles.statusDot, { backgroundColor: color }]} />
      <ThemedText type="small" style={{ color: colors.text, fontWeight: '900' }}>{label}</ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  safeArea: { flex: 1 },
  scrollContent: {
    padding: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.four,
  },
  hero: {
    minHeight: 132,
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.four,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    ...Shadows.card,
  },
  heroIcon: {
    width: 56,
    height: 56,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroCopy: { flex: 1, gap: Spacing.one },
  heroTitle: { fontSize: 27, fontWeight: '900' },
  notice: {
    minHeight: 52,
    borderRadius: Radius.md,
    borderWidth: 1,
    padding: Spacing.three,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  statsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  statCard: {
    width: '48%',
    minHeight: 112,
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.three,
    gap: Spacing.one,
  },
  statIcon: {
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
  },
  statValue: { fontSize: 21, fontWeight: '900' },
  segmented: {
    borderRadius: Radius.lg,
    padding: Spacing.one,
    flexDirection: 'row',
    gap: Spacing.one,
  },
  segment: {
    minWidth: 88,
    minHeight: 48,
    borderRadius: Radius.md,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.half,
    paddingHorizontal: Spacing.two,
  },
  loadingPanel: {
    minHeight: 120,
    borderRadius: Radius.lg,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },
  sectionStack: { gap: Spacing.three },
  card: {
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.three,
    gap: Spacing.three,
  },
  compactCard: {
    minHeight: 68,
    borderRadius: Radius.md,
    borderWidth: 1,
    padding: Spacing.three,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  cardTitle: { fontSize: 17, fontWeight: '900' },
  flex: { flex: 1 },
  statusPill: {
    minHeight: 34,
    borderRadius: 17,
    paddingHorizontal: Spacing.two,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
  },
  statusDot: { width: 8, height: 8, borderRadius: 4 },
  metaGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  metaItem: {
    width: '48%',
    borderRadius: Radius.md,
    padding: Spacing.two,
    gap: Spacing.half,
  },
  actionRow: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  primaryButton: {
    minHeight: 48,
    borderRadius: Radius.md,
    paddingHorizontal: Spacing.three,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.one,
    flex: 1,
  },
  dangerButton: {
    minHeight: 48,
    borderRadius: Radius.md,
    paddingHorizontal: Spacing.three,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.one,
    flex: 1,
  },
  secondaryButton: {
    minHeight: 48,
    borderRadius: Radius.md,
    borderWidth: 1,
    paddingHorizontal: Spacing.three,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.one,
    flex: 1,
  },
  buttonText: { color: '#FFFFFF', fontWeight: '900' },
  input: {
    minHeight: 48,
    borderRadius: Radius.md,
    borderWidth: 1,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    fontSize: 15,
  },
  modeRow: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  modeButton: {
    minHeight: 48,
    borderRadius: Radius.md,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    flex: 1,
  },
  formRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  lookupButton: {
    minHeight: 48,
    width: 54,
    borderRadius: Radius.md,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  presetGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  presetButton: {
    width: '48%',
    minHeight: 58,
    borderRadius: Radius.md,
    borderWidth: 1,
    padding: Spacing.two,
    justifyContent: 'center',
  },
  emptyState: {
    minHeight: 84,
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.three,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
});
