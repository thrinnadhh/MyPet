import React, { useState } from 'react';
import { Alert, Linking, Modal, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

import { AppIcon } from '@/components/app-icon';
import { AppBar, FilterChip, PrimaryAction, StateView, StatusBadge } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { useAuthIntent } from '@/context/AuthIntentContext';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useAppointments } from '@/hooks/use-appointments';
import { useTheme } from '@/hooks/use-theme';
import { useTranslation } from '@/i18n';
import type { CustomerAppointmentRecord } from '@/services/customer-history';

export default function AppointmentsScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const router = useRouter();
  const { requireAuth } = useAuthIntent();

  const {
    user,
    session,
    filteredAppointments,
    state,
    activeTab,
    setActiveTab,
    searchQuery,
    setSearchQuery,
    actionLoading,
    reload,
    cancel,
    submitReview,
  } = useAppointments();

  const [selectedApptForReview, setSelectedApptForReview] = useState<CustomerAppointmentRecord | null>(null);
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');

  const [selectedApptForCancel, setSelectedApptForCancel] = useState<CustomerAppointmentRecord | null>(null);
  const [cancelReason, setCancelReason] = useState('');

  if (!user || !session) {
    return (
      <ScreenShell scroll={false} header={<AppBar title={t('routes.appointments')} subtitle="Manage your pet visits" />}>
        <StateView
          kind="unauthenticated"
          title={t('states.unauthenticated')}
          message="Sign in to view your appointment history."
          actionLabel={t('common.signIn')}
          onAction={() => void requireAuth({ action: 'ORDER_HISTORY', returnTo: '/appointments' })}
        />
      </ScreenShell>
    );
  }

  const handleCancelSubmit = async () => {
    if (!selectedApptForCancel) return;
    try {
      await cancel(selectedApptForCancel.id, cancelReason || 'Cancelled by customer');
      setSelectedApptForCancel(null);
      setCancelReason('');
      Alert.alert(t('common.success'), 'Appointment cancelled successfully.');
    } catch (err: any) {
      Alert.alert(t('common.error'), err.message || 'Could not cancel appointment.');
    }
  };

  const handleReviewSubmit = async () => {
    if (!selectedApptForReview) return;
    try {
      const result = await submitReview({
        providerId: selectedApptForReview.providerId,
        targetId: selectedApptForReview.id,
        rating,
        comment,
      });

      setSelectedApptForReview(null);
      setComment('');
      if (result === 'duplicate') {
        Alert.alert(t('explore.alreadyReviewed'), t('explore.alreadyReviewedBody'));
      } else {
        Alert.alert(t('explore.thankYou'), t('explore.reviewSubmitted'));
      }
    } catch (err: any) {
      Alert.alert(t('common.error'), err.message || 'Could not submit review.');
    }
  };

  const openDirections = (address?: string) => {
    if (!address) return;
    const url = `https://maps.google.com/?q=${encodeURIComponent(address)}`;
    Linking.openURL(url).catch(() => null);
  };

  const callProvider = (phone?: string) => {
    if (!phone) return;
    Linking.openURL(`tel:${phone}`).catch(() => null);
  };

  return (
    <ScreenShell
      header={<AppBar title={t('routes.appointments')} subtitle="Upcoming visits, clinic details, and history" />}
      testID="appointments-screen"
    >
      {/* 4-Tab / Segmented Filter Navigation Bar */}
      <View style={styles.tabsContainer}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.tabsScroll}>
          <FilterChip
            label="Upcoming"
            selected={activeTab === 'upcoming'}
            onPress={() => setActiveTab('upcoming')}
          />
          <FilterChip
            label="Past"
            selected={activeTab === 'past'}
            onPress={() => setActiveTab('past')}
          />
          <FilterChip
            label="Cancelled"
            selected={activeTab === 'cancelled'}
            onPress={() => setActiveTab('cancelled')}
          />
        </ScrollView>

        {/* Search Field */}
        <View style={[styles.searchBox, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <AppIcon name="search" size={16} color={theme.textSecondary} />
          <TextInput
            value={searchQuery}
            onChangeText={setSearchQuery}
            placeholder="Search by clinic, service, or pet..."
            placeholderTextColor={theme.textSecondary}
            style={[styles.searchInput, { color: theme.text }]}
          />
          {searchQuery ? (
            <Pressable onPress={() => setSearchQuery('')}>
              <ThemedText style={{ color: theme.textSecondary, fontWeight: '700' }}>✕</ThemedText>
            </Pressable>
          ) : null}
        </View>
      </View>

      {/* Screen States */}
      {state === 'loading' || state === 'idle' ? (
        <StateView kind="loading" title={t('states.loading')} message={t('states.loadingMessage')} />
      ) : null}
      {state === 'offline' ? (
        <StateView kind="offline" title={t('states.offline')} message={t('states.offlineMessage')} actionLabel={t('states.retry')} onAction={() => void reload()} />
      ) : null}
      {state === 'error' ? (
        <StateView kind="error" title={t('states.error')} message="Could not load appointments." actionLabel={t('states.retry')} onAction={() => void reload()} />
      ) : null}
      {state === 'ready' && filteredAppointments.length === 0 ? (
        <StateView kind="empty" title="No appointments" message="Your scheduled visits will appear here." />
      ) : null}

      {/* Appointment Cards List */}
      {state === 'ready' && filteredAppointments.length > 0 ? (
        <View style={styles.list}>
          {filteredAppointments.map((appt) => {
            const isUpcoming = ['SLOT_HELD', 'CONFIRMED'].includes(appt.status);
            const isCompleted = appt.status === 'COMPLETED';

            return (
              <View
                key={appt.id}
                style={[
                  styles.card,
                  shadows.card,
                  { backgroundColor: theme.backgroundElement, borderColor: theme.border },
                ]}
              >
                {/* Header row */}
                <View style={styles.cardHeader}>
                  <View style={styles.flex}>
                    <ThemedText style={styles.providerName}>{appt.providerName}</ThemedText>
                    <ThemedText type="small" style={{ color: theme.primary, fontWeight: '700' }}>
                      {appt.serviceName} · {appt.petName}
                    </ThemedText>
                  </View>
                  <StatusBadge
                    label={appt.status}
                    tone={
                      appt.status === 'CONFIRMED' || appt.status === 'COMPLETED'
                        ? 'success'
                        : appt.status === 'CANCELLED' || appt.status === 'EXPIRED'
                        ? 'error'
                        : 'warning'
                    }
                  />
                </View>

                {/* Slot time & Address */}
                <View style={styles.infoRow}>
                  <AppIcon name="calendar" size={14} color={theme.textSecondary} />
                  <ThemedText type="small" themeColor="textSecondary">
                    {new Date(appt.slotStartsAt).toLocaleString()}
                  </ThemedText>
                </View>

                {appt.address ? (
                  <View style={styles.infoRow}>
                    <AppIcon name="location" size={14} color={theme.textSecondary} />
                    <ThemedText type="small" themeColor="textSecondary" numberOfLines={1}>
                      {appt.address}
                    </ThemedText>
                  </View>
                ) : null}

                {/* Action Buttons */}
                <View style={styles.actionRow}>
                  <Pressable
                    style={[styles.outlineBtn, { borderColor: theme.border }]}
                    onPress={() => router.push(`/appointments/${appt.id}` as any)}
                  >
                    <ThemedText type="small" style={{ color: theme.text, fontWeight: '700' }}>
                      View Details
                    </ThemedText>
                  </Pressable>

                  {appt.address ? (
                    <Pressable
                      style={[styles.iconBtn, { backgroundColor: theme.primarySoft }]}
                      onPress={() => openDirections(appt.address)}
                    >
                      <AppIcon name="location" size={14} color={theme.primary} />
                    </Pressable>
                  ) : null}

                  {appt.providerPhone ? (
                    <Pressable
                      style={[styles.iconBtn, { backgroundColor: theme.primarySoft }]}
                      onPress={() => callProvider(appt.providerPhone)}
                    >
                      <AppIcon name="sparkle" size={14} color={theme.primary} />
                    </Pressable>
                  ) : null}

                  {isUpcoming ? (
                    <Pressable
                      style={[styles.outlineBtn, { borderColor: theme.danger }]}
                      onPress={() => setSelectedApptForCancel(appt)}
                    >
                      <ThemedText type="small" style={{ color: theme.danger, fontWeight: '700' }}>
                        Cancel
                      </ThemedText>
                    </Pressable>
                  ) : null}

                  {isCompleted && !appt.hasReview ? (
                    <Pressable
                      style={[styles.solidBtn, { backgroundColor: theme.primarySoft }]}
                      onPress={() => setSelectedApptForReview(appt)}
                    >
                      <AppIcon name="star" size={14} color={theme.accent} />
                      <ThemedText type="small" style={{ color: theme.primary, fontWeight: '700' }}>
                        Review
                      </ThemedText>
                    </Pressable>
                  ) : null}
                </View>
              </View>
            );
          })}
        </View>
      ) : null}

      {/* Review Modal */}
      <Modal visible={Boolean(selectedApptForReview)} transparent animationType="slide">
        <View style={styles.modalOverlay}>
          <View style={[styles.modalBox, { backgroundColor: theme.backgroundElement }]}>
            <ThemedText type="title">Leave a Review</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              Rate your visit to {selectedApptForReview?.providerName}
            </ThemedText>

            {/* Rating Stars */}
            <View style={styles.starRow}>
              {[1, 2, 3, 4, 5].map((star) => (
                <Pressable key={star} onPress={() => setRating(star)}>
                  <AppIcon name="star" size={28} color={star <= rating ? theme.accent : theme.border} />
                </Pressable>
              ))}
            </View>

            <TextInput
              value={comment}
              onChangeText={setComment}
              placeholder="Share your experience (optional)..."
              placeholderTextColor={theme.textSecondary}
              style={[styles.reasonInput, { color: theme.text, borderColor: theme.border }]}
              multiline
            />

            <View style={styles.modalActions}>
              <Pressable style={styles.cancelModalBtn} onPress={() => setSelectedApptForReview(null)}>
                <ThemedText style={{ color: theme.textSecondary }}>Cancel</ThemedText>
              </Pressable>
              <PrimaryAction
                label="Submit Review"
                onPress={() => void handleReviewSubmit()}
                loading={actionLoading}
              />
            </View>
          </View>
        </View>
      </Modal>

      {/* Cancel Modal */}
      <Modal visible={Boolean(selectedApptForCancel)} transparent animationType="slide">
        <View style={styles.modalOverlay}>
          <View style={[styles.modalBox, { backgroundColor: theme.backgroundElement }]}>
            <ThemedText type="title">Cancel Appointment</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              Cancel visit for {selectedApptForCancel?.petName} with {selectedApptForCancel?.providerName}?
            </ThemedText>

            <TextInput
              value={cancelReason}
              onChangeText={setCancelReason}
              placeholder="Reason for cancellation..."
              placeholderTextColor={theme.textSecondary}
              style={[styles.reasonInput, { color: theme.text, borderColor: theme.border }]}
              multiline
            />

            <View style={styles.modalActions}>
              <Pressable style={styles.cancelModalBtn} onPress={() => setSelectedApptForCancel(null)}>
                <ThemedText style={{ color: theme.textSecondary }}>Keep Appointment</ThemedText>
              </Pressable>
              <PrimaryAction
                label="Confirm Cancel"
                onPress={() => void handleCancelSubmit()}
                loading={actionLoading}
              />
            </View>
          </View>
        </View>
      </Modal>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  tabsContainer: { paddingHorizontal: spacing.x4, gap: spacing.x3, marginBottom: spacing.x3 },
  tabsScroll: { flexDirection: 'row', gap: spacing.x2 },
  searchBox: {
    height: 40,
    borderWidth: 1,
    borderRadius: radii.compact,
    paddingHorizontal: spacing.x3,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.x2,
  },
  searchInput: { flex: 1, ...typography.body, paddingVertical: 0 },
  list: { paddingHorizontal: spacing.x4, gap: spacing.x3, paddingBottom: spacing.x6 },
  card: {
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: radii.card,
    padding: spacing.x4,
    gap: spacing.x2,
  },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  providerName: { ...typography.label },
  infoRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.x2 },
  actionRow: { flexDirection: 'row', gap: spacing.x2, justifyContent: 'flex-end', marginTop: spacing.x2 },
  outlineBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.x3,
    paddingVertical: spacing.x2,
    borderWidth: 1,
    borderRadius: radii.compact,
  },
  iconBtn: {
    width: 36,
    height: 36,
    borderRadius: radii.compact,
    alignItems: 'center',
    justifyContent: 'center',
  },
  solidBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.x1,
    paddingHorizontal: spacing.x3,
    paddingVertical: spacing.x2,
    borderRadius: radii.compact,
  },
  starRow: { flexDirection: 'row', gap: spacing.x2, justifyContent: 'center', marginVertical: spacing.x2 },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', padding: spacing.x4 },
  modalBox: { borderRadius: radii.card, padding: spacing.x6, gap: spacing.x3 },
  reasonInput: { borderWidth: 1, borderRadius: radii.compact, padding: spacing.x3, height: 72, textAlignVertical: 'top' },
  modalActions: { flexDirection: 'row', justifyContent: 'flex-end', alignItems: 'center', gap: spacing.x3 },
  cancelModalBtn: { padding: spacing.x3 },
});
