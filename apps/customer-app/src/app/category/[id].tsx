import { useLocalSearchParams, useRouter } from 'expo-router';
import React, { useState } from 'react';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { ThemedText } from '@/components/themed-text';
import { PrimaryButton } from '@/components/ui/primary-button';
import { ScreenHeader } from '@/components/ui/screen-header';
import { StatusBadge } from '@/components/ui/status-badge';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

interface CategoryProduct {
  id: string;
  name: string;
  brand: string;
  price: number;
  originalPrice?: number;
  rating: string;
  deliveryTime: string;
  inStock: boolean;
}

const CATEGORY_ITEMS: Record<string, { title: string; items: CategoryProduct[] }> = {
  food: {
    title: 'Food & Nutrition',
    items: [
      { id: 'p1', name: 'Royal Canin Maxi Adult Dry Food (3kg)', brand: 'Royal Canin', price: 2199, originalPrice: 2499, rating: '4.9 ★', deliveryTime: '15-25 mins', inStock: true },
      { id: 'p2', name: 'Pedigree Adult Chicken & Vegetables (10kg)', brand: 'Pedigree', price: 1850, originalPrice: 1999, rating: '4.7 ★', deliveryTime: '20-30 mins', inStock: true },
      { id: 'p3', name: 'Farmina N&D Grain Free Formula (2.5kg)', brand: 'Farmina', price: 2890, rating: '4.9 ★', deliveryTime: '15-25 mins', inStock: true },
    ],
  },
  grooming: {
    title: 'Grooming Services & Kits',
    items: [
      { id: 'g1', name: 'Full De-Shedding & Anti-Flea Shampoo', brand: 'PetHead', price: 699, originalPrice: 850, rating: '4.8 ★', deliveryTime: '15 mins', inStock: true },
      { id: 'g2', name: 'Professional Stainless Nail Clipper Set', brand: 'PawsPro', price: 449, rating: '4.7 ★', deliveryTime: '20 mins', inStock: true },
    ],
  },
  hospitals: {
    title: 'Hospitals & Vet Services',
    items: [
      { id: 'h1', name: 'General OPD Consultation Ticket', brand: 'PetCare Hospital', price: 499, rating: '4.9 ★', deliveryTime: 'Instant Booking', inStock: true },
      { id: 'h2', name: 'Annual Pet Health & Vaccination Package', brand: 'City Pet Hospital', price: 1999, originalPrice: 2499, rating: '4.8 ★', deliveryTime: 'Instant Booking', inStock: true },
    ],
  },
  vaccinations: {
    title: 'Vaccinations & Deworming',
    items: [
      { id: 'v1', name: 'DHPPi + Rabies Combined Vaccination', brand: 'Nobivac', price: 1200, rating: '4.9 ★', deliveryTime: 'Clinic Visit', inStock: true },
      { id: 'v2', name: 'Drontal Plus Deworming Tablets (3 Tabs)', brand: 'Bayer', price: 350, rating: '4.8 ★', deliveryTime: '15-25 mins', inStock: true },
    ],
  },
  toys: {
    title: 'Toys & Enrichment',
    items: [
      { id: 't1', name: 'KONG Classic Rubber Chew Toy (Large)', brand: 'KONG', price: 899, rating: '4.9 ★', deliveryTime: '15-25 mins', inStock: true },
      { id: 't2', name: 'Interactive Squeaky Plush Tug Rope', brand: 'Outward Hound', price: 499, rating: '4.6 ★', deliveryTime: '20 mins', inStock: true },
    ],
  },
  treats: {
    title: 'Treats & Chews',
    items: [
      { id: 'tr1', name: 'Jerky High-Protein Chicken Sticks (150g)', brand: 'Chip Chop', price: 299, originalPrice: 349, rating: '4.8 ★', deliveryTime: '15-25 mins', inStock: true },
      { id: 'tr2', name: 'Natural Dental Calcium Bones (Pack of 4)', brand: 'Gnawlers', price: 249, rating: '4.7 ★', deliveryTime: '15 mins', inStock: true },
    ],
  },
};

export default function CategoryScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const theme = useTheme();

  const categoryData = CATEGORY_ITEMS[id || 'food'] || {
    title: typeof id === 'string' ? id.charAt(0).toUpperCase() + id.slice(1) : 'Category',
    items: CATEGORY_ITEMS.food.items,
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <ScreenHeader title={categoryData.title} subtitle="Same-day delivery in Tirupati" />

      <FlatList
        data={categoryData.items}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContainer}
        renderItem={({ item }) => (
          <View style={[styles.card, Shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
            <View style={styles.cardInfo}>
              <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>{item.brand}</ThemedText>
              <ThemedText style={[styles.itemName, { color: theme.text }]}>{item.name}</ThemedText>
              <View style={styles.priceRow}>
                <ThemedText style={[styles.price, { color: theme.primary }]}>₹{item.price}</ThemedText>
                {item.originalPrice ? (
                  <ThemedText style={[styles.strikethrough, { color: theme.textSecondary }]}>₹{item.originalPrice}</ThemedText>
                ) : null}
                <StatusBadge label={item.rating} color={theme.warning} />

              </View>
            </View>

            <PrimaryButton label="ADD" onPress={() => router.push(`/commerce/product-detail?id=${item.id}` as never)} />
          </View>
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: Spacing.three },
  listContainer: { gap: Spacing.three, paddingVertical: Spacing.two },
  card: { flexDirection: 'row', alignItems: 'center', padding: Spacing.three, borderRadius: Radius.lg, borderWidth: 1, gap: Spacing.two },
  cardInfo: { flex: 1, gap: 4 },
  itemName: { fontSize: 15, fontWeight: '700' },
  priceRow: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two, marginTop: 4 },
  price: { fontSize: 16, fontWeight: '700' },
  strikethrough: { textDecorationLine: 'line-through', fontSize: 12 },
});
