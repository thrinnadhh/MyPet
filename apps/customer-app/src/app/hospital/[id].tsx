import { useLocalSearchParams, useRouter } from 'expo-router';
import React, { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { ThemedText } from '@/components/themed-text';
import { PrimaryButton } from '@/components/ui/primary-button';
import { ScreenHeader } from '@/components/ui/screen-header';
import { StatusBadge } from '@/components/ui/status-badge';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export default function HospitalProfileScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const theme = useTheme();

  const hospitalName = id === 'city-pet-hospital' ? 'City Pet Hospital Tirupati' : 'PetCare & Wellness Hospital';
  const [bookingSuccess, setBookingSuccess] = useState(false);

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <ScreenHeader title={hospitalName} subtitle="24/7 Veterinary Care • Tirupati" />

      <View style={[styles.heroCard, Shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
        <View style={styles.badgeRow}>
          <StatusBadge label="4.9 ★ (180+ reviews)" color={theme.warning} />
          <StatusBadge label="24/7 Emergency ICU" color={theme.danger} />
        </View>

        <ThemedText style={[styles.hospitalTitle, { color: theme.text }]}>{hospitalName}</ThemedText>
        <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>📍 AIR Bypass Road, Near Rama Chandra Nagar, Tirupati</ThemedText>
        <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>👨‍⚕️ Chief Vet: Dr. K. Srinivas, DVM (Surgeon, 12+ Yrs Exp)</ThemedText>
      </View>

      <View style={styles.section}>
        <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>Available Medical Services</ThemedText>
        {[
          { title: 'Emergency Trauma & ICU Care', fee: '₹799' },
          { title: 'General OPD Consultation', fee: '₹499' },
          { title: 'Pet Vaccination & Deworming', fee: '₹350' },
          { title: 'Blood Diagnostic & Ultrasound Lab', fee: '₹1,200' },
        ].map((srv, idx) => (
          <View key={idx} style={[styles.serviceRow, { borderColor: theme.border }]}>
            <View style={styles.flexOne}>
              <ThemedText style={[styles.serviceTitle, { color: theme.text }]}>{srv.title}</ThemedText>
              <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>Consultation Fee: {srv.fee}</ThemedText>
            </View>
            <AppIcon name="paw" color={theme.textSecondary} size={18} />
          </View>
        ))}
      </View>

      {bookingSuccess ? (
        <View style={[styles.successBanner, { backgroundColor: theme.primarySoft }]}>
          <AppIcon name="sparkle" color={theme.success} size={24} />
          <ThemedText style={{ color: theme.success, fontWeight: '700', fontSize: 13 }}>
            Appointment Confirmed! Confirmation SMS sent.
          </ThemedText>
        </View>
      ) : null}

      <View style={styles.actionFooter}>
        <PrimaryButton
          label={bookingSuccess ? 'Book Another Slot' : 'Book OPD Appointment Slot'}
          onPress={() => setBookingSuccess(true)}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: Spacing.three },
  flexOne: { flex: 1 },
  heroCard: { padding: Spacing.three, borderRadius: Radius.lg, borderWidth: 1, gap: Spacing.one, marginTop: Spacing.two },
  badgeRow: { flexDirection: 'row', gap: Spacing.one, flexWrap: 'wrap' },
  hospitalTitle: { fontSize: 20, fontWeight: '700' },
  section: { marginTop: Spacing.four, gap: Spacing.two },
  sectionTitle: { fontSize: 16, fontWeight: '700' },
  serviceRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: Spacing.two, borderBottomWidth: 1, gap: Spacing.two },
  serviceTitle: { fontSize: 14, fontWeight: '600' },
  successBanner: { padding: Spacing.three, borderRadius: Radius.md, marginTop: Spacing.three, flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  actionFooter: { marginTop: Spacing.four },
});
