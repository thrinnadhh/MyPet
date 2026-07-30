import React from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { FilterChip } from '@/components/foundation/primitives';
import { ThemedText } from '@/components/themed-text';
import { PrimaryButton } from '@/components/ui/primary-button';
import { StatusBadge } from '@/components/ui/status-badge';
import { radii, shadows, spacing, typography } from '@/design/tokens';

import { useTheme } from '@/hooks/use-theme';
import type { BookingState } from '@/hooks/useAppointmentBooking';

interface AppointmentBookingModalProps {
  visible: boolean;
  booking: BookingState;
  onClose: () => void;
  onSelectDate: (date: string) => void;
  onSelectSlot: (slot: string) => void;
  onSelectPet: (id: string, name: string) => void;
  onSubmit: () => Promise<boolean>;
}

const PET_OPTIONS = [
  { id: 'pet-bruno', name: 'Bruno (Golden Retriever)' },
  { id: 'pet-luna', name: 'Luna (Persian Cat)' },
];

const TIME_SLOTS = [
  '09:30 AM', '10:30 AM', '11:30 AM',
  '02:00 PM', '03:30 PM', '04:30 PM',
  '06:00 PM', '07:00 PM',
];

export function AppointmentBookingModal({
  visible,
  booking,
  onClose,
  onSelectDate,
  onSelectSlot,
  onSelectPet,
  onSubmit,
}: AppointmentBookingModalProps) {
  const theme = useTheme();

  const isVet = booking.providerType === 'VET_HOSPITAL';
  const actionLabel = isVet ? 'Confirm OPD Appointment' : 'Confirm Spa Session';

  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={[styles.modalCard, shadows.raised, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <View style={styles.header}>
            <View>
              <ThemedText style={[styles.title, { color: theme.text }]}>Book {booking.serviceName}</ThemedText>
              <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>{booking.providerName}</ThemedText>
            </View>
            <Pressable onPress={onClose} style={styles.closeBtn}>
              <AppIcon name="warning" color={theme.textSecondary} size={20} />
            </Pressable>
          </View>

          {booking.status === 'confirmed' ? (
            <View style={styles.confirmedBox}>
              <AppIcon name="sparkle" color={theme.success} size={48} />
              <ThemedText style={[styles.confirmTitle, { color: theme.success }]}>
                {isVet ? 'Appointment Confirmed!' : 'Spa Booking Reserved!'}
              </ThemedText>
              <ThemedText style={{ color: theme.text, fontSize: 14, textAlign: 'center' }}>
                Booking ID: <ThemedText style={{ fontWeight: '800' }}>{booking.bookingId}</ThemedText>
              </ThemedText>
              <ThemedText style={{ color: theme.textSecondary, fontSize: 13, textAlign: 'center' }}>
                Scheduled for {booking.selectedPetName} on {booking.selectedDate} at {booking.selectedTimeSlot}.
              </ThemedText>
              <PrimaryButton label="Done" onPress={onClose} />
            </View>
          ) : (
            <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
              {/* Pet Selection */}
              <View style={styles.section}>
                <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>Select Pet</ThemedText>
                <View style={styles.chipRow}>
                  {PET_OPTIONS.map((pet) => (
                    <FilterChip
                      key={pet.id}
                      label={pet.name}
                      selected={booking.selectedPetId === pet.id}
                      onPress={() => onSelectPet(pet.id, pet.name)}
                    />
                  ))}
                </View>
              </View>

              {/* Time Slots Grid */}
              <View style={styles.section}>
                <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>Available Time Slots</ThemedText>
                <View style={styles.slotGrid}>
                  {TIME_SLOTS.map((slot) => {
                    const isSelected = booking.selectedTimeSlot === slot;
                    return (
                      <Pressable
                        key={slot}
                        onPress={() => onSelectSlot(slot)}
                        style={[
                          styles.slotChip,
                          {
                            backgroundColor: isSelected ? theme.primary : theme.muted,
                            borderColor: isSelected ? theme.primary : theme.border,
                          },
                        ]}
                      >
                        <ThemedText style={{ color: isSelected ? '#FFFFFF' : theme.text, fontWeight: '700', fontSize: 13 }}>
                          {slot}
                        </ThemedText>
                      </Pressable>
                    );
                  })}
                </View>
              </View>

              {/* Price Summary */}
              <View style={[styles.summaryBox, { backgroundColor: theme.muted }]}>
                <View style={styles.summaryRow}>
                  <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>Service Fee</ThemedText>
                  <ThemedText style={{ fontSize: 15, fontWeight: '800', color: theme.primary }}>₹{booking.serviceFee}</ThemedText>
                </View>
                <StatusBadge label="Instant Slot Reservation" color={theme.success} />
              </View>

              <PrimaryButton
                label={booking.status === 'submitting' ? 'Reserving Slot...' : actionLabel}
                disabled={booking.status === 'submitting'}
                onPress={() => void onSubmit()}
              />
            </ScrollView>
          )}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'flex-end' },
  modalCard: { borderTopLeftRadius: radii.card, borderTopRightRadius: radii.card, padding: spacing.x4, maxHeight: '85%', borderTopWidth: 1, gap: spacing.x3 },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  title: { ...typography.headline, fontSize: 18, fontWeight: '800' },
  closeBtn: { padding: 4 },
  scrollContent: { gap: spacing.x4, paddingBottom: spacing.x4 },
  section: { gap: spacing.x2 },
  sectionTitle: { ...typography.headline, fontSize: 15, fontWeight: '700' },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  slotGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  slotChip: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: radii.compact, borderWidth: 1 },
  summaryBox: { padding: spacing.x3, borderRadius: radii.compact, gap: spacing.x2 },
  summaryRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  confirmedBox: { padding: spacing.x6, alignItems: 'center', gap: spacing.x3 },
  confirmTitle: { ...typography.headline, fontSize: 20, fontWeight: '800' },
});
