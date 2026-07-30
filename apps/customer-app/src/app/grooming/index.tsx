import { useRouter } from 'expo-router';
import React, { useMemo, useState } from 'react';
import { FlatList, Pressable, StyleSheet, View } from 'react-native';

import { AppointmentBookingModal } from '@/components/care/AppointmentBookingModal';
import { FilterChip } from '@/components/foundation/primitives';
import { ThemedText } from '@/components/themed-text';
import { PrimaryButton } from '@/components/ui/primary-button';
import { ScreenHeader } from '@/components/ui/screen-header';
import { StatusBadge } from '@/components/ui/status-badge';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { useAppointmentBooking } from '@/hooks/useAppointmentBooking';

interface GroomingServiceItem {
  id: string;
  category: 'FULL' | 'HYGIENE' | 'DESHEDDING' | 'PUPPY';
  title: string;
  desc: string;
  price: number;
  duration: string;
  petApplicability: string;
  inclusions: string[];
}

const GROOMING_SERVICES_CATALOG: GroomingServiceItem[] = [
  {
    id: 'gs-1',
    category: 'FULL',
    title: 'Full Spa & Breed Haircut Package',
    desc: 'Complete luxury spa day with warm bath, blow dry, breed-standard haircut, ear cleaning & paw balm.',
    price: 1299,
    duration: '60 mins',
    petApplicability: 'All Dog Breeds & Sizes',
    inclusions: ['Warm Herbal Bath', 'Blow Dry & Fluff', 'Styling Haircut', 'Nail Trimming', 'Ear Cleaning', 'Paw Balm'],
  },
  {
    id: 'gs-2',
    category: 'HYGIENE',
    title: 'Basic Hygiene Bath & Tick Protection',
    desc: 'Anti-tick bath using natural neem extracts, sanitary area trimming, paw massage & nail buffing.',
    price: 699,
    duration: '40 mins',
    petApplicability: 'Small & Medium Dogs / Cats',
    inclusions: ['Anti-Tick Bath', 'Sanitary Trim', 'Paw Buffing', 'Scented Spray'],
  },
  {
    id: 'gs-3',
    category: 'DESHEDDING',
    title: 'De-Shedding & Undercoat Furminator',
    desc: 'Deep undercoat de-shedding treatment reducing 90% loose shedding fur for heavy coat breeds.',
    price: 899,
    duration: '45 mins',
    petApplicability: 'Golden Retrievers, Huskies, Labs',
    inclusions: ['De-Shedding Shampoo', 'Furminator Raking', 'Blow Out', 'Coat Conditioning'],
  },
  {
    id: 'gs-4',
    category: 'PUPPY',
    title: 'Puppy First Spa Experience',
    desc: 'Ultra-gentle tearless bath for puppies under 6 months, warm fluff dry, paw balm & puppy treat cup.',
    price: 499,
    duration: '30 mins',
    petApplicability: 'Puppies (2-6 Months)',
    inclusions: ['Tearless Shampoo', 'Gentle Fluff Dry', 'Nail Clip', 'Treat Cup'],
  },
];

export default function GroomingServicesScreen() {
  const router = useRouter();
  const theme = useTheme();
  const [filterCategory, setFilterCategory] = useState<string>('ALL');

  const {
    modalVisible,
    booking,
    openBookingModal,
    closeBookingModal,
    selectDate,
    selectSlot,
    selectPet,
    submitBooking,
  } = useAppointmentBooking();

  const filteredServices = useMemo(() => {
    if (filterCategory === 'ALL') return GROOMING_SERVICES_CATALOG;
    return GROOMING_SERVICES_CATALOG.filter((s) => s.category === filterCategory);
  }, [filterCategory]);

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <ScreenHeader title="Grooming Services & Spa" subtitle="Certified pet groomers in Tirupati" />

      {/* Filter Category Chips */}
      <View style={styles.filterRow}>
        <FilterChip label="All Services" selected={filterCategory === 'ALL'} onPress={() => setFilterCategory('ALL')} />
        <FilterChip label="Full Spa" selected={filterCategory === 'FULL'} onPress={() => setFilterCategory('FULL')} />
        <FilterChip label="Hygiene Baths" selected={filterCategory === 'HYGIENE'} onPress={() => setFilterCategory('HYGIENE')} />
        <FilterChip label="De-Shedding" selected={filterCategory === 'DESHEDDING'} onPress={() => setFilterCategory('DESHEDDING')} />
        <FilterChip label="Puppy Spa" selected={filterCategory === 'PUPPY'} onPress={() => setFilterCategory('PUPPY')} />
      </View>

      <FlatList
        data={filteredServices}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
        renderItem={({ item }) => (
          <View style={[styles.serviceCard, shadows.raised, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
            <View style={styles.cardHeader}>
              <View style={{ flex: 1 }}>
                <StatusBadge label={`⏱️ ${item.duration}`} color={theme.primary} />
                <ThemedText style={[styles.serviceTitle, { color: theme.text }]}>{item.title}</ThemedText>
                <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>🐾 {item.petApplicability}</ThemedText>
              </View>
              <ThemedText style={[styles.price, { color: theme.primary }]}>₹{item.price}</ThemedText>
            </View>

            <ThemedText style={[styles.desc, { color: theme.textSecondary }]}>{item.desc}</ThemedText>

            <View style={styles.inclusionGrid}>
              {item.inclusions.map((inc, idx) => (
                <StatusBadge key={idx} label={`✓ ${inc}`} color={theme.success} />
              ))}
            </View>

            <PrimaryButton
              label="Book Spa Session"
              onPress={() =>
                openBookingModal({
                  providerId: 'paws-bubbles-spa',
                  providerName: 'Paws & Bubbles Spa',
                  providerType: 'GROOMING_CENTER',
                  serviceName: item.title,
                  serviceFee: item.price,
                })
              }
            />
          </View>
        )}
      />

      <AppointmentBookingModal
        visible={modalVisible}
        booking={booking}
        onClose={closeBookingModal}
        onSelectDate={selectDate}
        onSelectSlot={selectSlot}
        onSelectPet={selectPet}
        onSubmit={submitBooking}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: spacing.x3 },
  filterRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2, marginBottom: spacing.x3 },
  listContent: { gap: spacing.x4, paddingBottom: spacing.x6 },
  serviceCard: { padding: spacing.x4, borderRadius: radii.card, borderWidth: 1, gap: spacing.x3 },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  serviceTitle: { ...typography.headline, fontSize: 16, fontWeight: '700', marginTop: 4 },
  price: { ...typography.headline, fontSize: 20, fontWeight: '900' },
  desc: { fontSize: 13, lineHeight: 18 },
  inclusionGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
});
