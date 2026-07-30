import { useRouter } from 'expo-router';
import React, { useMemo, useState } from 'react';
import { FlatList, Image, Pressable, ScrollView, StyleSheet, View } from 'react-native';


import { AppIcon } from '@/components/app-icon';
import { FilterChip } from '@/components/foundation/primitives';
import { ThemedText } from '@/components/themed-text';
import { PrimaryButton } from '@/components/ui/primary-button';
import { ScreenHeader } from '@/components/ui/screen-header';
import { StatusBadge } from '@/components/ui/status-badge';
import { useCart } from '@/context/CartContext';
import { useFavourites } from '@/context/FavouritesContext';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { SAMPLE_PRODUCTS, SHOPS_DATA, type CommerceProduct, type ShopProfileData } from '@/services/catalog-data';

export default function FavouritesScreen() {
  const router = useRouter();
  const theme = useTheme();
  const { favourites, toggleFavourite } = useFavourites();
  const { addToCart } = useCart();

  const [activeTab, setActiveTab] = useState<'ALL' | 'PRODUCTS' | 'SHOPS'>('ALL');

  // Filter items
  const favProducts = useMemo(() => {
    const prodFavs = favourites.filter((f) => f.targetType.toUpperCase() === 'PRODUCT');
    return SAMPLE_PRODUCTS.filter((p) => prodFavs.some((f) => f.targetId === p.id));
  }, [favourites]);

  const favShops = useMemo(() => {
    const shopFavs = favourites.filter((f) => f.targetType.toUpperCase() === 'SHOP');
    return Object.values(SHOPS_DATA).filter((s) => shopFavs.some((f) => f.targetId === s.id));
  }, [favourites]);

  const displayCount = favProducts.length + favShops.length;

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <ScreenHeader title="My Favourites" subtitle={`${displayCount} saved items & shops`} />

      {/* Filter Tabs */}
      <View style={styles.tabRow}>
        <FilterChip label={`All (${displayCount})`} selected={activeTab === 'ALL'} onPress={() => setActiveTab('ALL')} />
        <FilterChip label={`Products (${favProducts.length})`} selected={activeTab === 'PRODUCTS'} onPress={() => setActiveTab('PRODUCTS')} />
        <FilterChip label={`Shops (${favShops.length})`} selected={activeTab === 'SHOPS'} onPress={() => setActiveTab('SHOPS')} />
      </View>

      {displayCount === 0 ? (
        <View style={styles.emptyState}>
          <AppIcon name="warning" color={theme.textSecondary} size={48} />
          <ThemedText style={styles.emptyTitle}>No Favourites Saved Yet</ThemedText>
          <ThemedText style={{ color: theme.textSecondary, fontSize: 13, textAlign: 'center' }}>
            Tap the heart icon on any product or shop profile to save it here for quick access.
          </ThemedText>
          <PrimaryButton label="Explore Products" onPress={() => router.push('/category/food' as never)} />
        </View>
      ) : (
        <ScrollView contentContainerStyle={styles.listContent} showsVerticalScrollIndicator={false}>
          {/* Fav Shops Section */}
          {(activeTab === 'ALL' || activeTab === 'SHOPS') && favShops.length > 0 && (
            <View style={styles.section}>
              <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>Saved Shops</ThemedText>
              {favShops.map((shop) => (
                <Pressable
                  key={shop.id}
                  onPress={() => router.push(`/shop/${shop.id}` as never)}
                  style={[styles.shopCard, shadows.raised, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}
                >
                  <Image source={{ uri: shop.heroImageUrl }} style={styles.shopThumb} resizeMode="cover" />
                  <View style={{ flex: 1, gap: 4 }}>
                    <ThemedText style={{ fontWeight: '700', fontSize: 15, color: theme.text }}>{shop.name}</ThemedText>
                    <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>{shop.tagline}</ThemedText>
                    <StatusBadge label={shop.rating} color={theme.warning} />
                  </View>
                  <Pressable onPress={() => void toggleFavourite('SHOP', shop.id)} style={{ padding: 4 }}>
                    <AppIcon name="check" color={theme.danger} size={22} />
                  </Pressable>
                </Pressable>
              ))}
            </View>
          )}

          {/* Fav Products Section */}
          {(activeTab === 'ALL' || activeTab === 'PRODUCTS') && favProducts.length > 0 && (
            <View style={styles.section}>
              <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>Saved Products</ThemedText>
              {favProducts.map((prod) => (
                <Pressable
                  key={prod.id}
                  onPress={() => router.push(`/commerce/product-detail?id=${prod.id}` as never)}
                  style={[styles.prodCard, shadows.raised, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}
                >
                  <Image source={{ uri: prod.imageUrl }} style={styles.prodThumb} resizeMode="cover" />
                  <View style={{ flex: 1, gap: 4 }}>
                    <ThemedText style={{ fontSize: 11, color: theme.textSecondary }}>{prod.brand}</ThemedText>
                    <ThemedText style={{ fontWeight: '700', fontSize: 14, color: theme.text }} numberOfLines={1}>{prod.name}</ThemedText>
                    <ThemedText style={{ fontWeight: '800', fontSize: 15, color: theme.primary }}>₹{prod.price}</ThemedText>
                  </View>
                  <View style={{ gap: 8, alignItems: 'flex-end' }}>
                    <Pressable onPress={() => void toggleFavourite('PRODUCT', prod.id)} style={{ padding: 4 }}>
                      <AppIcon name="check" color={theme.danger} size={20} />
                    </Pressable>
                    <PrimaryButton label="ADD" onPress={() => addToCart(prod, prod.variants[0])} />
                  </View>
                </Pressable>
              ))}
            </View>
          )}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: spacing.x3 },
  tabRow: { flexDirection: 'row', gap: spacing.x2, marginBottom: spacing.x3 },
  emptyState: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.x6, gap: spacing.x3 },
  emptyTitle: { ...typography.headline, fontSize: 18, marginTop: 8 },
  listContent: { gap: spacing.x4, paddingBottom: spacing.x6 },
  section: { gap: spacing.x3 },
  sectionTitle: { ...typography.headline, fontSize: 16, fontWeight: '700' },
  shopCard: { flexDirection: 'row', alignItems: 'center', padding: spacing.x3, borderRadius: radii.card, borderWidth: 1, gap: spacing.x3 },
  shopThumb: { width: 60, height: 60, borderRadius: radii.compact },
  prodCard: { flexDirection: 'row', alignItems: 'center', padding: spacing.x3, borderRadius: radii.card, borderWidth: 1, gap: spacing.x3 },
  prodThumb: { width: 70, height: 70, borderRadius: radii.compact },
});
