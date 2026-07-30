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

export default function GroomerProfileScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const theme = useTheme();

  const groomerName = id === 'fluffy-tails' ? 'Fluffy Tails Grooming Salon' : 'Paws & Bubbles Spa';
  const [booked, setBooked] = useState(false);

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <ScreenHeader title={groomerName} subtitle="Luxury Pet Spa • Tirupati" />

      <View style={[styles.heroCard, Shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
        <View style={styles.badgeRow}>
          <StatusBadge label="4.9 ★ (145+ reviews)" color={theme.warning} />
          <StatusBadge label="Certified Groomers" color={theme.success} />
        </View>

        <ThemedText style={[styles.groomerTitle, { color: theme.text }]}>{groomerName}</ThemedText>
        <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>📍 Tilak Road, Near Mahati Auditorium, Tirupati</ThemedText>
        <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>✂️ Specialized in medicated baths, de-shedding & breed styling</ThemedText>
      </View>

      <View style={styles.section}>
        <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>Popular Spa Packages</ThemedText>
        {[
          { name: 'Full Grooming & Bath Package', desc: 'Warm Bath, Blow Dry, Haircut, Nail Trimming, Ear Cleaning', price: '₹1,299' },
          { name: 'Basic Hygiene Bath & Trim', desc: 'Anti-Tick Bath, Sanitary Trim, Paw Massage & Nail Buffing', price: '₹699' },
          { name: 'Puppy First Bath Experience', desc: 'Gentle Tearless Bath, Fluff Dry, Paw Balm & Treat Cup', price: '₹499' },
        ].map((pkg, idx) => (
          <View key={idx} style={[styles.pkgCard, { backgroundColor: theme.muted, borderColor: theme.border }]}>
            <View style={styles.flexOne}>
              <ThemedText style={[styles.pkgName, { color: theme.text }]}>{pkg.name}</ThemedText>
              <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>{pkg.desc}</ThemedText>
            </View>
            <ThemedText style={[styles.pkgPrice, { color: theme.primary }]}>{pkg.price}</ThemedText>
          </View>
        ))}
      </View>

      {booked ? (
        <View style={[styles.bookedBanner, { backgroundColor: theme.primarySoft }]}>
          <AppIcon name="sparkle" color={theme.success} size={24} />
          <ThemedText style={{ color: theme.success, fontWeight: '700', fontSize: 13 }}>
            Spa Session Requested! Slot reserved for today.
          </ThemedText>
        </View>
      ) : null}

      <View style={styles.actionFooter}>
        <PrimaryButton label={booked ? 'Request Another Session' : 'Book Grooming Session'} onPress={() => setBooked(true)} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: Spacing.three },
  flexOne: { flex: 1 },
  heroCard: { padding: Spacing.three, borderRadius: Radius.lg, borderWidth: 1, gap: Spacing.one, marginTop: Spacing.two },
  badgeRow: { flexDirection: 'row', gap: Spacing.one, flexWrap: 'wrap' },
  groomerTitle: { fontSize: 20, fontWeight: '700' },
  section: { marginTop: Spacing.four, gap: Spacing.two },
  sectionTitle: { fontSize: 16, fontWeight: '700' },
  pkgCard: { padding: Spacing.three, borderRadius: Radius.md, borderWidth: 1, flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  pkgName: { fontWeight: '700', fontSize: 14 },
  pkgPrice: { fontSize: 15, fontWeight: '700' },
  bookedBanner: { padding: Spacing.three, borderRadius: Radius.md, marginTop: Spacing.three, flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  actionFooter: { marginTop: Spacing.four },
});
