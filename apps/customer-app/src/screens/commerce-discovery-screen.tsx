import { useRouter } from 'expo-router';
import React, { useCallback, useEffect, useState } from 'react';
import { FlatList, Image, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { AppBar, StateView } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { PrimaryButton } from '@/components/ui/primary-button';
import { StatusBadge } from '@/components/ui/status-badge';
import { INITIAL_MARKET } from '@/config/markets';
import { useCart } from '@/context/CartContext';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { useTranslation } from '@/i18n';
import { SHOPS_DATA, type ShopProfileData } from '@/services/catalog-data';
import { isOfflineError } from '@/services/customer-profile';
import { fetchProviders, type ProviderSummary } from '@/services/provider-discovery';

type LoadState = 'loading' | 'ready' | 'offline' | 'error';

const COMMERCE_CATEGORIES = [
  { id: 'food', title: 'Food', icon: 'food' },
  { id: 'furniture', title: 'Furniture', icon: 'home' },
  { id: 'toys', title: 'Toys', icon: 'sparkle' },
  { id: 'travel', title: 'Travel', icon: 'location' },
  { id: 'treats', title: 'Treats', icon: 'paw' },
  { id: 'waste', title: 'Waste', icon: 'warning' },
  { id: 'new-arrivals', title: 'New', icon: 'sparkle' },
];

export function ShopCategoryNav() {
  const router = useRouter();
  const theme = useTheme();

  return (
    <View style={styles.section}>
      <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>Shop by Category</ThemedText>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.catScroll}>
        {COMMERCE_CATEGORIES.map((cat) => (
          <Pressable
            key={cat.id}
            onPress={() => router.push(`/category/${cat.id}` as never)}
            style={({ pressed }) => [styles.catItem, pressed && styles.pressed]}
          >
            <View style={[styles.catIconBox, { backgroundColor: theme.primarySoft, borderColor: theme.primary }]}>
              <AppIcon name={cat.icon as never} color={theme.primary} size={22} />
            </View>
            <ThemedText style={[styles.catLabel, { color: theme.text }]} numberOfLines={1}>
              {cat.title}
            </ThemedText>
          </Pressable>
        ))}
      </ScrollView>
    </View>
  );
}

export function ShopStoreCards({ shops }: { shops: ShopProfileData[] }) {
  const router = useRouter();
  const theme = useTheme();

  return (
    <View style={styles.section}>
      <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>Nearby Pet Superstores</ThemedText>
      <View style={styles.shopGrid}>
        {shops.map((shop) => (
          <Pressable
            key={shop.id}
            onPress={() => router.push(`/shop/${shop.id}` as never)}
            style={({ pressed }) => [
              styles.shopCard,
              shadows.raised,
              { backgroundColor: theme.backgroundElement, borderColor: theme.border },
              pressed && styles.pressed,
            ]}
          >
            <Image source={{ uri: shop.heroImageUrl }} style={styles.shopBanner} resizeMode="cover" />
            <View style={styles.shopContent}>
              <View style={styles.rowBetween}>
                <ThemedText style={[styles.shopTitle, { color: theme.text }]}>{shop.name}</ThemedText>
                <StatusBadge label={shop.rating} color={theme.warning} />
              </View>

              <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>{shop.tagline}</ThemedText>
              <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>📍 {shop.address}</ThemedText>

              <View style={styles.rowBetween}>
                <ThemedText style={{ fontSize: 12, color: theme.primary, fontWeight: '700' }}>⚡ Delivery: {shop.deliveryEta}</ThemedText>
                <PrimaryButton label="Explore Shop" onPress={() => router.push(`/shop/${shop.id}` as never)} />
              </View>
            </View>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

export default function CommerceDiscoveryScreen() {
  const router = useRouter();
  const theme = useTheme();
  const { t } = useTranslation();
  const { providerName, totalItemsCount, subtotalAmount } = useCart();

  const [state, setState] = useState<LoadState>('ready');
  const shopsList = Object.values(SHOPS_DATA);

  return (
    <ScreenShell
      header={<AppBar title={t('commerceFoundation.title')} subtitle="Pet Stores & Express Delivery in Tirupati" />}
      testID="commerce-discovery-screen"
    >
      <View style={styles.container}>
        <ShopCategoryNav />
        <ShopStoreCards shops={shopsList} />
      </View>

      {totalItemsCount > 0 && (
        <View style={[styles.stickyCart, shadows.raised, { backgroundColor: theme.primary }]}>

          <View>
            <ThemedText style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 14 }}>
              {totalItemsCount} {totalItemsCount === 1 ? 'Item' : 'Items'} | ₹{subtotalAmount}
            </ThemedText>
            <ThemedText style={{ color: 'rgba(255,255,255,0.8)', fontSize: 12 }}>{providerName ?? 'Cart'}</ThemedText>
          </View>
          <Pressable
            onPress={() => router.push('/cart' as never)}
            style={styles.viewCartBtn}
            accessibilityRole="button"
            accessibilityLabel="View Cart"
          >
            <ThemedText style={{ color: theme.primary, fontWeight: '800' }}>View Cart →</ThemedText>
          </Pressable>
        </View>
      )}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: { gap: spacing.x4, paddingBottom: 80 },
  section: { gap: spacing.x2 },
  sectionTitle: { ...typography.headline, fontSize: 16, fontWeight: '700' },
  catScroll: { gap: spacing.x3, paddingVertical: spacing.x1 },
  catItem: { width: 68, alignItems: 'center', gap: 4 },
  catIconBox: { width: 56, height: 56, borderRadius: 28, borderWidth: 1.5, alignItems: 'center', justifyContent: 'center' },
  catLabel: { fontSize: 11, fontWeight: '600', textAlign: 'center' },
  shopGrid: { gap: spacing.x3 },
  shopCard: { borderRadius: radii.card, borderWidth: 1, overflow: 'hidden' },
  shopBanner: { width: '100%', height: 120 },
  shopContent: { padding: spacing.x3, gap: spacing.x2 },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  shopTitle: { ...typography.headline, fontSize: 16, fontWeight: '700' },
  stickyCart: { position: 'absolute', bottom: 16, left: 16, right: 16, borderRadius: radii.card, paddingHorizontal: spacing.x4, paddingVertical: spacing.x3, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  viewCartBtn: { backgroundColor: '#FFFFFF', paddingHorizontal: spacing.x4, paddingVertical: spacing.x2, borderRadius: radii.compact },
  pressed: { opacity: 0.88 },
});
