import { useRouter } from 'expo-router';
import React, { useState } from 'react';
import { Alert, Image, Linking, Modal, Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { ThemedText } from '@/components/themed-text';
import { PrimaryButton } from '@/components/ui/primary-button';
import { ScreenHeader } from '@/components/ui/screen-header';
import { StatusBadge } from '@/components/ui/status-badge';

import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useAppointments } from '@/hooks/useAppointments';
import { useTheme } from '@/hooks/use-theme';
import { type Appointment, type AppointmentStatus, type AppointmentTab } from '@/services/appointments-data';

export default function AppointmentsScreen() {
  const theme = useTheme();
  const router = useRouter();

  const { tab, setTab, appointments, loading, refreshing, onRefresh, cancelAppointment, rescheduleAppointment } = useAppointments('upcoming');

  const [rescheduleApt, setRescheduleApt] = useState<Appointment | null>(null);
  const [selectedDate, setSelectedDate] = useState('Aug 2');
  const [selectedSlot, setSelectedSlot] = useState('11:00 AM - 11:30 AM');

  const getStatusColor = (status: AppointmentStatus) => {
    switch (status) {
      case 'UPCOMING':
        return theme.primary;
      case 'COMPLETED':
        return theme.success;
      case 'RESCHEDULED':
        return theme.warning;
      case 'CANCELLED':
        return theme.error;
      default:
        return theme.primary;
    }
  };

  const handleCancel = (apt: Appointment) => {
    Alert.alert('Cancel Appointment', `Are you sure you want to cancel appointment ${apt.appointmentNumber} at ${apt.providerName}?`, [
      { text: 'No', style: 'cancel' },
      {
        text: 'Yes, Cancel',
        style: 'destructive',
        onPress: () => cancelAppointment(apt.id),
      },
    ]);
  };

  const handleCallClinic = (phone: string) => {
    void Linking.openURL(`tel:${phone}`);
  };

  const handleConfirmReschedule = () => {
    if (!rescheduleApt) return;
    rescheduleAppointment(rescheduleApt.id, selectedDate, selectedSlot);
    setRescheduleApt(null);
    Alert.alert('Appointment Rescheduled', `Your appointment has been moved to ${selectedDate} at ${selectedSlot}.`);
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <ScreenHeader title="My Appointments" subtitle="Vet consultations & grooming slots" />

      {/* Tab Switcher */}
      <View style={styles.tabContainer}>
        {(['upcoming', 'past', 'cancelled'] as AppointmentTab[]).map((t) => {
          const selected = tab === t;
          return (
            <Pressable
              key={t}
              onPress={() => setTab(t)}
              style={[
                styles.tabChip,
                {
                  backgroundColor: selected ? theme.primary : theme.backgroundElement,
                  borderColor: theme.border,
                },
              ]}
            >
              <ThemedText style={[styles.tabLabel, { color: selected ? '#FFFFFF' : theme.text }]}>
                {t === 'upcoming' ? 'Upcoming' : t === 'past' ? 'Past Visits' : 'Cancelled'}
              </ThemedText>
            </Pressable>
          );
        })}
      </View>

      {/* Appointments List */}
      <ScrollView
        contentContainerStyle={styles.listContent}
        showsVerticalScrollIndicator={false}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void onRefresh()} tintColor={theme.primary} />}
      >
        {appointments.length === 0 && !loading && (
          <View style={styles.emptyState}>
            <AppIcon name="calendar" color={theme.textSecondary} size={48} />
            <ThemedText style={[styles.emptyTitle, { color: theme.text }]}>No Appointments Found</ThemedText>
            <ThemedText style={{ fontSize: 13, color: theme.textSecondary, textAlign: 'center' }}>
              {"You don't have any " + tab + " appointments."}
            </ThemedText>

            <PrimaryButton label="Book Vet Consultation" onPress={() => router.push('/hospital/city-pet-hospital' as never)} />
          </View>
        )}

        {appointments.map((apt) => (
          <View
            key={apt.id}
            style={[styles.aptCard, shadows.raised, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}
          >
            {/* Provider & Status Header */}
            <View style={styles.cardHeader}>
              <Image source={{ uri: apt.providerLogoUrl }} style={styles.providerLogo} resizeMode="cover" />
              <View style={{ flex: 1, gap: 2 }}>
                <ThemedText style={[styles.providerName, { color: theme.text }]}>{apt.providerName}</ThemedText>
                <ThemedText style={{ fontSize: 11, color: theme.primary, fontWeight: '700' }}>
                  {apt.providerType === 'VET_HOSPITAL' ? '🩺 Vet Hospital' : '✂️ Grooming Spa'}
                </ThemedText>
              </View>
              <StatusBadge label={apt.statusText} color={getStatusColor(apt.status)} />
            </View>

            {/* Service & Pet Details */}
            <View style={[styles.detailsBox, { backgroundColor: theme.background }]}>
              <View style={styles.detailRow}>
                <ThemedText style={{ fontSize: 13, fontWeight: '800', color: theme.text, flex: 1 }}>{apt.serviceName}</ThemedText>
                <View style={[styles.petPill, { backgroundColor: theme.primarySoft }]}>
                  <Image source={{ uri: apt.petAvatarUrl }} style={styles.petAvatar} resizeMode="cover" />
                  <ThemedText style={{ fontSize: 11, fontWeight: '700', color: theme.primary }}>{apt.petName}</ThemedText>
                </View>
              </View>
              <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>With {apt.doctorOrGroomerName}</ThemedText>
            </View>

            {/* Slot Box */}
            <View style={[styles.slotCard, { backgroundColor: theme.primarySoft, borderColor: theme.primary }]}>
              <AppIcon name="clock" color={theme.primary} size={18} />
              <View style={{ flex: 1 }}>
                <ThemedText style={{ fontSize: 13, fontWeight: '800', color: theme.primary }}>
                  {apt.date} • {apt.timeSlot}
                </ThemedText>
                <ThemedText style={{ fontSize: 11, color: theme.textSecondary }}>{apt.cancellationPolicy}</ThemedText>
              </View>
              <ThemedText style={{ fontSize: 14, fontWeight: '800', color: theme.primary }}>₹{apt.totalPrice}</ThemedText>
            </View>

            {/* Address */}
            <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>📍 {apt.clinicAddress}</ThemedText>

            {/* Actions Bar */}
            <View style={styles.actionsRow}>
              <Pressable
                onPress={() => handleCallClinic(apt.clinicPhone)}
                style={[styles.actionBtn, { backgroundColor: theme.success }]}
              >
                <AppIcon name="support" color="#FFFFFF" size={14} />
                <ThemedText style={{ fontSize: 12, fontWeight: '700', color: '#FFFFFF' }}>Call Clinic</ThemedText>
              </Pressable>


              {apt.canReschedule && (
                <Pressable
                  onPress={() => setRescheduleApt(apt)}
                  style={[styles.actionBtn, { backgroundColor: theme.primarySoft, borderColor: theme.primary, borderWidth: 1 }]}
                >
                  <ThemedText style={{ fontSize: 12, fontWeight: '700', color: theme.primary }}>Reschedule</ThemedText>
                </Pressable>
              )}

              {apt.canCancel && (
                <Pressable
                  onPress={() => handleCancel(apt)}
                  style={[styles.actionBtn, { backgroundColor: theme.errorSoft, borderColor: theme.error, borderWidth: 1 }]}
                >
                  <ThemedText style={{ fontSize: 12, fontWeight: '700', color: theme.error }}>Cancel</ThemedText>
                </Pressable>
              )}

              {apt.invoiceUrl && (
                <Pressable
                  onPress={() => Alert.alert('Download Invoice', `Invoice for ${apt.appointmentNumber}`)}
                  style={[styles.actionBtn, { backgroundColor: theme.muted, borderColor: theme.border, borderWidth: 1 }]}
                >
                  <ThemedText style={{ fontSize: 12, fontWeight: '700', color: theme.text }}>Invoice</ThemedText>
                </Pressable>
              )}
            </View>
          </View>
        ))}
      </ScrollView>

      {/* Reschedule Modal */}
      {rescheduleApt && (
        <Modal visible transparent animationType="slide">
          <View style={styles.modalOverlay}>
            <View style={[styles.modalContent, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText style={[styles.modalTitle, { color: theme.text }]}>Reschedule Appointment</ThemedText>
              <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>
                Moving slot for {rescheduleApt.petName} at {rescheduleApt.providerName}.
              </ThemedText>

              {/* Date Selectors */}
              <View style={{ gap: 6 }}>
                <ThemedText style={{ fontSize: 12, fontWeight: '700', color: theme.text }}>Select New Date:</ThemedText>
                <View style={{ flexDirection: 'row', gap: 8 }}>
                  {['Aug 2', 'Aug 3', 'Aug 4'].map((d) => (
                    <Pressable
                      key={d}
                      onPress={() => setSelectedDate(d)}
                      style={[
                        styles.modalChip,
                        {
                          backgroundColor: selectedDate === d ? theme.primary : theme.background,
                          borderColor: theme.border,
                        },
                      ]}
                    >
                      <ThemedText style={{ fontSize: 12, fontWeight: '700', color: selectedDate === d ? '#FFFFFF' : theme.text }}>
                        {d}
                      </ThemedText>
                    </Pressable>
                  ))}
                </View>
              </View>

              {/* Slot Selectors */}
              <View style={{ gap: 6 }}>
                <ThemedText style={{ fontSize: 12, fontWeight: '700', color: theme.text }}>Select Time Slot:</ThemedText>
                <View style={{ gap: 6 }}>
                  {['10:30 AM - 11:00 AM', '11:00 AM - 11:30 AM', '03:30 PM - 04:00 PM'].map((s) => (
                    <Pressable
                      key={s}
                      onPress={() => setSelectedSlot(s)}
                      style={[
                        styles.modalSlotRow,
                        {
                          backgroundColor: selectedSlot === s ? theme.primarySoft : theme.background,
                          borderColor: selectedSlot === s ? theme.primary : theme.border,
                        },
                      ]}
                    >
                      <ThemedText style={{ fontSize: 13, fontWeight: '700', color: selectedSlot === s ? theme.primary : theme.text }}>
                        {s}
                      </ThemedText>
                    </Pressable>
                  ))}
                </View>
              </View>

              <View style={{ flexDirection: 'row', gap: 12, marginTop: 12 }}>
                <Pressable
                  onPress={() => setRescheduleApt(null)}
                  style={[styles.modalCancelBtn, { borderColor: theme.border }]}
                >
                  <ThemedText style={{ fontSize: 13, fontWeight: '700', color: theme.text }}>Back</ThemedText>
                </Pressable>
                <View style={{ flex: 1 }}>
                  <PrimaryButton label="Confirm New Slot" onPress={handleConfirmReschedule} />
                </View>
              </View>
            </View>
          </View>
        </Modal>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: spacing.x3 },
  tabContainer: { flexDirection: 'row', gap: spacing.x2, marginBottom: spacing.x3 },
  tabChip: {
    flex: 1,
    height: 38,
    borderRadius: radii.compact,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tabLabel: { fontSize: 12, fontWeight: '700' },
  listContent: { gap: spacing.x4, paddingBottom: spacing.x6 },
  emptyState: { alignItems: 'center', justifyContent: 'center', padding: spacing.x6, gap: spacing.x3 },
  emptyTitle: { ...typography.headline, fontSize: 16, fontWeight: '700' },
  aptCard: { borderRadius: radii.card, borderWidth: 1, padding: spacing.x3, gap: spacing.x3 },
  cardHeader: { flexDirection: 'row', gap: spacing.x3, alignItems: 'center' },
  providerLogo: { width: 36, height: 36, borderRadius: radii.pill },
  providerName: { ...typography.headline, fontSize: 14, fontWeight: '700' },
  detailsBox: { padding: spacing.x3, borderRadius: radii.compact, gap: 4 },
  detailRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  petPill: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 8, paddingVertical: 4, borderRadius: radii.pill },
  petAvatar: { width: 18, height: 18, borderRadius: 9 },
  slotCard: { padding: spacing.x3, borderRadius: radii.compact, borderWidth: 1, flexDirection: 'row', alignItems: 'center', gap: spacing.x3 },
  actionsRow: { flexDirection: 'row', gap: 6, alignItems: 'center', flexWrap: 'wrap' },
  actionBtn: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 12, paddingVertical: 6, borderRadius: radii.compact },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'flex-end' },
  modalContent: { padding: spacing.x4, borderTopLeftRadius: radii.card, borderTopRightRadius: radii.card, gap: spacing.x3 },
  modalTitle: { ...typography.headline, fontSize: 18, fontWeight: '800' },
  modalChip: { paddingHorizontal: 14, paddingVertical: 8, borderRadius: radii.compact, borderWidth: 1 },
  modalSlotRow: { padding: 12, borderRadius: radii.compact, borderWidth: 1 },
  modalCancelBtn: { height: 44, paddingHorizontal: 16, borderRadius: radii.card, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
});
