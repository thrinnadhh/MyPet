import React, { useCallback, useState, useEffect } from 'react';
import { StyleSheet, View, FlatList, TouchableOpacity, Platform, ActivityIndicator } from 'react-native';
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

const BACKUP_SALONS = [
  {
    id: '1',
    name: 'Pet Spa & Bath (Offline Demo)',
    services: 'Bath & Brush, Full Groom, Nail Trim',
    nextSlot: 'Today, 5:00 PM',
    distance: '1.4 km',
    rating: '4.9',
    ratingCount: '110',
  },
  {
    id: '2',
    name: 'Grooming Tails Center (Offline Demo)',
    services: 'Hair Trimming, Ear Cleaning, Massage',
    nextSlot: 'Tomorrow, 11:30 AM',
    distance: '1.9 km',
    rating: '4.7',
    ratingCount: '78',
  },
];

interface Salon {
  id: string;
  name: string;
  services: string;
  nextSlot: string;
  distance: string;
  rating: string;
  ratingCount: string;
}

const SalonCard = React.memo(({ item, colors }: { item: Salon, colors: any }) => {
  return (
    <TouchableOpacity 
      style={[
        styles.salonCard, 
        { 
          backgroundColor: colors.backgroundElement,
          borderColor: colors.textSecondary,
        }
      ]}
      activeOpacity={0.7}
    >
      <View style={styles.salonHeader}>
        <View style={{ flex: 1 }}>
          <ThemedText style={[styles.name, { color: colors.text }]}>
            {item.name}
          </ThemedText>
          <ThemedText type="small" style={{ color: colors.textSecondary, marginTop: Spacing.half }}>
            ✂️ {item.services}
          </ThemedText>
        </View>
        <View style={[styles.ratingBadge, { backgroundColor: colors.background, borderColor: colors.primary }]}>
          <ThemedText type="small" style={{ color: colors.text, fontWeight: '700' }}>
            ⭐ {item.rating}
          </ThemedText>
        </View>
      </View>

      <View style={[styles.slotContainer, { backgroundColor: colors.backgroundSelected }]}>
        <ThemedText type="small" style={{ color: colors.text, fontWeight: '500' }}>
          Next Available Slot:
        </ThemedText>
        <ThemedText style={{ color: colors.cta, fontWeight: '800' }}>
          📅 {item.nextSlot}
        </ThemedText>
      </View>

      <View style={styles.metaRow}>
        <ThemedText type="small" style={{ color: colors.textSecondary }}>
          📍 {item.distance} away
        </ThemedText>
        <TouchableOpacity 
          style={[styles.bookButton, { backgroundColor: colors.cta, borderColor: colors.text }]}
          activeOpacity={0.8}
        >
          <ThemedText type="small" style={{ color: '#ffffff', fontWeight: '800' }}>
            Book Spa
          </ThemedText>
        </TouchableOpacity>
      </View>
    </TouchableOpacity>
  );
});

export default function GroomScreen() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];

  const [salons, setSalons] = useState<Salon[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const fetchSalons = async () => {
    try {
      // Fetch active providers of type GROOMING_CENTER near Indiranagar, Bangalore
      const response = await fetch(
        `${API_BASE_URL}/api/v1/discovery/providers?longitude=77.6404&latitude=12.9719&radius=10.0&type=GROOMING_CENTER`,
        { headers: { 'Accept': 'application/json' } }
      );
      if (!response.ok) throw new Error('Network response not ok');
      const data = await response.json();
      
      const mapped = data.map((p: any) => ({
        id: p.providerId,
        name: p.name,
        services: p.description ? p.description.substring(0, 35) : 'General Styling, Bathing, Nail Care',
        nextSlot: 'Today, 6:00 PM', // Fallback slot placeholder
        distance: `${p.distanceKm.toFixed(1)} km`,
        rating: p.ratingAvg ? p.ratingAvg.toFixed(1) : '0.0',
        ratingCount: p.ratingCount ? p.ratingCount.toString() : '0',
      }));
      setSalons(mapped);
    } catch (error) {
      console.warn('Discovery API unavailable for groom, falling back to mock data', error);
      setSalons(BACKUP_SALONS);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchSalons();
  }, []);

  const renderSalon = useCallback(({ item }: { item: Salon }) => {
    return <SalonCard item={item} colors={colors} />;
  }, [colors]);

  const keyExtractor = useCallback((item: Salon) => item.id, []);

  return (
    <ThemedView style={[styles.container, { backgroundColor: colors.background }]}>
      <SafeAreaView style={styles.safeArea}>
        <View style={[styles.header, { borderBottomColor: colors.backgroundSelected }]}>
          <ThemedText type="subtitle" style={{ color: colors.text, fontWeight: '800' }}>
            Book a Groomer
          </ThemedText>
          <View style={styles.headerSub}>
            <ThemedText type="small" style={{ color: colors.textSecondary, flex: 1 }}>
              Professional styling, bath, and hygiene treatments
            </ThemedText>
            {isLoading && <ActivityIndicator size="small" color={colors.primary} />}
          </View>
        </View>

        <FlatList
          data={salons}
          renderItem={renderSalon}
          keyExtractor={keyExtractor}
          contentContainerStyle={styles.listContent}
        />
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
  headerSub: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: Spacing.one,
  },
  listContent: {
    padding: Spacing.four,
    gap: Spacing.four,
    paddingBottom: Spacing.six,
  },
  salonCard: {
    padding: Spacing.four,
    borderRadius: 24,
    borderWidth: 3,
    gap: Spacing.three,
    // Claymorphic shadow
    shadowColor: '#000',
    shadowOffset: { width: 4, height: 4 },
    shadowOpacity: 0.12,
    shadowRadius: 0,
    elevation: 3,
  },
  salonHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  name: {
    fontSize: 16,
    fontWeight: '800',
  },
  ratingBadge: {
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.half,
    borderRadius: 12,
    borderWidth: 1,
  },
  slotContainer: {
    padding: Spacing.three,
    borderRadius: 16,
    gap: Spacing.one,
  },
  metaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: Spacing.one,
  },
  bookButton: {
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two,
    borderRadius: 16,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: 100,
    minHeight: 44, // Touch target height
  },
});
