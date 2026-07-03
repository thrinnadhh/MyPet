import React, { useCallback, useState, useEffect } from 'react';
import { StyleSheet, Image, View, FlatList, TouchableOpacity, ScrollView, ActivityIndicator, useColorScheme, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { AppIcon, type AppIconName } from '@/components/app-icon';
import { Spacing, Colors, Radius, Shadows } from '@/constants/theme';
import { appConfig } from '@/utils/app-config';
import { useAuth } from '@/context/AuthContext';

const CATEGORIES = [
  { id: '1', name: 'Food & Nutrition', icon: 'cart' },
  { id: '2', name: 'Toys & Fun', icon: 'sparkle' },
  { id: '3', name: 'Pharmacy', icon: 'medical' },
  { id: '4', name: 'Accessories', icon: 'paw' },
] as const;

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

interface Offering {
  offeringId: string;
  name: string;
  description?: string;
  category?: string;
  price: number;
  stockQuantity?: number;
}

interface Address {
  addressId: string;
  label?: string;
  line1: string;
  city: string;
  pincode: string;
}

interface OrderPayload {
  orderId: string;
  totalAmount: number | string;
}

interface ApiErrorPayload {
  error?: string;
}

const DEMO_DELIVERY_ADDRESS_ID = '11111111-1111-4111-8111-111111111111';

const StoreCard = React.memo(({ item, colors, isSelected, onPress }: { item: Store, colors: any, isSelected: boolean, onPress: () => void }) => {
  return (
    <TouchableOpacity 
      style={[
        styles.storeCard, 
        { 
          backgroundColor: colors.backgroundElement,
          borderColor: isSelected ? colors.primary : colors.textSecondary,
        }
      ]}
      activeOpacity={0.7}
      onPress={onPress}
    >
      <View style={styles.storeInfo}>
        <View style={{ flex: 1, paddingRight: Spacing.two }}>
          <ThemedText style={[styles.storeName, { color: colors.text }]}>
            {item.name}
          </ThemedText>
          <View style={styles.storeMeta}>
            <ThemedText type="small" style={{ color: colors.textSecondary }}>
              {item.rating} ({item.ratingCount} reviews)
            </ThemedText>
            <ThemedText type="small" style={[styles.metaDivider, { color: colors.textSecondary }]}>•</ThemedText>
            <ThemedText type="small" style={{ color: colors.textSecondary }}>
              {item.distance}
            </ThemedText>
          </View>
        </View>
        <View style={[styles.timeBadge, { backgroundColor: colors.background, borderColor: colors.primary }]}>
          <ThemedText type="small" style={{ color: colors.primary, fontWeight: '700' }}>
            {item.deliveryTime}
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
StoreCard.displayName = 'StoreCard';

export default function ShopScreen() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];
  const { user, session } = useAuth();

  const [coords, setCoords] = useState({ longitude: 77.6404, latitude: 12.9719 });
  const [stores, setStores] = useState<Store[]>([]);
  const [selectedStore, setSelectedStore] = useState<Store | null>(null);
  const [offerings, setOfferings] = useState<Offering[]>([]);
  const [cart, setCart] = useState<Record<string, number>>({});
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingOfferings, setIsLoadingOfferings] = useState(false);
  const [checkoutState, setCheckoutState] = useState<'idle' | 'success' | 'failure'>('idle');

  const authHeaders = useCallback((): Record<string, string> => {
    const headers: Record<string, string> = { 'Content-Type': 'application/json', Accept: 'application/json' };
    if (session?.access_token) {
      headers.Authorization = `Bearer ${session.access_token}`;
    }
    return headers;
  }, [session]);

  const fetchStores = async () => {
    try {
      const response = await fetch(
        `${appConfig.apiBaseUrl}/api/v1/discovery/providers?longitude=${coords.longitude}&latitude=${coords.latitude}&radius=10.0&type=PET_STORE`,
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
      if (mapped.length > 0 && !selectedStore) {
        setSelectedStore(mapped[0]);
      }
    } catch (error) {
      if (appConfig.allowDemoMode) {
        console.warn('Discovery API unavailable, using explicit demo store data', error);
        setStores(BACKUP_STORES);
      } else {
        console.warn('Discovery API unavailable', error);
        setStores([]);
      }
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (typeof navigator !== 'undefined' && navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          setCoords({
            longitude: position.coords.longitude,
            latitude: position.coords.latitude
          });
        },
        (error) => {
          console.warn("Failed to get geolocation, using Indiranagar default:", error.message);
        }
      );
    }
  }, []);

  useEffect(() => {
    fetchStores();
  }, [coords]);

  useEffect(() => {
    const fetchOfferings = async () => {
      if (!selectedStore) return;
      setIsLoadingOfferings(true);
      setCart({});
      setCheckoutState('idle');
      try {
        const response = await fetch(
          `${appConfig.apiBaseUrl}/api/v1/catalog/offerings?providerId=${selectedStore.id}`,
          { headers: { Accept: 'application/json' } }
        );
        if (!response.ok) throw new Error('Unable to load store products');
        const data = await response.json();
        const mapped = data
          .filter((item: any) => item.status !== 'INACTIVE' && item.stockQuantity !== 0)
          .map((item: any) => ({
            offeringId: item.offeringId,
            name: item.name,
            description: item.description,
            category: item.category,
            price: Number(item.price ?? 0),
            stockQuantity: item.stockQuantity,
          }));
        setOfferings(mapped);
      } catch (error) {
        console.warn('Catalog offerings unavailable', error);
        setOfferings([]);
      } finally {
        setIsLoadingOfferings(false);
      }
    };

    fetchOfferings();
  }, [selectedStore]);

  const selectedItems = offerings
    .map((offering) => ({ offering, quantity: cart[offering.offeringId] ?? 0 }))
    .filter((item) => item.quantity > 0);

  const cartTotal = selectedItems.reduce((total, item) => total + item.offering.price * item.quantity, 0);

  const updateQuantity = (offeringId: string, nextQuantity: number) => {
    setCart((current) => {
      const next = { ...current };
      if (nextQuantity <= 0) {
        delete next[offeringId];
      } else {
        next[offeringId] = nextQuantity;
      }
      return next;
    });
  };

  const loadDefaultAddress = async (): Promise<Address> => {
    if (appConfig.allowDemoMode) {
      return {
        addressId: DEMO_DELIVERY_ADDRESS_ID,
        label: 'Demo Home',
        line1: 'Explicit demo delivery address',
        city: 'Bangalore',
        pincode: '560038',
      };
    }

    const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/addresses/default`, {
      headers: authHeaders(),
    });
    if (!response.ok) {
      throw new Error('Add a default delivery address before checkout.');
    }
    return response.json();
  };

  const readApiError = async (response: Response): Promise<string | undefined> => {
    try {
      const payload = await response.json() as ApiErrorPayload;
      return payload.error;
    } catch {
      return undefined;
    }
  };

  const messageFromError = (error: unknown): string => {
    return error instanceof Error ? error.message : 'Please try again.';
  };

  const cancelOrderAfterPaymentFailure = async (orderId: string) => {
    const note = encodeURIComponent('Sandbox payment failed; reserved stock restored.');
    const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/orders/${orderId}/status?status=CANCELLED&note=${note}`, {
      method: 'PUT',
      headers: authHeaders(),
    });
    if (!response.ok) {
      const message = await readApiError(response);
      throw new Error(message || 'Payment failure was recorded, but order cancellation failed.');
    }
  };

  const checkout = async (success: boolean) => {
    if (!user || !selectedStore || selectedItems.length === 0 || checkoutState !== 'idle') return;

    setCheckoutState(success ? 'success' : 'failure');
    try {
      const address = await loadDefaultAddress();
      const orderResponse = await fetch(`${appConfig.apiBaseUrl}/api/v1/orders`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({
          customerId: user.id,
          providerId: selectedStore.id,
          deliveryAddressId: address.addressId,
          deliveryFee: 0,
          discountAmount: 0,
          items: selectedItems.map((item) => ({
            offeringId: item.offering.offeringId,
            quantity: item.quantity,
          })),
        }),
      });
      const orderPayload = await orderResponse.json() as OrderPayload & ApiErrorPayload;
      if (!orderResponse.ok) {
        throw new Error(orderPayload?.error || 'Order creation failed.');
      }

      const paymentResponse = await fetch(`${appConfig.apiBaseUrl}/api/v1/payments/transactions/result`, {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({
          userId: user.id,
          referenceId: orderPayload.orderId,
          transactionType: 'ORDER_PAYMENT',
          amount: orderPayload.totalAmount,
          gatewayTransactionId: `sandbox_${success ? 'captured' : 'failed'}_${orderPayload.orderId}`,
          success,
        }),
      });
      const paymentPayload = await paymentResponse.json() as ApiErrorPayload;
      if (!paymentResponse.ok) {
        throw new Error(paymentPayload?.error || 'Payment verification failed.');
      }

      if (!success) {
        await cancelOrderAfterPaymentFailure(orderPayload.orderId);
      }

      setCart({});
      Alert.alert(
        success ? 'Payment captured' : 'Payment failed',
        success
          ? `Order ${String(orderPayload.orderId).slice(0, 8)} is placed.`
          : `Failure event recorded and stock restored for order ${String(orderPayload.orderId).slice(0, 8)}.`
      );
    } catch (error: unknown) {
      Alert.alert('Checkout unavailable', messageFromError(error));
    } finally {
      setCheckoutState('idle');
    }
  };

  const renderStore = useCallback(({ item }: { item: Store }) => {
    return (
      <StoreCard
        item={item}
        colors={colors}
        isSelected={selectedStore?.id === item.id}
        onPress={() => setSelectedStore(item)}
      />
    );
  }, [colors, selectedStore?.id]);

  const keyExtractor = useCallback((item: Store) => item.id, []);

  return (
    <ThemedView style={[styles.container, { backgroundColor: colors.background }]}>
      <SafeAreaView style={styles.safeArea}>
        <View style={[styles.header, { borderBottomColor: colors.backgroundSelected }]}>
          <View>
            <ThemedText type="small" style={{ color: colors.textSecondary, fontWeight: '700', letterSpacing: 1 }}>
              DELIVERING TO
            </ThemedText>
            <View style={styles.locationRow}>
              <AppIcon name="location" color={colors.primary} size={16} />
              <ThemedText style={{ color: colors.text, fontWeight: '800' }}>
                Home — Indiranagar, Bangalore
              </ThemedText>
            </View>
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
                <View style={[styles.categoryIcon, { backgroundColor: colors.backgroundSelected }]}>
                  <AppIcon name={cat.icon as AppIconName} color={colors.primary} size={26} />
                </View>
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

          {selectedStore && (
            <View style={styles.checkoutSection}>
              <View style={styles.sectionHeader}>
                <ThemedText style={[styles.sectionTitleText, { color: colors.text }]}>
                  {selectedStore.name}
                </ThemedText>
                {isLoadingOfferings && <ActivityIndicator size="small" color={colors.primary} />}
              </View>

              <View style={styles.productList}>
                {!isLoadingOfferings && offerings.length === 0 ? (
                  <View style={[styles.emptyState, { borderColor: colors.textSecondary, backgroundColor: colors.backgroundElement }]}>
                    <ThemedText style={{ color: colors.text, fontWeight: '800' }}>
                      No live products available
                    </ThemedText>
                    <ThemedText type="small" style={{ color: colors.textSecondary }}>
                      This store needs active catalog offerings before checkout can run.
                    </ThemedText>
                  </View>
                ) : (
                  offerings.map((offering) => {
                    const quantity = cart[offering.offeringId] ?? 0;
                    return (
                      <View
                        key={offering.offeringId}
                        style={[styles.productCard, { backgroundColor: colors.backgroundElement, borderColor: colors.textSecondary }]}
                      >
                        <View style={styles.productInfo}>
                          <ThemedText style={[styles.productName, { color: colors.text }]}>
                            {offering.name}
                          </ThemedText>
                          {!!offering.description && (
                            <ThemedText type="small" style={{ color: colors.textSecondary }} numberOfLines={2}>
                              {offering.description}
                            </ThemedText>
                          )}
                          <ThemedText style={{ color: colors.primary, fontWeight: '800' }}>
                            Rs {offering.price.toFixed(2)}
                          </ThemedText>
                        </View>
                        <View style={styles.quantityControl}>
                          <TouchableOpacity
                            style={[styles.quantityButton, { borderColor: colors.primary }]}
                            onPress={() => updateQuantity(offering.offeringId, quantity - 1)}
                            disabled={quantity === 0}
                          >
                            <ThemedText style={{ color: colors.primary, fontWeight: '900' }}>-</ThemedText>
                          </TouchableOpacity>
                          <ThemedText style={[styles.quantityText, { color: colors.text }]}>
                            {quantity}
                          </ThemedText>
                          <TouchableOpacity
                            style={[styles.quantityButton, { borderColor: colors.primary }]}
                            onPress={() => updateQuantity(offering.offeringId, quantity + 1)}
                          >
                            <ThemedText style={{ color: colors.primary, fontWeight: '900' }}>+</ThemedText>
                          </TouchableOpacity>
                        </View>
                      </View>
                    );
                  })
                )}
              </View>

              {selectedItems.length > 0 && (
                <View style={[styles.cartBar, { backgroundColor: colors.backgroundElement, borderColor: colors.primary }]}>
                  <View>
                    <ThemedText style={{ color: colors.text, fontWeight: '800' }}>
                      {selectedItems.length} item{selectedItems.length === 1 ? '' : 's'} selected
                    </ThemedText>
                    <ThemedText type="small" style={{ color: colors.textSecondary }}>
                      Total Rs {cartTotal.toFixed(2)}
                    </ThemedText>
                  </View>
                  <View style={styles.checkoutActions}>
                    <TouchableOpacity
                      style={[styles.checkoutButton, { backgroundColor: colors.primary }]}
                      onPress={() => checkout(true)}
                      disabled={checkoutState !== 'idle'}
                    >
                      <ThemedText type="small" style={styles.checkoutButtonText}>Pay</ThemedText>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={[styles.failureButton, { borderColor: colors.textSecondary }]}
                      onPress={() => checkout(false)}
                      disabled={checkoutState !== 'idle'}
                    >
                      <ThemedText type="small" style={{ color: colors.text, fontWeight: '800' }}>Fail</ThemedText>
                    </TouchableOpacity>
                  </View>
                </View>
              )}
            </View>
          )}
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
    borderBottomWidth: 1,
  },
  locationRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
    marginTop: Spacing.half,
  },
  scrollContent: {
    paddingBottom: Spacing.six,
  },
  heroContainer: {
    marginHorizontal: Spacing.four,
    marginVertical: Spacing.four,
    height: 180,
    borderRadius: Radius.xl,
    borderWidth: 1,
    overflow: 'hidden',
    position: 'relative',
    ...Shadows.card,
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
    borderWidth: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: Spacing.two,
    ...Shadows.pressed,
  },
  categoryIcon: {
    width: 44,
    height: 44,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
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
    borderRadius: Radius.xl,
    borderWidth: 1,
    gap: Spacing.two,
    ...Shadows.card,
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
  checkoutSection: {
    marginTop: Spacing.two,
  },
  productList: {
    paddingHorizontal: Spacing.four,
    gap: Spacing.three,
  },
  emptyState: {
    borderWidth: 1,
    borderRadius: Radius.lg,
    padding: Spacing.four,
    gap: Spacing.one,
  },
  productCard: {
    borderWidth: 1,
    borderRadius: Radius.lg,
    padding: Spacing.three,
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 96,
    gap: Spacing.three,
    ...Shadows.pressed,
  },
  productInfo: {
    flex: 1,
    gap: Spacing.half,
  },
  productName: {
    fontSize: 15,
    fontWeight: '800',
  },
  quantityControl: {
    width: 104,
    height: 36,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  quantityButton: {
    width: 32,
    height: 32,
    borderRadius: 16,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  quantityText: {
    minWidth: 28,
    textAlign: 'center',
    fontWeight: '900',
  },
  cartBar: {
    marginHorizontal: Spacing.four,
    marginTop: Spacing.four,
    borderWidth: 1,
    borderRadius: Radius.lg,
    padding: Spacing.three,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
    ...Shadows.card,
  },
  checkoutActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  checkoutButton: {
    minWidth: 72,
    height: 38,
    borderRadius: 19,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: Spacing.three,
  },
  checkoutButtonText: {
    color: '#ffffff',
    fontWeight: '900',
  },
  failureButton: {
    minWidth: 58,
    height: 38,
    borderRadius: 19,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: Spacing.three,
  },
});
