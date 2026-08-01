import { useRouter } from 'expo-router';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Linking, Modal, Pressable, StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import {
  ActionButton,
  AppBar,
  FeedbackBanner,
  RoleBadge,
  StatusBadge,
} from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { AppCard } from '@/components/ui/app-card';
import { TextField } from '@/components/ui/text-field';
import { useAuth } from '@/context/AuthContext';
import { radii, spacing, touchTarget, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import {
  CaptainLocationError,
  getCaptainCoordinates,
  startCaptainLocationTracking,
  stopCaptainLocationTracking,
  syncCaptainLocationNow,
} from '@/services/captain-location';
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

interface ActiveDelivery {
  jobId: string;
  orderId: string;
  deliveryFee: number | null;
}

type DeliveryStep = 1 | 2 | 3 | 4;

function shortOrderId(orderId: string): string {
  return orderId.slice(0, 8).toUpperCase();
}

async function responseError(response: Response, fallback: string): Promise<string> {
  const body = (await response.json().catch(() => null)) as { error?: string; message?: string } | null;
  return body?.error ?? body?.message ?? fallback;
}

export default function DeliveryScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { user, session } = useAuth();

  const [isOnline, setIsOnline] = useState(false);
  const [loading, setLoading] = useState(false);
  const [activeOffer, setActiveOffer] = useState<DispatchOffer | null>(null);
  const [offerCountdown, setOfferCountdown] = useState(30);
  const [activeDelivery, setActiveDelivery] = useState<ActiveDelivery | null>(null);
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

  const getCoordinates = useCallback(
    () => getCaptainCoordinates({ allowDemoMode: appConfig.allowDemoMode }),
    [],
  );

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
    if (!user) return;
    setLoading(true);
    try {
      const nextOnline = !isOnline;
      const coordinates = nextOnline ? await getCoordinates() : null;

      const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/captains/status`, {
        method: 'PUT',
        headers: authHeaders(true),
        body: JSON.stringify({
          online: nextOnline,
          longitude: coordinates?.longitude ?? null,
          latitude: coordinates?.latitude ?? null,
        }),
      });

      if (!response.ok) {
        throw new Error(await responseError(response, 'Could not update captain availability.'));
      }

      if (nextOnline) {
        if (!session?.access_token) {
          throw new CaptainLocationError('session-missing', 'An authenticated captain session is required.');
        }
        const tracking = await startCaptainLocationTracking({
          apiBaseUrl: appConfig.apiBaseUrl,
          userId: user.id,
          accessToken: session.access_token,
          allowDemoMode: appConfig.allowDemoMode,
        });
        if (tracking.warning) {
          Alert.alert('Background tracking limited', tracking.warning);
        }
      } else {
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
        Alert.alert('Status change failed', error instanceof Error ? error.message : 'Please check your connection.');
      }
    } finally {
      setLoading(false);
    }
  }, [authHeaders, getCoordinates, isOnline, session, user]);

  useEffect(() => {
    if (!isOnline || !user) return undefined;
    const interval = setInterval(() => void updateLocation(), 20000);
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
        // Polling failures are represented by the next retry; no false offer is created.
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

  const respondToOffer = useCallback(
    async (answer: 'ACCEPTED' | 'REJECTED') => {
      if (!activeOffer) return;
      setLoading(true);
      try {
        const response = await fetch(
          `${appConfig.apiBaseUrl}/api/v1/dispatch/offers/${activeOffer.offerId}/respond?response=${answer}`,
          { method: 'POST', headers: authHeaders() },
        );
        if (!response.ok) throw new Error(await responseError(response, 'The offer may have expired.'));
        if (answer === 'ACCEPTED') {
          setActiveDelivery({ jobId: activeOffer.jobId, orderId: activeOffer.orderId, deliveryFee: null });
          setDeliveryStep(1);
        }
        setActiveOffer(null);
      } catch (error: unknown) {
        Alert.alert('Offer response failed', error instanceof Error ? error.message : 'Please try again.');
        setActiveOffer(null);
      } finally {
        setLoading(false);
      }
    },
    [activeOffer, authHeaders],
  );

  const submitProof = useCallback(
    async (kind: 'pickup' | 'deliver', proofCode: string) => {
      if (!activeDelivery) return;
      const cleaned = proofCode.trim();
      if (cleaned.length < 4) {
        Alert.alert('Verification code required', 'Enter the code provided by the merchant or customer.');
        return;
      }

      setVerifyingProof(true);
      try {
        const response = await fetch(
          `${appConfig.apiBaseUrl}/api/v1/dispatch/jobs/${activeDelivery.jobId}/${kind}`,
          {
            method: 'POST',
            headers: authHeaders(true),
            body: JSON.stringify({ proofCode: cleaned }),
          },
        );
        if (!response.ok) {
          throw new Error(await responseError(response, 'The verification code was rejected.'));
        }

        if (kind === 'pickup') {
          setPickupProof('');
          setDeliveryStep(3);
        } else {
          setHandoverProof('');
          setActiveDelivery(null);
          setDeliveryStep(1);
          Alert.alert('Delivery completed', 'The order was delivered and the server recorded the handover.');
        }
      } catch (error: unknown) {
        Alert.alert('Verification failed', error instanceof Error ? error.message : 'Please verify the code and retry.');
      } finally {
        setVerifyingProof(false);
      }
    },
    [activeDelivery, authHeaders],
  );

  const progress = useMemo(() => (activeDelivery ? deliveryStep / 4 : 0), [activeDelivery, deliveryStep]);

  return (
    <ScreenShell
      header={
        <AppBar
          eyebrow="CAPTAIN WORKSPACE"
          title="Delivery operations"
          subtitle="Accept dispatch offers and complete verified handovers"
          action={<RoleBadge role="captain" />}
        />
      }
      testID="captain-delivery"
    >
      <FeedbackBanner
        tone={appConfig.allowDemoMode ? 'warning' : isOnline ? 'success' : 'info'}
        title={appConfig.allowDemoMode ? 'Sandbox location mode' : isOnline ? 'Online and discoverable' : 'Currently offline'}
        message={
          appConfig.allowDemoMode
            ? 'Demo coordinates are used only because demo mode is explicitly enabled.'
            : isOnline
              ? 'Verified device location updates are sent while you are online.'
              : 'MyPet never sends placeholder coordinates in production.'
        }
        icon={appConfig.allowDemoMode ? 'sparkle' : isOnline ? 'check' : 'location'}
      />

      <AppCard style={styles.onboardingCard}>
        <View style={[styles.roundIcon, { backgroundColor: theme.primarySoft }]}>
          <AppIcon name="shield" color={theme.primary} size={22} />
        </View>
        <View style={styles.flex}>
          <ThemedText style={styles.cardTitle}>Captain verification</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            Complete identity, vehicle, selfie, and bank verification before going online.
          </ThemedText>
        </View>
        <ActionButton label="Open" variant="ghost" onPress={() => router.push('/captain-onboarding' as never)} />
      </AppCard>

      {activeDelivery ? (
        <AppCard style={styles.deliveryCard}>
          <View style={styles.cardHeader}>
            <View style={styles.flex}>
              <ThemedText style={styles.cardTitle}>Order #{shortOrderId(activeDelivery.orderId)}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">Active delivery job</ThemedText>
            </View>
            <StatusBadge label={`STEP ${deliveryStep} OF 4`} tone="info" />
          </View>

          <View style={[styles.progressTrack, { backgroundColor: theme.muted }]} accessibilityRole="progressbar" accessibilityValue={{ min: 0, max: 4, now: deliveryStep }}>
            <View style={[styles.progressFill, { backgroundColor: theme.primary, width: `${progress * 100}%` }]} />
          </View>

          {deliveryStep === 1 ? (
            <View style={styles.stepContent}>
              <View style={[styles.stepIcon, { backgroundColor: theme.primarySoft }]}>
                <AppIcon name="store" color={theme.primary} size={28} />
              </View>
              <ThemedText style={styles.stepTitle}>Travel to the pickup store</ThemedText>
              <ThemedText type="small" themeColor="textSecondary" style={styles.centerText}>
                Pickup address and navigation require the order-detail location contract. Do not navigate using placeholder data.
              </ThemedText>
              <ActionButton label="I have arrived" icon="check" onPress={() => setDeliveryStep(2)} />
            </View>
          ) : null}

          {deliveryStep === 2 ? (
            <View style={styles.stepContent}>
              <View style={[styles.stepIcon, { backgroundColor: theme.primarySoft }]}>
                <AppIcon name="inventory" color={theme.primary} size={28} />
              </View>
              <ThemedText style={styles.stepTitle}>Verify pickup</ThemedText>
              <ThemedText type="small" themeColor="textSecondary" style={styles.centerText}>
                Enter the merchant-issued proof code. Validation is performed by the dispatch service.
              </ThemedText>
              <TextField
                label="Pickup verification code"
                placeholder="Enter merchant code"
                value={pickupProof}
                onChangeText={setPickupProof}
                keyboardType="number-pad"
                maxLength={8}
                autoComplete="one-time-code"
              />
              <ActionButton
                label="Confirm pickup"
                icon="check"
                loading={verifyingProof}
                onPress={() => void submitProof('pickup', pickupProof)}
              />
            </View>
          ) : null}

          {deliveryStep === 3 ? (
            <View style={styles.stepContent}>
              <View style={[styles.stepIcon, { backgroundColor: theme.primarySoft }]}>
                <AppIcon name="truck" color={theme.primary} size={28} />
              </View>
              <ThemedText style={styles.stepTitle}>Travel to the customer</ThemedText>
              <ThemedText type="small" themeColor="textSecondary" style={styles.centerText}>
                Customer address remains protected until the connected order-detail contract returns an authorized destination.
              </ThemedText>
              <ActionButton label="I have arrived" icon="location" onPress={() => setDeliveryStep(4)} />
            </View>
          ) : null}

          {deliveryStep === 4 ? (
            <View style={styles.stepContent}>
              <View style={[styles.stepIcon, { backgroundColor: theme.successSoft }]}>
                <AppIcon name="check" color={theme.success} size={28} />
              </View>
              <ThemedText style={styles.stepTitle}>Verify handover</ThemedText>
              <ThemedText type="small" themeColor="textSecondary" style={styles.centerText}>
                Enter the customer-issued proof code. The server decides whether delivery can be completed.
              </ThemedText>
              <TextField
                label="Handover verification code"
                placeholder="Enter customer code"
                value={handoverProof}
                onChangeText={setHandoverProof}
                keyboardType="number-pad"
                maxLength={8}
                autoComplete="one-time-code"
              />
              <ActionButton
                label="Complete delivery"
                icon="check"
                loading={verifyingProof}
                onPress={() => void submitProof('deliver', handoverProof)}
              />
            </View>
          ) : null}
        </AppCard>
      ) : (
        <AppCard style={styles.availabilityCard}>
          <View style={styles.cardHeader}>
            <View style={styles.statusTitleRow}>
              <View style={[styles.statusDot, { backgroundColor: isOnline ? theme.success : theme.textSecondary }]} />
              <ThemedText style={styles.cardTitle}>{isOnline ? 'Online and waiting' : 'Offline'}</ThemedText>
            </View>
            <StatusBadge label={isOnline ? 'ONLINE' : 'OFFLINE'} tone={isOnline ? 'success' : 'neutral'} />
          </View>
          <ThemedText type="small" themeColor="textSecondary">
            {isOnline
              ? 'Dispatch offers are checked every four seconds. Keep this workspace active while accepting jobs.'
              : 'Go online only when location access is available and you are ready to accept delivery work.'}
          </ThemedText>
          <ActionButton
            label={isOnline ? 'Go offline' : 'Go online'}
            variant={isOnline ? 'destructive' : 'primary'}
            icon={isOnline ? 'xmark' : 'location'}
            loading={loading}
            onPress={() => void toggleOnline()}
          />
        </AppCard>
      )}

      <Modal
        visible={activeOffer !== null}
        transparent
        animationType="fade"
        onRequestClose={() => setActiveOffer(null)}
      >
        <View style={styles.modalBackdrop}>
          <View style={[styles.offerCard, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]} accessibilityViewIsModal>
            <View style={styles.cardHeader}>
              <View style={styles.flex}>
                <ThemedText style={styles.offerTitle}>Incoming dispatch offer</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  Order #{activeOffer ? shortOrderId(activeOffer.orderId) : ''}
                </ThemedText>
              </View>
              <StatusBadge label={`${offerCountdown}s`} tone={offerCountdown <= 10 ? 'danger' : 'warning'} />
            </View>

            <View style={[styles.progressTrack, { backgroundColor: theme.muted }]}>
              <View
                style={[
                  styles.progressFill,
                  { backgroundColor: offerCountdown <= 10 ? theme.danger : theme.primary, width: `${(offerCountdown / 30) * 100}%` },
                ]}
              />
            </View>

            <ThemedText type="small" themeColor="textSecondary">
              Pickup and customer location details must come from the authorized order-detail contract after acceptance. Earnings are recorded after server-confirmed delivery.
            </ThemedText>

            <View style={styles.offerActions}>
              <ActionButton
                label="Decline"
                variant="ghost"
                disabled={loading}
                onPress={() => void respondToOffer('REJECTED')}
                style={styles.offerAction}
              />
              <ActionButton
                label="Accept job"
                icon="check"
                loading={loading}
                onPress={() => void respondToOffer('ACCEPTED')}
                style={styles.offerAction}
              />
            </View>

            <Pressable
              onPress={() => setActiveOffer(null)}
              accessibilityRole="button"
              accessibilityLabel="Close dispatch offer"
              style={({ pressed }) => [styles.modalClose, pressed && styles.pressed]}
            >
              <AppIcon name="xmark" color={theme.textSecondary} size={20} />
            </Pressable>
          </View>
        </View>
      </Modal>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  onboardingCard: {
    minHeight: 104,
    padding: spacing.x4,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.x3,
  },
  roundIcon: { width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
  cardTitle: { ...typography.title, fontSize: 18, lineHeight: 24 },
  deliveryCard: { padding: spacing.x4, gap: spacing.x4 },
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
  modalBackdrop: { flex: 1, backgroundColor: 'rgba(11,28,48,0.58)', alignItems: 'center', justifyContent: 'center', padding: spacing.x4 },
  offerCard: { width: '100%', maxWidth: 520, borderWidth: StyleSheet.hairlineWidth, borderRadius: radii.feature, padding: spacing.x6, gap: spacing.x4 },
  offerTitle: { ...typography.title },
  offerActions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  offerAction: { flexGrow: 1, flexBasis: 160 },
  modalClose: { position: 'absolute', top: -touchTarget / 2, right: 0, width: touchTarget, height: touchTarget, alignItems: 'center', justifyContent: 'center' },
  pressed: { opacity: 0.82 },
});
