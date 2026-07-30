import { useRouter } from 'expo-router';
import React, { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { AppIcon, type AppIconName } from '@/components/app-icon';
import { ThemedText } from '@/components/themed-text';
import { BannerCarousel } from '@/components/ui/banner-carousel';
import { PrimaryButton } from '@/components/ui/primary-button';
import { ScreenHeader } from '@/components/ui/screen-header';
import { StatusBadge } from '@/components/ui/status-badge';
import { PROMO_BANNERS } from '@/constants/content';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/hooks/use-theme';
import { useTranslation } from '@/i18n';
import { fetchBanners, type PromoBanner } from '@/services/content';
import { appConfig } from '@/utils/app-config';

interface StoryCategory {
  id: string;
  titleKey: string;
  icon: AppIconName;
  route: string;
}

const STORY_CATEGORIES: StoryCategory[] = [
  { id: 'food', titleKey: 'shop.categories.food', icon: 'cart', route: '/category/food' },
  { id: 'grooming', titleKey: 'home.grooming', icon: 'groom', route: '/category/grooming' },
  { id: 'hospitals', titleKey: 'home.vetCare', icon: 'medical', route: '/category/hospitals' },
  { id: 'vaccinations', titleKey: 'home.vaccinationReminders', icon: 'sparkle', route: '/category/vaccinations' },
  { id: 'toys', titleKey: 'shop.categories.toys', icon: 'paw', route: '/category/toys' },
  { id: 'treats', titleKey: 'shop.categories.accessories', icon: 'store', route: '/category/treats' },
  { id: 'waste', titleKey: 'shop.categories.pharmacy', icon: 'shield', route: '/category/waste' },
  { id: 'furniture', titleKey: 'home.services', icon: 'card', route: '/category/furniture' },
  { id: 'travel', titleKey: 'home.guides', icon: 'history', route: '/category/travel' },
];

interface HospitalItem {
  id: string;
  name: string;
  rating: string;
  ratingCount: string;
  distance: string;
  isEmergency: boolean;
  doctors: string;
  services: string[];
}

const FEATURED_HOSPITALS: HospitalItem[] = [
  {
    id: 'hosp-1',
    name: 'PetCare & Wellness Hospital',
    rating: '4.9 ★',
    ratingCount: '(180+ reviews)',
    distance: '1.2 km',
    isEmergency: true,
    doctors: 'Dr. Srinivas, DVM (Surgeon)',
    services: ['24/7 ICU', 'Vaccination', 'Surgery', 'Pet Dental'],
  },
  {
    id: 'hosp-2',
    name: 'City Pet Hospital Tirupati',
    rating: '4.8 ★',
    ratingCount: '(140+ reviews)',
    distance: '2.4 km',
    isEmergency: false,
    doctors: 'Dr. Ananya, MVSc (Medicine)',
    services: ['OPD Consultation', 'Blood Test Lab', 'Ultrasound'],
  },
];

interface GroomerItem {
  id: string;
  name: string;
  rating: string;
  distance: string;
  priceRange: string;
  services: string[];
}

const FEATURED_GROOMERS: GroomerItem[] = [
  {
    id: 'groom-1',
    name: 'Paws & Bubbles Spa',
    rating: '4.9 ★',
    distance: '0.8 km',
    priceRange: '₹499 - ₹1,499',
    services: ['Full Bath & Blow Dry', 'Haircut', 'Nail Trimming', 'Ear Cleaning'],
  },
  {
    id: 'groom-2',
    name: 'Fluffy Tails Salon',
    rating: '4.7 ★',
    distance: '1.9 km',
    priceRange: '₹399 - ₹1,199',
    services: ['Puppy Bath', 'De-Shedding', 'Tick Treatment'],
  },
];

interface GuideItem {
  id: string;
  slug: string;
  title: string;
  subtitle: string;
  readTime: string;
  badge: string;
}

const PET_CARE_GUIDES: GuideItem[] = [
  {
    id: 'guide-1',
    slug: 'puppy-nutrition-0-2-mo',
    title: 'Puppy Nutrition Guide (0 - 2 Months)',
    subtitle: 'Essential weaning steps, mother milk substitutes, and digestive health tips.',
    readTime: '3 min read',
    badge: 'Nutrition',
  },
  {
    id: 'guide-2',
    slug: 'puppy-growth-2-12-mo',
    title: 'Puppy Growth Tracker (2 - 12 Months)',
    subtitle: 'Weight milestones, teething relief, and age-appropriate exercise routines.',
    readTime: '4 min read',
    badge: 'Growth',
  },
  {
    id: 'guide-3',
    slug: 'coat-skin-health',
    title: 'Coat & Skin Health Masterclass',
    subtitle: 'How to prevent seasonal shedding, hot spots, and maintain a glossy coat.',
    readTime: '5 min read',
    badge: 'Dermatology',
  },
];

export default function HomeScreen() {
  const router = useRouter();
  const theme = useTheme();
  const { user } = useAuth();
  const { t } = useTranslation();

  const [searchQuery, setSearchQuery] = useState('');
  const [banners, setBanners] = useState<PromoBanner[]>(
    appConfig.allowDemoMode
      ? PROMO_BANNERS.map((b) => ({ id: b.id, title: b.title, subtitle: b.subtitle, accent: b.accent, durationSec: b.durationSec }))
      : [],
  );

  useEffect(() => {
    void fetchBanners().then(setBanners).catch(() => undefined);
  }, []);

  const firstName = useMemo(() => {
    if (typeof user?.user_metadata?.full_name === 'string') {
      return user.user_metadata.full_name.split(' ')[0];
    }
    return t('common.petParent');
  }, [user, t]);

  return (
    <ScrollView style={[styles.container, { backgroundColor: theme.background }]} contentContainerStyle={styles.contentContainer}>
      {/* Top Header */}
      <ScreenHeader
        title="PetStore"
        subtitle={`Welcome back, ${firstName}`}
        trailing={<AppIcon name="paw" color={theme.primary} size={24} />}
      />

      {/* Location Bar */}
      <View style={[styles.locationBar, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
        <AppIcon name="location" color={theme.primary} size={18} />
        <View style={styles.flexOne}>
          <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>Delivering to</ThemedText>
          <ThemedText style={{ fontSize: 14, fontWeight: '700', color: theme.text }}>Tirupati, Andhra Pradesh</ThemedText>
        </View>
      </View>

      {/* Search Field */}
      <View style={[styles.searchField, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
        <AppIcon name="search" color={theme.textSecondary} size={18} />
        <TextInput
          value={searchQuery}
          onChangeText={setSearchQuery}
          placeholder="Search pet food, vet care, grooming spas..."
          placeholderTextColor={theme.textSecondary}
          style={[styles.searchInput, { color: theme.text }]}
          returnKeyType="search"
          onSubmitEditing={() => {
            if (searchQuery.trim()) {
              router.push(`/category/food` as never);
            }
          }}
        />
      </View>

      {/* Category Stories (Swiggy Circular Row) */}
      <View style={styles.sectionMargin}>
        <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>Explore Services & Products</ThemedText>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.storiesScroll}>
          {STORY_CATEGORIES.map((cat) => (
            <Pressable
              key={cat.id}
              onPress={() => router.push(cat.route as never)}
              accessibilityRole="button"
              accessibilityLabel={t(cat.titleKey)}
              style={({ pressed }) => [styles.storyItem, pressed && styles.pressed]}
            >
              <View style={[styles.storyCircle, { backgroundColor: theme.primarySoft, borderColor: theme.primary }]}>
                <AppIcon name={cat.icon} color={theme.primary} size={24} />
              </View>
              <ThemedText numberOfLines={1} style={[styles.storyText, { color: theme.text }]}>
                {t(cat.titleKey)}
              </ThemedText>
            </Pressable>
          ))}
        </ScrollView>
      </View>

      {/* High-Impact Promotional Banners */}
      <View style={styles.sectionMargin}>
        <BannerCarousel banners={banners} onPress={() => router.push('/guides' as never)} />
      </View>

      {/* Hospitals & Vet Care Section */}
      <View style={styles.sectionMargin}>
        <View style={styles.sectionHeaderRow}>
          <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>Hospitals & Vet Care Near You</ThemedText>
          <Pressable onPress={() => router.push('/vet' as never)}>
            <ThemedText style={{ color: theme.cta, fontWeight: '700', fontSize: 13 }}>View All Vets</ThemedText>
          </Pressable>
        </View>
        <View style={styles.cardsColumn}>
          {FEATURED_HOSPITALS.map((hosp) => (
            <Pressable
              key={hosp.id}
              onPress={() => router.push(`/hospital/${hosp.id}` as never)}
              style={({ pressed }) => [
                styles.entityCard,
                Shadows.card,
                { backgroundColor: theme.backgroundElement, borderColor: theme.border },
                pressed && styles.pressed,
              ]}
            >
              <View style={styles.cardHeaderRow}>
                <View style={styles.flexOne}>
                  <View style={styles.titleBadgeRow}>
                    <ThemedText style={[styles.entityName, { color: theme.text }]}>{hosp.name}</ThemedText>
                    {hosp.isEmergency && <StatusBadge label="24/7 ICU" color={theme.danger} />}
                  </View>

                  <ThemedText style={{ fontSize: 13, color: theme.textSecondary, marginTop: 2 }}>
                    <ThemedText style={{ color: theme.warning, fontWeight: '700' }}>{hosp.rating}</ThemedText> {hosp.ratingCount} • {hosp.distance}
                  </ThemedText>
                </View>
              </View>
              <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>👨‍⚕️ {hosp.doctors}</ThemedText>
              <View style={styles.tagsRow}>
                {hosp.services.map((srv, idx) => (
                  <View key={idx} style={[styles.miniTag, { backgroundColor: theme.muted }]}>
                    <ThemedText style={{ fontSize: 11, fontWeight: '600', color: theme.text }}>{srv}</ThemedText>
                  </View>
                ))}
              </View>
              <View style={styles.cardFooter}>
                <PrimaryButton label="Book Appointment" onPress={() => router.push(`/hospital/${hosp.id}` as never)} />
              </View>
            </Pressable>
          ))}
        </View>
      </View>

      {/* Grooming Spas Section */}
      <View style={styles.sectionMargin}>
        <View style={styles.sectionHeaderRow}>
          <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>Grooming Spas Near You</ThemedText>
          <Pressable onPress={() => router.push('/groom' as never)}>
            <ThemedText style={{ color: theme.cta, fontWeight: '700', fontSize: 13 }}>View Spas</ThemedText>
          </Pressable>
        </View>
        <View style={styles.cardsColumn}>
          {FEATURED_GROOMERS.map((groom) => (
            <Pressable
              key={groom.id}
              onPress={() => router.push(`/groomer/${groom.id}` as never)}
              style={({ pressed }) => [
                styles.entityCard,
                Shadows.card,
                { backgroundColor: theme.backgroundElement, borderColor: theme.border },
                pressed && styles.pressed,
              ]}
            >
              <View style={styles.cardHeaderRow}>
                <View style={styles.flexOne}>
                  <ThemedText style={[styles.entityName, { color: theme.text }]}>{groom.name}</ThemedText>
                  <ThemedText style={{ fontSize: 13, color: theme.textSecondary, marginTop: 2 }}>
                    <ThemedText style={{ color: theme.warning, fontWeight: '700' }}>{groom.rating}</ThemedText> • {groom.distance} • {groom.priceRange}
                  </ThemedText>
                </View>
              </View>
              <View style={styles.tagsRow}>
                {groom.services.map((srv, idx) => (
                  <View key={idx} style={[styles.miniTag, { backgroundColor: theme.primarySoft }]}>
                    <ThemedText style={{ fontSize: 11, fontWeight: '700', color: theme.primary }}>{srv}</ThemedText>
                  </View>
                ))}
              </View>
              <View style={styles.cardFooter}>
                <PrimaryButton label="Book Session" onPress={() => router.push(`/groomer/${groom.id}` as never)} />
              </View>
            </Pressable>
          ))}
        </View>
      </View>

      {/* Pet Care Guides */}
      <View style={[styles.sectionMargin, { marginBottom: Spacing.four }]}>
        <View style={styles.sectionHeaderRow}>
          <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>Pet Care Guides & Health Tips</ThemedText>
          <Pressable onPress={() => router.push('/guides' as never)}>
            <ThemedText style={{ color: theme.cta, fontWeight: '700', fontSize: 13 }}>All Guides</ThemedText>
          </Pressable>
        </View>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.guidesScroll}>
          {PET_CARE_GUIDES.map((guide) => (
            <Pressable
              key={guide.id}
              onPress={() => router.push(`/guide/${guide.slug}` as never)}
              style={({ pressed }) => [
                styles.guideCard,
                Shadows.card,
                { backgroundColor: theme.backgroundElement, borderColor: theme.border },
                pressed && styles.pressed,
              ]}
            >
              <View style={styles.guideBadgeRow}>
                <StatusBadge label={guide.badge} color={theme.success} />
                <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>{guide.readTime}</ThemedText>
              </View>

              <ThemedText style={[styles.guideTitle, { color: theme.text }]} numberOfLines={2}>
                {guide.title}
              </ThemedText>
              <ThemedText style={{ fontSize: 12, color: theme.textSecondary }} numberOfLines={2}>
                {guide.subtitle}
              </ThemedText>
            </Pressable>
          ))}
        </ScrollView>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  contentContainer: { padding: Spacing.three, gap: Spacing.two },
  flexOne: { flex: 1 },
  pressed: { opacity: 0.85 },
  locationBar: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two, padding: Spacing.two, borderRadius: Radius.md, borderWidth: 1 },
  searchField: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two, paddingHorizontal: Spacing.two, borderRadius: Radius.md, borderWidth: 1, height: 44 },
  searchInput: { flex: 1, height: 44, fontSize: 14 },
  sectionMargin: { marginTop: Spacing.three },
  sectionTitle: { fontSize: 17, fontWeight: '700' },
  sectionHeaderRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: Spacing.two },
  storiesScroll: { gap: Spacing.three, paddingVertical: Spacing.one },
  storyItem: { width: 72, alignItems: 'center', gap: Spacing.one },
  storyCircle: { width: 60, height: 60, borderRadius: 30, borderWidth: 1.5, alignItems: 'center', justifyContent: 'center' },
  storyText: { fontSize: 11, textAlign: 'center', fontWeight: '600' },
  cardsColumn: { gap: Spacing.three, marginTop: Spacing.one },
  entityCard: { borderWidth: 1, borderRadius: Radius.lg, padding: Spacing.three, gap: Spacing.two },
  cardHeaderRow: { flexDirection: 'row', justifyContent: 'space-between' },
  titleBadgeRow: { flexDirection: 'row', alignItems: 'center', gap: Spacing.one, flexWrap: 'wrap' },
  entityName: { fontSize: 16, fontWeight: '700' },
  tagsRow: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.one },
  miniTag: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: Radius.sm },
  cardFooter: { marginTop: Spacing.one },
  guidesScroll: { gap: Spacing.three, paddingVertical: Spacing.one },
  guideCard: { width: 240, borderWidth: 1, borderRadius: Radius.lg, padding: Spacing.three, gap: Spacing.two },
  guideBadgeRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  guideTitle: { fontWeight: '700', fontSize: 14 },
});
