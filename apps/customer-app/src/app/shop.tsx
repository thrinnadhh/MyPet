import React, { useCallback, useState, useEffect } from 'react';
import { StyleSheet, Image, View, FlatList, TouchableOpacity, ScrollView, Platform, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing, Colors } from '@/constants/theme';
import { useColorScheme } from 'react-native';

const API_BASE_URL = Platform.select({
  android: 'http://10.0.2.2:8080',
  ios: 'http://localhost:8080',
  default: 'http://localhost:8080',
});

const CATEGORIES = [
  { id: '1', name: 'Food & Nutrition', icon: '🍖' },
  { id: '2', name: 'Toys & Fun', icon: '🧸' },
  { id: '3', name: 'Pharmacy', icon: '💊' },
  { id: '4', name: 'Accessories', icon: '🦮' },
];

const BACKUP_STORES = [
  {
    id: '1',
    name: 'Paws & Petals Store (Offline Demo)',
    distance: '0.8 km',
    rating: '4.9',
    ratingCount: '120',
    deliveryTime: '15-20 mins',
    tags: ['Organic', 'Premium', 'Fast Delivery'],
  },
  {
    id: '2',
    name: 'Happy Pets Store (Offline Demo)',
    distance: '1.5 km',
    rating: '4.7',
    ratingCount: '85',
    deliveryTime: '25-30 mins',
    tags: ['Toys', 'Food', 'Discounts'],
  },
];

interface Store {
  id: string;
  name: string;
  distance: string;
  rating: string;
  ratingCount: string;
  deliveryTime: string;
  tags: string[];
}

const StoreCard = React.memo(({ item, colors }: { item: Store, colors: any }) => {
  return (
    <TouchableOpacity 
      style={[
        styles.storeCard, 
        { 
          backgroundColor: colors.backgroundElement,
          borderColor: colors.textSecondary,
        }
      ]}
      activeOpacity={0.7}
    >
      <View style={styles.storeInfo}>
        <View style={{ flex: 1, paddingRight: Spacing.two }}>
          <ThemedText style={[styles.storeName, { color: colors.text }]}>
            {item.name}
          </ThemedText>
          <View style={styles.storeMeta}>
            <ThemedText type="small" style={{ color: colors.textSecondary }}>
              ⭐ {item.rating} ({item.ratingCount} reviews)
            </ThemedText>
            <ThemedText type="small" style={[styles.metaDivider, { color: colors.textSecondary }]}>•</ThemedText>
            <ThemedText type="small" style={{ color: colors.textSecondary }}>
              📍 {item.distance}
            </ThemedText>
          </View>
        </View>
        <View style={[styles.timeBadge, { backgroundColor: colors.background, borderColor: colors.primary }]}>
          <ThemedText type="small" style={{ color: colors.primary, fontWeight: '700' }}>
            🕒 {item.deliveryTime}
          </ThemedText>
        </View>
      </View>
      <View style={styles.tagContainer}>
        {item.tags.map((tag, idx) => (
          <View key={idx} style={[styles.tag, { backgroundColor: colors.backgroundSelected }]}>
            <ThemedText type="small" style={[styles.tagText, { color: colors.text }]}>
              {tag}
            </ThemedText>
          </View>
        ))}
      </View>
    </TouchableOpacity>
  );
});

export default function ShopScreen() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];

  const [stores, setStores] = useState<Store[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const fetchStores = async () => {
    try {
      // Fetch active providers of type PET_STORE near Indiranagar, Bangalore
      const response = await fetch(
        `${API_BASE_URL}/api/v1/discovery/providers?longitude=77.6404&latitude=12.9719&radius=10.0&type=PET_STORE`,
        { headers: { 'Accept': 'application/json' } }
      );
      if (!response.ok) throw new Error('Network response not ok');
      const data = await response.json();
      
      const mapped = data.map((p: any) => ({
        id: p.providerId,
        name: p.name,
        distance: `${p.distanceKm.toFixed(1)} km`,
        rating: p.ratingAvg ? p.ratingAvg.toFixed(1) : '0.0',
        ratingCount: p.ratingCount ? p.ratingCount.toString() : '0',
        deliveryTime: '15-25 mins',
        tags: p.description ? [p.description.substring(0, 15), 'Store'] : ['Pet Store'],
      }));
      setStores(mapped);
    } catch (error) {
      console.warn('Discovery API unavailable, falling back to mock data', error);
      setStores(BACKUP_STORES);
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    fetchStores();
  }, []);

  const handleRefresh = () => {
    setIsRefreshing(true);
    fetchStores();
  };

  const renderStore = useCallback(({ item }: { item: Store }) => {
    return <StoreCard item={item} colors={colors} />;
  }, [colors]);

  const keyExtractor = useCallback((item: Store) => item.id, []);

  return (
    <ThemedView style={[styles.container, { backgroundColor: colors.background }]}>
      <SafeAreaView style={styles.safeArea}>
        <View style={[styles.header, { borderBottomColor: colors.backgroundSelected }]}>
          <View>
            <ThemedText type="small" style={{ color: colors.textSecondary, fontWeight: '700', letterSpacing: 1 }}>
              DELIVERING TO
            </ThemedText>
            <ThemedText style={{ color: colors.text, fontWeight: '800' }}>
              📍 Home — Indiranagar, Bangalore
            </ThemedText>
          </View>
        </View>

        <ScrollView 
          showsVerticalScrollIndicator={false} 
          contentContainerStyle={styles.scrollContent}
        >
          {/* Featured Card */}
          <View style={[styles.heroContainer, { borderColor: colors.text, backgroundColor: colors.backgroundElement }]}>
            <Image 
              source={require('@/assets/images/pet_store_hero.png')} 
              style={styles.heroImage}
            />
            <View style={styles.heroOverlay}>
              <ThemedText type="title" style={styles.heroTitle}>
                Paw & Petals
              </ThemedText>
              <ThemedText type="default" style={styles.heroSubtitle}>
                Premium Organic Pet Food & Treats delivered in 15 mins.
              </ThemedText>
            </View>
          </View>

          {/* Categories */}
          <ThemedText style={[styles.sectionTitle, { color: colors.text }]}>
            Shop by Category
          </ThemedText>
          <ScrollView 
            horizontal 
            showsHorizontalScrollIndicator={false} 
            contentContainerStyle={styles.categoriesContainer}
          >
            {CATEGORIES.map((cat) => (
              <TouchableOpacity 
                key={cat.id} 
                style={[
                  styles.categoryCard, 
                  { 
                    backgroundColor: colors.backgroundElement,
                    borderColor: colors.textSecondary,
                  }
                ]}
                activeOpacity={0.7}
              >
                <ThemedText style={styles.categoryIcon}>{cat.icon}</ThemedText>
                <ThemedText type="small" style={[styles.categoryName, { color: colors.text }]}>
                  {cat.name}
                </ThemedText>
              </TouchableOpacity>
            ))}
          </ScrollView>

          {/* Nearby Stores */}
          <View style={styles.sectionHeader}>
            <ThemedText style={[styles.sectionTitleText, { color: colors.text }]}>
              Stores Near You
            </ThemedText>
            {isLoading && <ActivityIndicator size="small" color={colors.primary} />}
          </View>
          
          <FlatList
            data={stores}
            renderItem={renderStore}
            keyExtractor={keyExtractor}
            scrollEnabled={false}
            contentContainerStyle={styles.storesList}
          />
        </ScrollView>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
  },
  header: {
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.three,
    borderBottomWidth: 2,
  },
  scrollContent: {
    paddingBottom: Spacing.six,
  },
  heroContainer: {
    marginHorizontal: Spacing.four,
    marginVertical: Spacing.four,
    height: 180,
    borderRadius: 24,
    borderWidth: 3,
    overflow: 'hidden',
    position: 'relative',
    // Claymorphic double shadow
    shadowColor: '#000',
    shadowOffset: { width: 4, height: 4 },
    shadowOpacity: 0.15,
    shadowRadius: 0,
    elevation: 4,
  },
  heroImage: {
    width: '100%',
    height: '100%',
  },
  heroOverlay: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: 'rgba(2, 44, 34, 0.75)',
    padding: Spacing.three,
  },
  heroTitle: {
    color: '#ffffff',
    fontSize: 20,
    fontWeight: '800',
  },
  heroSubtitle: {
    color: '#ECFDF5',
    marginTop: Spacing.one,
    fontSize: 12,
  },
  sectionTitle: {
    paddingHorizontal: Spacing.four,
    marginTop: Spacing.four,
    marginBottom: Spacing.two,
    fontSize: 18,
    fontWeight: '800',
  },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingRight: Spacing.four,
    marginTop: Spacing.four,
    marginBottom: Spacing.two,
  },
  sectionTitleText: {
    paddingHorizontal: Spacing.four,
    fontSize: 18,
    fontWeight: '800',
    flex: 1,
  },
  categoriesContainer: {
    paddingLeft: Spacing.four,
    paddingBottom: Spacing.two,
    gap: Spacing.three,
  },
  categoryCard: {
    width: 110,
    height: 100,
    borderRadius: 20,
    borderWidth: 2.5,
    justifyContent: 'center',
    alignItems: 'center',
    padding: Spacing.two,
    // Claymorphic shadow
    shadowColor: '#000',
    shadowOffset: { width: 3, height: 3 },
    shadowOpacity: 0.1,
    shadowRadius: 0,
    elevation: 2,
  },
  categoryIcon: {
    fontSize: 32,
    marginBottom: Spacing.one,
  },
  categoryName: {
    textAlign: 'center',
    fontWeight: '700',
    fontSize: 11,
  },
  storesList: {
    paddingHorizontal: Spacing.four,
    gap: Spacing.three,
  },
  storeCard: {
    padding: Spacing.four,
    borderRadius: 24,
    borderWidth: 3,
    gap: Spacing.two,
    // Claymorphic shadows
    shadowColor: '#000',
    shadowOffset: { width: 4, height: 4 },
    shadowOpacity: 0.12,
    shadowRadius: 0,
    elevation: 3,
    marginBottom: Spacing.two,
  },
  storeInfo: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  storeName: {
    fontSize: 16,
    fontWeight: '800',
  },
  storeMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: Spacing.one,
  },
  metaDivider: {
    marginHorizontal: Spacing.two,
  },
  timeBadge: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
    borderRadius: 16,
    borderWidth: 1.5,
    minHeight: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tagContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
    marginTop: Spacing.one,
  },
  tag: {
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.half,
    borderRadius: 10,
  },
  tagText: {
    fontSize: 11,
    fontWeight: '700',
  },
});
