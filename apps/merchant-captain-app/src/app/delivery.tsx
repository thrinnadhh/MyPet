import { useRouter } from 'expo-router';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Linking, Modal, Pressable, StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import {
  ActionButton,
  AppBar,
  FeedbackBanner,
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
import { radii, spacing, touchTarget, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import {
  type CaptainDeliveryJob,
  deliveryStepForStatus,
  fetchCaptainJobs,
  isActiveCaptainJob,
  submitCaptainProof,
} from '@/services/captain-deliveries';
import {
  CaptainLocationError,
  getCaptainCoordinates,
  startCaptainLocationTracking,
  stopCaptainLocationTracking,
  syncCaptainLocationNow,
} from '@/services/captain-location';
import { formatDateTime, formatDeliveryStatus } from '@/utils/formatters';
import { appConfig } from '@/utils/app-config';

interface DispatchOffer {
  offerId: string;
  jobId: string;
  captainId: string;
  offeredAt: string;
  response: string | null;
  offerRank: number;
  orderId: string;
}

type DeliveryStep = 1 | 2 | 3 | 4;

function shortOrderId(orderId: string): string {
  return orderId.slice(0, 8).toUpperCase();
}

async function responseError(response: Response, fallback: string): Promise<string> {
  const body = (await response.json().catch(() => null)) as { error?: string; message?: string } | null;
  return body?.message ?? body?.error ?? fallback;
}

function historyTone(status: CaptainDeliveryJob['status']): 'success' | 'danger' | 'warning' | 'info' | 'neutral' {
  if (status === 'COMPLETED') return 'success';
  if (status === 'FAILED' || status === 'REJECTED' || status === 'TIMED_OUT') return 'danger';
  if (status === 'ACCEPTED' || status === 'PICKED_UP') return 'info';
  if (status === 'OFFERED' || status === 'PENDING_ASSIGNMENT') return 'warning';
  return 'neutral';
}

export default function DeliveryScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { user, session } = useAuth();

  const [isOnline, setIsOnline] = useState(false);
  const [loading, setLoading] = useState(false);
  const [restoring, setRestoring] = useState(true);
  const [jobs, setJobs] = useState<CaptainDeliveryJob[]>([]);
  const [jobError, setJobError] = useState<unknown>(null);
  const [activeOffer, setActiveOffer] = useState<DispatchOffer | null>(null);
  const [offerCountdown, setOfferCountdown] = useState(30);
  const [activeDelivery, setActiveDelivery] = useState<CaptainDeliveryJob | null>(null);
  const [deliveryStep, setDeliveryStep] = useState<DeliveryStep>(1);
  const [pickupProof, setPickupProof] = useState('');
  const [handoverProof, setHandoverProof] = useState('');
  const [verifyingProof, setVerifyingProof] = useState(false);

  const authHeaders = useCallback(
    (json = false): Record<string, string> => {
      const headers: Record<string, string> = {};
      if (json) headers['Content-Type'] = 'application/json';
      if (user?.id) headers['X-User-Id'] = user.id;
      if (session?.access_token) headers.Authorization = `Bearer ${session.access_token}`;
      return headers;
    },
    [session, user],
  );

  const startActiveTracking = useCallback(async () => {
    if (!user?.id || !session?.access_token) return;
    const result = await startCaptainLocationTracking({
      apiBaseUrl: appConfig.apiBaseUrl,
      userId: user.id,
      accessToken: session.access_token,
      allowDemoMode: appConfig.allowDemoMode,
    });
    if (result.warning) Alert.alert('Background tracking limited', result.warning);
  }, [session, user]);

  const loadJobs = useCallback(async () => {
    if (!user || !session) {
      setRestoring(false);
      return;
    }
    setJobError(null);
    try {
      const nextJobs = await fetchCaptainJobs();
      setJobs(nextJobs);
      const active = nextJobs.find(isActiveCaptainJob) ?? null;
      setActiveDelivery(active);
      if (active) {
        setDeliveryStep(deliveryStepForStatus(active.status));
        setIsOnline(true);
        await startActiveTracking();
      }
    } catch (error) {
      setJobError(error);
    } finally {
      setRestoring(false);
    }
  }, [session, startActiveTracking, user]);

  useEffect(() => {
    void loadJobs();
  }, [loadJobs]);

  const updateLocation = useCallback(async () => {
    if (!user?.id || !session?.access_token) return false;
    return syncCaptainLocationNow({
      apiBaseUrl: appConfig.apiBaseUrl,
      userId: user.id,
      accessToken: session.access_token,
      allowDemoMode: appConfig.allowDemoMode,
    });
  }, [session, user]);

  const toggleOnline = useCallback(async () => {
    if (!user || !session?.access_token) return;
    setLoading(true);
    try {
      const nextOnline = !isOnline;
      if (!nextOnline && activeDelivery) {
        throw new Error('Complete or escalate the active delivery before going offline.');
      }
      const coordinates = nextOnline
        ? await getCaptainCoordinates({ allowDemoMode: appConfig.allowDemoMode })
        : null;
      const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/captains/status`, {
        method: 'PUT',
        headers: authHeaders(true),
        body: JSON.stringify({
          online: nextOnline,
          longitude: coordinates?.longitude ?? null,
          latitude: coordinates?.latitude ?? null,
        }),
      });
      if (!response.ok) throw new Error(await responseError(response, 'Could not update captain availability.'));

      if (!nextOnline) {
        await stopCaptainLocationTracking();
        setActiveOffer(null);
      }
      setIsOnline(nextOnline);
    } catch (error: unknown) {
      if (error instanceof CaptainLocationError && error.code === 'permission-blocked') {
        Alert.alert('Location permission blocked', error.message, [
          { text: 'Cancel', style: 'cancel' },
          { text: 'Open settings', onPress: () => void Linking.openSettings() },
        ]);
      } else {
        Alert.alert('Status change failed', apiErrorMessage(error, 'Please check your connection.'));
      }
    } finally {
      setLoading(false);
    }
  }, [activeDelivery, authHeaders, isOnline, session, user]);

  useEffect(() => {
    if (!isOnline || !user) return undefined;
    const interval = setInterval(() => void updateLocation(), 20_000);
    return () => clearInterval(interval);
  }, [isOnline, updateLocation, user]);

  useEffect(() => {
    if (!isOnline || !user || activeDelivery || activeOffer) return undefined;
    const poll = async () => {
      try {
        const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/dispatch/offers`, { headers: authHeaders() });
        if (!response.ok) return;
        const offers = (await response.json()) as DispatchOffer[];
        const next = offers.find((offer) => offer.response === null);
        if (next) {
          setActiveOffer(next);
          setOfferCountdown(30);
        }
      } catch {
        // Polling retries naturally; never invent an offer.
      }
    };
    void poll();
    const interval = setInterval(() => void poll(), 4000);
    return () => clearInterval(interval);
  }, [activeDelivery, activeOffer, authHeaders, isOnline, user]);

  useEffect(() => {
    if (!activeOffer) return undefined;
    const timer = setInterval(() => {
      setOfferCountdown((value) => {
        if (value <= 1) {
          setActiveOffer(null);
          return 0;
        }
        return value - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [activeOffer]);

  const respondToOffer = useCallback(async (answer: 'ACCEPTED' | 'REJECTED') => {
    if (!activeOffer) return;
    setLoading(true);
    try {
      const response = await fetch(
        `${appConfig.apiBaseUrl}/api/v1/dispatch/offers/${activeOffer.offerId}/respond?response=${answer}`,
        { method: 'POST', headers: authHeaders() },
      );
      if (!response.ok) throw new Error(await responseError(response, 'The offer may have expired.'));
      setActiveOffer(null);
      if (answer === 'ACCEPTED') {
        await loadJobs();
      }
    } catch (error) {
      Alert.alert('Offer response failed', apiErrorMessage(error, 'Please try again.'));
      setActiveOffer(null);
    } finally {
      setLoading(false);
    }
  }, [activeOffer, authHeaders, loadJobs]);

  const callCustomer = useCallback(async () => {
    const phone = activeDelivery?.customerPhone;
    if (!phone) {
      Alert.alert('Customer contact unavailable', 'This active delivery does not have a callable customer number.');
      return;
    }
    const dialUrl = `tel:${phone}`;
    const supported = await Linking.canOpenURL(dialUrl);
    if (!supported) {
      Alert.alert('Calling unavailable', 'This device cannot open the phone dialer.');
      return;
    }
    await Linking.openURL(dialUrl);
  }, [activeDelivery?.customerPhone]);

  const submitProof = useCallback(async (kind: 'pickup' | 'deliver', proofCode: string) => {
    if (!activeDelivery) return;
    const cleaned = proofCode.trim();
    if (cleaned.length < 4) {
      Alert.alert('Verification code required', 'Enter the code provided by the merchant or customer.');
      return;
    }

    setVerifyingProof(true);
    try {
      const updated = await submitCaptainProof(activeDelivery.jobId, kind, cleaned);
      setJobs((current) => [updated, ...current.filter((job) => job.jobId !== updated.jobId)]);
      if (kind === 'pickup') {
        setPickupProof('');
        setActiveDelivery(updated);
        setDeliveryStep(3);
      } else {
        setHandoverProof('');
        setActiveDelivery(null);
        setDeliveryStep(1);
        await stopCaptainLocationTracking();
        Alert.alert('Delivery completed', 'The server recorded the verified handover.');
      }
    } catch (error) {
      Alert.alert('Verification failed', apiErrorMessage(error, 'Please verify the code and retry.'));
      void loadJobs();
    } finally {
      setVerifyingProof(false);
    }
  }, [activeDelivery, loadJobs]);

  const progress = useMemo(() => (activeDelivery ? deliveryStep / 4 : 0), [activeDelivery, deliveryStep]);
  const history = useMemo(() => jobs.filter((job) => !isActiveCaptainJob(job)).slice(0, 10), [jobs]);

  if (restoring) {
    return (
      <ScreenShell scroll={false} header={<AppBar title="Delivery operations" />}>
        <StateView kind="loading" title="Restoring delivery state" message="Checking your assigned jobs…" />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell
      header={<AppBar eyebrow="CAPTAIN WORKSPACE" title="Delivery operations" subtitle="Resume assignments and complete verified handovers" action={<RoleBadge role="captain" />} />}
      testID="captain-delivery"
    >
      {jobError ? <FeedbackBanner tone="danger" title="Delivery history unavailable" message={apiErrorMessage(jobError)} /> : null}
      <FeedbackBanner
        tone={appConfig.allowDemoMode ? 'warning' : isOnline ? 'success' : 'info'}
        title={appConfig.allowDemoMode ? 'Sandbox location mode' : isOnline ? 'Online and discoverable' : 'Currently offline'}
        message={activeDelivery ? 'Active work is restored from the dispatch service and background tracking is enabled.' : 'Background tracking starts only after accepting a delivery.'}
        icon={appConfig.allowDemoMode ? 'sparkle' : isOnline ? 'check' : 'location'}
      />

      <AppCard style={styles.onboardingCard}>
        <View style={[styles.roundIcon, { backgroundColor: theme.primarySoft }]}><AppIcon name="shield" color={theme.primary} size={22} /></View>
        <View style={styles.flex}>
          <ThemedText style={styles.cardTitle}>Captain verification</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">Identity, vehicle and bank approval are required before delivery work.</ThemedText>
        </View>
        <ActionButton label="Open" variant="ghost" onPress={() => router.push('/captain-onboarding' as never)} />
      </AppCard>

      {activeDelivery ? (
        <AppCard style={styles.deliveryCard}>
          <View style={styles.cardHeader}>
            <View style={styles.flex}>
              <ThemedText style={styles.cardTitle}>Order #{shortOrderId(activeDelivery.orderId)}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">{formatDeliveryStatus(activeDelivery.status)} · restored from server</ThemedText>
            </View>
            <StatusBadge label={`STEP ${deliveryStep} OF 4`} tone="info" />
          </View>
          <View style={[styles.progressTrack, { backgroundColor: theme.muted }]} accessibilityRole="progressbar" accessibilityValue={{ min: 0, max: 4, now: deliveryStep }}>
            <View style={[styles.progressFill, { backgroundColor: theme.primary, width: `${progress * 100}%` }]} />
          </View>

          {activeDelivery.customerPhone ? (
            <View style={[styles.contactCard, { backgroundColor: theme.background, borderColor: theme.border }]}>
              <View style={styles.flex}>
                <ThemedText type="smallBold">Customer delivery contact</ThemedText>
                <ThemedText>{activeDelivery.customerPhone}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {activeDelivery.customerPhoneVerified
                    ? 'This number matches a phone verified by the customer authentication flow.'
                    : 'Customer-provided delivery contact. It has not been OTP-verified by MyPet.'}
                </ThemedText>
              </View>
              <StatusBadge label={activeDelivery.customerPhoneVerified ? 'VERIFIED PHONE' : 'CUSTOMER PROVIDED'} tone={activeDelivery.customerPhoneVerified ? 'success' : 'info'} />
              <ActionButton label="Call customer" onPress={() => void callCustomer()} />
            </View>
          ) : null}

          {deliveryStep === 1 ? <DeliveryStepView icon="store" title="Travel to the pickup store" message="Destination details remain protected until an authorized delivery-context API is available." action="I have arrived" onAction={() => setDeliveryStep(2)} /> : null}
          {deliveryStep === 2 ? (
            <View style={styles.stepContent}>
              <StepIcon name="inventory" />
              <ThemedText style={styles.stepTitle}>Verify pickup</ThemedText>
              <ThemedText type="small" themeColor="textSecondary" style={styles.centerText}>Enter the merchant-issued code. The dispatch service validates it and changes the job to Picked up.</ThemedText>
              <TextField label="Pickup verification code" value={pickupProof} onChangeText={setPickupProof} keyboardType="number-pad" maxLength={8} autoComplete="one-time-code" />
              <ActionButton label="Confirm pickup" icon="check" loading={verifyingProof} onPress={() => void submitProof('pickup', pickupProof)} />
            </View>
          ) : null}
          {deliveryStep === 3 ? <DeliveryStepView icon="truck" title="Travel to the customer" message="Use the active delivery contact above if you need to call the customer. The job resumes here after an app restart." action="I have arrived" onAction={() => setDeliveryStep(4)} /> : null}
          {deliveryStep === 4 ? (
            <View style={styles.stepContent}>
              <StepIcon name="check" success />
              <ThemedText style={styles.stepTitle}>Verify handover</ThemedText>
              <ThemedText type="small" themeColor="textSecondary" style={styles.centerText}>Enter the customer-issued code. Completion is accepted only from the Picked up state.</ThemedText>
              <TextField label="Handover verification code" value={handoverProof} onChangeText={setHandoverProof} keyboardType="number-pad" maxLength={8} autoComplete="one-time-code" />
              <ActionButton label="Complete delivery" icon="check" loading={verifyingProof} onPress={() => void submitProof('deliver', handoverProof)} />
            </View>
          ) : null}
        </AppCard>
      ) : (
        <AppCard style={styles.availabilityCard}>
          <View style={styles.cardHeader}>
            <View style={styles.statusTitleRow}><View style={[styles.statusDot, { backgroundColor: isOnline ? theme.success : theme.textSecondary }]} /><ThemedText style={styles.cardTitle}>{isOnline ? 'Online and waiting' : 'Offline'}</ThemedText></View>
            <StatusBadge label={isOnline ? 'ONLINE' : 'OFFLINE'} tone={isOnline ? 'success' : 'neutral'} />
          </View>
          <ThemedText type="small" themeColor="textSecondary">{isOnline ? 'Foreground location stays fresh while waiting. Background tracking begins after acceptance.' : 'A fresh device location is required before going online.'}</ThemedText>
          <ActionButton label={isOnline ? 'Go offline' : 'Go online'} variant={isOnline ? 'destructive' : 'primary'} icon={isOnline ? 'xmark' : 'location'} loading={loading} onPress={() => void toggleOnline()} />
        </AppCard>
      )}

      <View style={styles.historySection}>
        <SectionHeader title="Delivery history" subtitle="Your most recent server-assigned jobs" actionLabel="Refresh" onAction={() => void loadJobs()} />
        {history.length === 0 ? <StateView kind="empty" title="No completed delivery history" message="Completed and failed jobs will appear here." /> : (
          <View style={styles.historyList}>
            {history.map((job) => (
              <AppCard key={job.jobId} style={styles.historyCard}>
                <View style={styles.cardHeader}>
                  <View style={[styles.roundIcon, { backgroundColor: theme.muted }]}><AppIcon name="history" color={theme.primary} size={20} /></View>
                  <View style={styles.flex}>
                    <ThemedText type="smallBold">Order #{shortOrderId(job.orderId)}</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">{formatDateTime(job.resolvedAt ?? job.assignedAt ?? job.createdAt)}</ThemedText>
                  </View>
                  <StatusBadge label={formatDeliveryStatus(job.status)} tone={historyTone(job.status)} />
                </View>
              </AppCard>
            ))}
          </View>
        )}
      </View>

      <Modal visible={activeOffer !== null} transparent animationType="fade" onRequestClose={() => setActiveOffer(null)}>
        <View style={styles.modalBackdrop}>
          <View style={[styles.offerCard, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]} accessibilityViewIsModal>
            <View style={styles.cardHeader}>
              <View style={styles.flex}><ThemedText style={styles.offerTitle}>Incoming dispatch offer</ThemedText><ThemedText type="small" themeColor="textSecondary">Order #{activeOffer ? shortOrderId(activeOffer.orderId) : ''}</ThemedText></View>
              <StatusBadge label={`${offerCountdown}s`} tone={offerCountdown <= 10 ? 'danger' : 'warning'} />
            </View>
            <View style={[styles.progressTrack, { backgroundColor: theme.muted }]}><View style={[styles.progressFill, { backgroundColor: offerCountdown <= 10 ? theme.danger : theme.primary, width: `${(offerCountdown / 30) * 100}%` }]} /></View>
            <ThemedText type="small" themeColor="textSecondary">Accepting creates a resumable server assignment. Proof codes and protected destinations are never included in this offer.</ThemedText>
            <View style={styles.offerActions}>
              <ActionButton label="Decline" variant="ghost" disabled={loading} onPress={() => void respondToOffer('REJECTED')} style={styles.offerAction} />
              <ActionButton label="Accept job" icon="check" loading={loading} onPress={() => void respondToOffer('ACCEPTED')} style={styles.offerAction} />
            </View>
            <Pressable onPress={() => setActiveOffer(null)} accessibilityRole="button" accessibilityLabel="Close dispatch offer" style={styles.modalClose}><AppIcon name="xmark" color={theme.textSecondary} size={20} /></Pressable>
          </View>
        </View>
      </Modal>
    </ScreenShell>
  );
}

function StepIcon({ name, success }: { name: 'inventory' | 'check'; success?: boolean }) {
  const theme = useTheme();
  return <View style={[styles.stepIcon, { backgroundColor: success ? theme.successSoft : theme.primarySoft }]}><AppIcon name={name} color={success ? theme.success : theme.primary} size={28} /></View>;
}

function DeliveryStepView({ icon, title, message, action, onAction }: { icon: 'store' | 'truck'; title: string; message: string; action: string; onAction: () => void }) {
  const theme = useTheme();
  return <View style={styles.stepContent}><View style={[styles.stepIcon, { backgroundColor: theme.primarySoft }]}><AppIcon name={icon} color={theme.primary} size={28} /></View><ThemedText style={styles.stepTitle}>{title}</ThemedText><ThemedText type="small" themeColor="textSecondary" style={styles.centerText}>{message}</ThemedText><ActionButton label={action} icon={icon === 'store' ? 'check' : 'location'} onPress={onAction} /></View>;
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  onboardingCard: { minHeight: 104, padding: spacing.x4, flexDirection: 'row', alignItems: 'center', gap: spacing.x3 },
  roundIcon: { width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
  cardTitle: { ...typography.title, fontSize: 18, lineHeight: 24 },
  deliveryCard: { padding: spacing.x4, gap: spacing.x4 },
  contactCard: { borderWidth: StyleSheet.hairlineWidth, borderRadius: radii.compact, padding: spacing.x3, gap: spacing.x3 },
  availabilityCard: { padding: spacing.x4, gap: spacing.x4 },
  cardHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.x3 },
  statusTitleRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.x2 },
  statusDot: { width: 12, height: 12, borderRadius: 6 },
  progressTrack: { height: 8, borderRadius: 4, overflow: 'hidden' },
  progressFill: { height: '100%', borderRadius: 4 },
  stepContent: { alignItems: 'center', gap: spacing.x3, paddingVertical: spacing.x4 },
  stepIcon: { width: 64, height: 64, borderRadius: 32, alignItems: 'center', justifyContent: 'center' },
  stepTitle: { ...typography.title, textAlign: 'center' },
  centerText: { textAlign: 'center', maxWidth: 520 },
  historySection: { gap: spacing.x3 },
  historyList: { gap: spacing.x2 },
  historyCard: { padding: spacing.x3 },
  modalBackdrop: { flex: 1, backgroundColor: 'rgba(11,28,48,0.58)', alignItems: 'center', justifyContent: 'center', padding: spacing.x4 },
  offerCard: { width: '100%', maxWidth: 520, borderWidth: StyleSheet.hairlineWidth, borderRadius: radii.feature, padding: spacing.x6, gap: spacing.x4 },
  offerTitle: { ...typography.title },
  offerActions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  offerAction: { flexGrow: 1, flexBasis: 160 },
  modalClose: { position: 'absolute', top: -touchTarget / 2, right: 0, width: touchTarget, height: touchTarget, alignItems: 'center', justifyContent: 'center' },
});