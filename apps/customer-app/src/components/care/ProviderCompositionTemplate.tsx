import { useRouter } from 'expo-router';
import React from 'react';
import { Image, Linking, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { AppointmentBookingModal } from '@/components/care/AppointmentBookingModal';
import { ThemedText } from '@/components/themed-text';
import { PrimaryButton } from '@/components/ui/primary-button';
import { ScreenHeader } from '@/components/ui/screen-header';
import { StatusBadge } from '@/components/ui/status-badge';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { useAppointmentBooking } from '@/hooks/useAppointmentBooking';

export interface ProviderCompositionData {
  id: string;
  name: string;
  type: 'VET_HOSPITAL' | 'GROOMING_CENTER';
  tagline: string;
  address: string;
  phone: string;
  rating: string;
  reviewCount: number;
  heroImageUrl: string;
  distanceKm: number;
  operatingHours: string;
  emergencyCare?: boolean;
  services: Array<{ name: string; desc: string; fee: number; duration?: string }>;
  staffRoster: Array<{ name: string; role: string; experience: string; avatarUrl?: string }>;
  facilities: string[];
}

export function ProviderCompositionTemplate({ provider }: { provider: ProviderCompositionData }) {
  const router = useRouter();
  const theme = useTheme();
  const {
    modalVisible,
    booking,
    openBookingModal,
    closeBookingModal,
    selectDate,
    selectSlot,
    selectPet,
    submitBooking,
  } = useAppointmentBooking();

  const isVet = provider.type === 'VET_HOSPITAL';

  const handleCall = () => {
    void Linking.openURL(`tel:${provider.phone}`);
  };

  const handleDirections = () => {
    void Linking.openURL(`https://maps.google.com/?q=${encodeURIComponent(provider.address)}`);
  };

  const handleMessage = () => {
    router.push(`/chat?recipient=${encodeURIComponent(provider.name)}` as never);
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <ScreenHeader title={provider.name} subtitle={provider.tagline} />

      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        {/* Hero Card */}
        <View style={styles.heroCard}>
          <Image source={{ uri: provider.heroImageUrl }} style={styles.heroImage} resizeMode="cover" />
          <View style={styles.heroOverlay}>
            <StatusBadge label={provider.rating} color={theme.warning} />
            <StatusBadge label={`${provider.distanceKm} km away`} color={theme.primary} />
            {provider.emergencyCare && (
              <StatusBadge label="24/7 ICU" color={theme.danger} />
            )}
          </View>
        </View>

        {/* Overview Header */}
        <View style={styles.section}>
          <ThemedText style={[styles.title, { color: theme.text }]}>{provider.name}</ThemedText>
          <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>📍 {provider.address}</ThemedText>
          <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>🕒 Hours: {provider.operatingHours}</ThemedText>
        </View>

        {/* Quick Action Bar (Call, Directions, Chat) */}
        <View style={styles.actionRow}>
          <Pressable onPress={handleCall} style={[styles.actionBtn, { backgroundColor: theme.primarySoft }]}>
            <AppIcon name="paw" color={theme.primary} size={18} />
            <ThemedText style={{ color: theme.primary, fontWeight: '700', fontSize: 13 }}>Call</ThemedText>
          </Pressable>

          <Pressable onPress={handleDirections} style={[styles.actionBtn, { backgroundColor: theme.primarySoft }]}>
            <AppIcon name="location" color={theme.primary} size={18} />
            <ThemedText style={{ color: theme.primary, fontWeight: '700', fontSize: 13 }}>Directions</ThemedText>
          </Pressable>

          <Pressable onPress={handleMessage} style={[styles.actionBtn, { backgroundColor: theme.primarySoft }]}>
            <AppIcon name="sparkle" color={theme.primary} size={18} />
            <ThemedText style={{ color: theme.primary, fontWeight: '700', fontSize: 13 }}>Message</ThemedText>
          </Pressable>
        </View>

        {/* Services & Offerings */}
        <View style={styles.section}>
          <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>
            {isVet ? 'Medical Services & OPD Fees' : 'Spa & Grooming Packages'}
          </ThemedText>
          <View style={styles.cardList}>
            {provider.services.map((srv, idx) => (
              <View
                key={idx}
                style={[styles.serviceCard, shadows.raised, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}
              >
                <View style={{ flex: 1, gap: 4 }}>
                  <ThemedText style={{ fontWeight: '700', fontSize: 15, color: theme.text }}>{srv.name}</ThemedText>
                  <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>{srv.desc}</ThemedText>
                  {srv.duration && (
                    <ThemedText style={{ fontSize: 12, color: theme.primary }}>⏱️ {srv.duration}</ThemedText>
                  )}
                </View>
                <View style={{ alignItems: 'flex-end', gap: 6 }}>
                  <ThemedText style={{ fontWeight: '900', fontSize: 16, color: theme.primary }}>₹{srv.fee}</ThemedText>
                  <Pressable
                    onPress={() =>
                      openBookingModal({
                        providerId: provider.id,
                        providerName: provider.name,
                        providerType: provider.type,
                        serviceName: srv.name,
                        serviceFee: srv.fee,
                      })
                    }
                    style={[styles.bookBtn, { backgroundColor: theme.primary }]}
                  >
                    <ThemedText style={{ color: '#FFFFFF', fontWeight: '800', fontSize: 12 }}>Book</ThemedText>
                  </Pressable>
                </View>
              </View>
            ))}
          </View>
        </View>

        {/* Facilities / Amenities */}
        <View style={styles.section}>
          <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>Facilities & Highlights</ThemedText>
          <View style={styles.chipGrid}>
            {provider.facilities.map((fac, idx) => (
              <StatusBadge key={idx} label={`✓ ${fac}`} color={theme.primary} />
            ))}
          </View>
        </View>

        {/* Doctors / Groomers Roster */}
        <View style={styles.section}>
          <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>
            {isVet ? 'Veterinary Specialists' : 'Certified Groomers'}
          </ThemedText>
          <View style={styles.rosterList}>
            {provider.staffRoster.map((staff, idx) => (
              <View
                key={idx}
                style={[styles.staffCard, shadows.raised, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}
              >
                <AppIcon name="paw" color={theme.primary} size={24} />
                <View style={{ flex: 1 }}>
                  <ThemedText style={{ fontWeight: '700', fontSize: 14, color: theme.text }}>{staff.name}</ThemedText>
                  <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>{staff.role} • {staff.experience}</ThemedText>
                </View>
              </View>
            ))}
          </View>
        </View>
      </ScrollView>

      {/* Sticky Booking CTA */}
      <View style={[styles.stickyFooter, shadows.raised, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
        <PrimaryButton
          label={isVet ? 'Book OPD Appointment Slot' : 'Book Grooming Session'}
          onPress={() =>
            openBookingModal({
              providerId: provider.id,
              providerName: provider.name,
              providerType: provider.type,
            })
          }
        />
      </View>

      <AppointmentBookingModal
        visible={modalVisible}
        booking={booking}
        onClose={closeBookingModal}
        onSelectDate={selectDate}
        onSelectSlot={selectSlot}
        onSelectPet={selectPet}
        onSubmit={submitBooking}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: spacing.x3 },
  scrollContent: { paddingBottom: 100, gap: spacing.x4 },
  heroCard: { width: '100%', height: 200, borderRadius: radii.card, overflow: 'hidden', position: 'relative' },
  heroImage: { width: '100%', height: '100%' },
  heroOverlay: { position: 'absolute', top: 12, left: 12, right: 12, flexDirection: 'row', gap: spacing.x2, flexWrap: 'wrap' },
  section: { gap: spacing.x2 },
  title: { ...typography.headline, fontSize: 20, fontWeight: '800' },
  sectionTitle: { ...typography.headline, fontSize: 16, fontWeight: '700' },
  actionRow: { flexDirection: 'row', gap: spacing.x3 },
  actionBtn: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, paddingVertical: 10, borderRadius: radii.compact },
  cardList: { gap: spacing.x3 },
  serviceCard: { flexDirection: 'row', alignItems: 'center', padding: spacing.x3, borderRadius: radii.card, borderWidth: 1, gap: spacing.x3 },
  bookBtn: { paddingHorizontal: 16, paddingVertical: 6, borderRadius: radii.compact },
  chipGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  rosterList: { gap: spacing.x2 },
  staffCard: { flexDirection: 'row', alignItems: 'center', padding: spacing.x3, borderRadius: radii.compact, borderWidth: 1, gap: spacing.x3 },
  stickyFooter: { position: 'absolute', bottom: 0, left: 0, right: 0, borderTopWidth: 1, padding: spacing.x4 },
});
