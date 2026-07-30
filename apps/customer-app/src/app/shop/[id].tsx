import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { ThemedText } from '@/components/themed-text';
import { PrimaryButton } from '@/components/ui/primary-button';
import { ScreenHeader } from '@/components/ui/screen-header';
import { StatusBadge } from '@/components/ui/status-badge';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export default function ShopProfileScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const theme = useTheme();

  const shopName = id === 'petcare-pharmacy'
    ? 'PetCare Pharmacy & Supplies'
    : id === 'the-healthy-hound'
      ? 'The Healthy Hound Nutrition Hub'
      : 'The Posh Paws Superstore';

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <ScreenHeader title={shopName} subtitle="Verified Pet Partner • Tirupati" />

      <View style={[styles.heroCard, Shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
        <View style={styles.badgeRow}>
          <StatusBadge label="4.9 ★ (220+ reviews)" color={theme.warning} />
          <StatusBadge label="15-25 min delivery" color={theme.success} />
        </View>

        <ThemedText style={[styles.shopTitle, { color: theme.text }]}>{shopName}</ThemedText>
        <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>📍 Korlagunta Main Road, Tirupati, AP 517501</ThemedText>
        <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>🕒 Open today: 8:00 AM - 10:00 PM</ThemedText>
      </View>

      <View style={styles.section}>
        <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>Featured Product Categories</ThemedText>
        <View style={styles.catGrid}>
          {['Dry & Wet Food', 'Prescription Diet', 'Supplements & Vitamins', 'Grooming Essentials', 'Chew Toys & Bones'].map((cat, idx) => (
            <View key={idx} style={[styles.catCard, { backgroundColor: theme.primarySoft, borderColor: theme.primary }]}>
              <AppIcon name="store" color={theme.primary} size={20} />
              <ThemedText style={{ fontWeight: '700', color: theme.primary, fontSize: 13 }}>{cat}</ThemedText>
            </View>
          ))}
        </View>
      </View>

      <View style={styles.actionFooter}>
        <PrimaryButton label="Browse Shop Catalog" onPress={() => router.push('/category/food' as never)} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: Spacing.three },
  heroCard: { padding: Spacing.three, borderRadius: Radius.lg, borderWidth: 1, gap: Spacing.one, marginTop: Spacing.two },
  badgeRow: { flexDirection: 'row', gap: Spacing.one, flexWrap: 'wrap' },
  shopTitle: { fontSize: 20, fontWeight: '700' },
  section: { marginTop: Spacing.four, gap: Spacing.two },
  sectionTitle: { fontSize: 16, fontWeight: '700' },
  catGrid: { gap: Spacing.one },
  catCard: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two, padding: Spacing.two, borderRadius: Radius.sm, borderWidth: 1 },
  actionFooter: { marginTop: Spacing.four },
});
