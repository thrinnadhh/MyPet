import { useRouter } from 'expo-router';
import React, { useMemo, useState } from 'react';
import { FlatList, Image, Pressable, StyleSheet, TextInput, View } from 'react-native';

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
import { type CommerceProduct } from '@/services/catalog-data';

interface CategoryTemplateProps {
  title: string;
  subtitle?: string;
  products: CommerceProduct[];
}

export function CategoryTemplate({ title, subtitle, products }: CategoryTemplateProps) {
  const router = useRouter();
  const theme = useTheme();
  const { addToCart, items, updateQuantity } = useCart();
  const { isFavourite, toggleFavourite } = useFavourites();

  const [searchQuery, setSearchQuery] = useState('');
  const [selectedSort, setSelectedSort] = useState<'RELEVANCE' | 'PRICE_LOW' | 'PRICE_HIGH' | 'RATING'>('RELEVANCE');
  const [inStockOnly, setInStockOnly] = useState(false);
  const [selectedBrand, setSelectedBrand] = useState<string | null>(null);

  // Extract unique brands
  const brands = useMemo(() => {
    const set = new Set(products.map((p) => p.brand));
    return Array.from(set);
  }, [products]);

  // Filter & Sort Logic
  const filteredProducts = useMemo(() => {
    let list = [...products];

    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      list = list.filter((p) => p.name.toLowerCase().includes(q) || p.brand.toLowerCase().includes(q));
    }

    if (inStockOnly) {
      list = list.filter((p) => p.inStock);
    }

    if (selectedBrand) {
      list = list.filter((p) => p.brand === selectedBrand);
    }

    if (selectedSort === 'PRICE_LOW') {
      list.sort((a, b) => a.price - b.price);
    } else if (selectedSort === 'PRICE_HIGH') {
      list.sort((a, b) => b.price - a.price);
    } else if (selectedSort === 'RATING') {
      list.sort((a, b) => parseFloat(b.rating) - parseFloat(a.rating));
    }

    return list;
  }, [products, searchQuery, inStockOnly, selectedBrand, selectedSort]);

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <ScreenHeader title={title} subtitle={subtitle ?? 'Same-day local delivery'} />

      {/* Internal Search */}
      <View style={[styles.searchBox, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
        <AppIcon name="search" color={theme.textSecondary} size={18} />
        <TextInput
          value={searchQuery}
          onChangeText={setSearchQuery}
          placeholder={`Search ${title.toLowerCase()}...`}
          placeholderTextColor={theme.textSecondary}
          style={[styles.searchInput, { color: theme.text }]}
        />
        {searchQuery.length > 0 && (
          <Pressable onPress={() => setSearchQuery('')}>
            <AppIcon name="warning" color={theme.textSecondary} size={16} />
          </Pressable>
        )}
      </View>

      {/* Filter & Sort Chips */}
      <View style={styles.filterRow}>
        <FlatList
          horizontal
          showsHorizontalScrollIndicator={false}
          data={[
            { id: 'ALL', label: 'All Brands', active: selectedBrand === null, onPress: () => setSelectedBrand(null) },
            { id: 'STOCK', label: 'In Stock Only', active: inStockOnly, onPress: () => setInStockOnly(!inStockOnly) },
            { id: 'LOW', label: 'Price: Low to High', active: selectedSort === 'PRICE_LOW', onPress: () => setSelectedSort(selectedSort === 'PRICE_LOW' ? 'RELEVANCE' : 'PRICE_LOW') },
            { id: 'RATING', label: 'Top Rated', active: selectedSort === 'RATING', onPress: () => setSelectedSort(selectedSort === 'RATING' ? 'RELEVANCE' : 'RATING') },
            ...brands.map((b) => ({ id: b, label: b, active: selectedBrand === b, onPress: () => setSelectedBrand(selectedBrand === b ? null : b) })),
          ]}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.filterList}
          renderItem={({ item }) => (
            <FilterChip label={item.label} selected={item.active} onPress={item.onPress} />
          )}
        />
      </View>

      {/* Products Grid / List */}
      {filteredProducts.length === 0 ? (
        <View style={styles.emptyState}>
          <AppIcon name="store" color={theme.textSecondary} size={40} />
          <ThemedText style={styles.emptyTitle}>No products found</ThemedText>
          <ThemedText style={{ color: theme.textSecondary, fontSize: 13, textAlign: 'center' }}>
            Try clearing your search query or filters to explore items.
          </ThemedText>
        </View>
      ) : (
        <FlatList
          data={filteredProducts}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.listContent}
          renderItem={({ item }) => {
            const isFav = isFavourite('PRODUCT', item.id);
            const cartItem = items.find((i) => i.product.id === item.id);
            const qtyInCart = cartItem ? cartItem.quantity : 0;

            return (
              <Pressable
                onPress={() => router.push(`/commerce/product-detail?id=${item.id}` as never)}
                style={({ pressed }) => [
                  styles.productCard,
                  shadows.raised,
                  { backgroundColor: theme.backgroundElement, borderColor: theme.border },
                  pressed && styles.pressed,
                ]}
              >
                <View style={styles.imageContainer}>
                  <Image source={{ uri: item.imageUrl }} style={styles.productImage} resizeMode="cover" />
                  <Pressable
                    onPress={() => void toggleFavourite('PRODUCT', item.id)}
                    style={[styles.favBadge, { backgroundColor: theme.background }]}
                    accessibilityLabel="Toggle Favourite"
                  >
                    <AppIcon name={isFav ? 'check' : 'warning'} color={isFav ? theme.danger : theme.textSecondary} size={18} />
                  </Pressable>
                  {item.isNewArrival && (
                    <View style={styles.newArrivalTag}>
                      <ThemedText style={styles.newArrivalText}>NEW</ThemedText>
                    </View>
                  )}
                </View>

                <View style={styles.productDetails}>
                  <View style={styles.brandRow}>
                    <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>{item.brand}</ThemedText>
                    <StatusBadge label={item.rating} color={theme.warning} />
                  </View>

                  <ThemedText style={[styles.productName, { color: theme.text }]} numberOfLines={2}>
                    {item.name}
                  </ThemedText>

                  <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>⚡ {item.deliveryTime}</ThemedText>

                  <View style={styles.priceFooter}>
                    <View>
                      <View style={styles.priceRow}>
                        <ThemedText style={[styles.priceText, { color: theme.primary }]}>₹{item.price}</ThemedText>
                        {item.originalPrice ? (
                          <ThemedText style={styles.strikethrough}>₹{item.originalPrice}</ThemedText>
                        ) : null}
                      </View>
                      {!item.inStock && (
                        <ThemedText style={{ fontSize: 11, color: theme.danger, fontWeight: '700' }}>Out of Stock</ThemedText>
                      )}
                    </View>

                    {qtyInCart > 0 ? (
                      <View style={[styles.stepper, { backgroundColor: theme.primarySoft, borderColor: theme.primary }]}>
                        <Pressable onPress={() => updateQuantity(item.id, undefined, qtyInCart - 1)} style={styles.stepBtn}>
                          <ThemedText style={{ color: theme.primary, fontWeight: '800' }}>-</ThemedText>
                        </Pressable>
                        <ThemedText style={{ color: theme.primary, fontWeight: '700', paddingHorizontal: 6 }}>{qtyInCart}</ThemedText>
                        <Pressable onPress={() => updateQuantity(item.id, undefined, qtyInCart + 1)} style={styles.stepBtn}>
                          <ThemedText style={{ color: theme.primary, fontWeight: '800' }}>+</ThemedText>
                        </Pressable>
                      </View>
                    ) : (
                      <PrimaryButton
                        label="ADD"
                        disabled={!item.inStock}
                        onPress={() => addToCart(item, item.variants[0])}
                      />
                    )}
                  </View>
                </View>
              </Pressable>
            );
          }}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: spacing.x3 },
  searchBox: { flexDirection: 'row', alignItems: 'center', gap: spacing.x2, borderWidth: 1, borderRadius: radii.compact, paddingHorizontal: spacing.x3, height: 44, marginBottom: spacing.x2 },
  searchInput: { flex: 1, height: 44, ...typography.body },
  filterRow: { paddingVertical: spacing.x1, marginBottom: spacing.x2 },
  filterList: { gap: spacing.x2 },
  emptyState: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.x6, gap: spacing.x2 },
  emptyTitle: { ...typography.headline, fontSize: 16, marginTop: 8 },
  listContent: { gap: spacing.x3, paddingBottom: spacing.x6 },
  productCard: { flexDirection: 'row', borderRadius: radii.card, borderWidth: 1, padding: spacing.x3, gap: spacing.x3 },
  imageContainer: { width: 100, height: 100, borderRadius: radii.compact, overflow: 'hidden', position: 'relative' },
  productImage: { width: '100%', height: '100%' },
  favBadge: { position: 'absolute', top: 4, right: 4, borderRadius: 12, padding: 4, elevation: 2 },
  newArrivalTag: { position: 'absolute', bottom: 4, left: 4, backgroundColor: '#FF3B30', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4 },
  newArrivalText: { color: '#FFFFFF', fontSize: 9, fontWeight: '900' },
  productDetails: { flex: 1, gap: spacing.x1 },
  brandRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  productName: { ...typography.headline, fontSize: 14, fontWeight: '700' },
  priceFooter: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end', marginTop: 'auto' },
  priceRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.x1 },
  priceText: { ...typography.headline, fontSize: 16, fontWeight: '800' },
  strikethrough: { textDecorationLine: 'line-through', fontSize: 12, color: '#888888' },
  stepper: { flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderRadius: radii.compact, paddingHorizontal: 4, height: 36 },
  stepBtn: { paddingHorizontal: 8, paddingVertical: 4 },
  pressed: { opacity: 0.88 },
});
