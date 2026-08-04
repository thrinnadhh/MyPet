import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import React, { useEffect, useMemo, useState } from 'react';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  Pressable,
  ScrollView,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';

import { AppIcon, type AppIconName } from '@/components/app-icon';
import { LocationModal, NotifyCityModal } from '@/components/location-modal';
import { ThemedText } from '@/components/themed-text';
import { BannerCarousel } from '@/components/ui/banner-carousel';
import { PROMO_BANNERS } from '@/constants/content';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';
import { useLocation } from '@/context/LocationContext';
import { useTheme } from '@/hooks/use-theme';
import { useTranslation } from '@/i18n';
import { fetchBanners, fetchGuides, type GuideArticle, type PromoBanner } from '@/services/content';

const CARD_WIDTH = 236;
const CARD_GAP = 12;

interface ShortcutItem {
  id: string;
  label: string;
  icon: AppIconName;
  route: string;
}

interface CategoryItem {
  id: string;
  label: string;
  image: string;
  route: string;
}

interface DiscoveryCardItem {
  id: string;
  title: string;
  subtitle: string;
  image: string;
  rating?: string;
  meta: string;
  metaIcon: AppIconName;
  route: string;
  likes?: number;
  authorName?: string;
  companyName?: string;
}

const QUICK_ACTIONS: ShortcutItem[] = [
  { id: 'favourites', label: 'Favourites', icon: 'heart', route: '/favourites' },
  { id: 'orders', label: 'Orders', icon: 'document', route: '/(tabs)/orders' },
  { id: 'new-arrivals', label: 'New Arrivals', icon: 'sparkle', route: '/category/new-arrivals' },
];

const HEALTH_ACTIONS: ShortcutItem[] = [
  { id: 'vaccinations', label: 'Vaccinations & Tablets', icon: 'medical', route: '/health/vaccinations' },
  { id: 'reports', label: 'Reports', icon: 'document', route: '/health/reports' },
  { id: 'appointments', label: 'Appointments', icon: 'calendar', route: '/appointments' },
];

const CATEGORIES: CategoryItem[] = [
  {
    id: 'food',
    label: 'Food & Nutrition',
    route: '/category/food',
    image: 'https://lh3.googleusercontent.com/aida/AP1WRLtm0J5MuRuW4w4olkkflcjkSX-9bxCk23-GrLzJRiqZ3_Zhhy2q5eirMvjXv9zBuFs-CX_wO-6hy7L6dSiuCztDtcMr-ivqfjh1miBnYaJCBqeENN0uVuip23wlVEFdtlPGQnFLRX3DX5fIQbX-zxuI7suRgPsZfrgEES97W4eShI8nPsgFqE4D5gfrllobU8d8bK_gIhaDhfBFjG_xFI4evQH34o8Zj-nfArMBARfijbV2pPvCBxZhVNc',
  },
  {
    id: 'grooming',
    label: 'Grooming Services',
    route: '/category/grooming',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuCLDcsiQzTJ35jcCpCNHSC0CPGtsB--0Xdb-LVHpAoteDtktABgPSTQMMPGcfAgwvMEa22Twz_PWoxMANUVHDlfmcOgn53ytuQl7eHMq2kD2oBJX8mNowGEJjxAIHOdSyARgHYwDg6TFxoXYoYnVogC8c3QqEQxzKXQHBhPxhv1VK3mWc1o8kwr-eyteIwsACN_yi3C9LZwRdXcVVbk_7sQFr6t-JFQsx7yaIuZTVNVZeEEPbhBBDvdW00lu99huqxwo4ClJpdhVnY',
  },
  {
    id: 'hospitals',
    label: 'Hospitals & Care',
    route: '/category/hospitals',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuB5z2g3IHBH5gz3oR6QqQl6XDHPXhUN4b482F_jJ_bPPyD_OnMLA-gnGMdyNXz7v-jaFvfwW2nZgw5KX9NdTC9YFXzkoNU1GbbdvagvvRSdasnjCk7_elM2rSKuGbzmVkaxSgZdguhWDkjbumkNBU7ppWfcO0BHE2XmNjU2nF4ild_5dbokZ4jck5r_IU4B0KaW73XkasFSbOjZBQL9xAMihZ9AWDirYg99ysJl5RAKEqRVNyjhtIeMcQILmQFS97_A-HBozb9Kz-k',
  },
  {
    id: 'treats',
    label: 'Treats & Chews',
    route: '/category/treats',
    image: 'https://lh3.googleusercontent.com/aida/AP1WRLu2j9XUv_Re8vRJWJIq3otI7IoX2NgLk7u6dz-Q8YcKZM56ZhlEEG2hhVjR_8a8TLk1hk-4Tl9pMOlZyJGEzTBYJn_bkhNE9uLeijZ6EWFbm_jLi4gI6TBl8Gw26ZsyNvJfA59F0JWB29hKlzMIleF_-IFt7EarCxyfY7nXTlogfRnnfkUY63uBoXugR7m67gBg3tiS5d5irmOdJvPRSbNWfAdde8rkOop9HKp5a_RfrM4tCAPcR1ICVDY',
  },
  {
    id: 'toys',
    label: 'Toys & Enrichment',
    route: '/category/toys',
    image: 'https://lh3.googleusercontent.com/aida/AP1WRLtxvZTVCFJV_VWo3wS_Re5Qq0invY-1glo_OjI0J4hnMPR5MsjoVIKEEB49TttNQJp72uxI1VSi0wNPgKqXEtHAdcpBZuG27EMpH_1RfaFojtTrIRgPUv35DFFVg-9ecM4jajxDGRfXVSmtgvzxmHysSJYFIWRJ2SMSFm1lNrv4u-Ghvt7H5oCHglK5OaRJ6K3T0Op6_cCx801FfzvuWPMT2d8gdn5EVH4KwwsSeKMDpOeSeMGimOg2jHY',
  },
  {
    id: 'travel',
    label: 'Travel & Apparel',
    route: '/category/travel',
    image: 'https://lh3.googleusercontent.com/aida/AP1WRLtGMKTRDD97W5R-veADg2j3lfveNZ2oerY0hevdFnQgDaTJbt99ZIlpABlOxwf1kGlPaGHbVZr7PUJCnX_EqmzDgRXFImh97MiasRQu_kNuciIDGp_S8g_B8MuPXvZLKO5kwtSueMsWt0dFZ7G13zv6VLEpf_WPg54CPQsq_GGFnu6K70UjqfxgalRLTUqFmz49cSXfKPKZstWqH44WXlNCUPSDxaZcoGPJPblVe195H6OdCKuPMTGxRTo',
  },
];

const FILTERS = ['All', 'Dry Food', 'Wet Food', 'Puppy', 'Adult', 'Senior'];

const FOOD_AND_NUTRITION: DiscoveryCardItem[] = [
  {
    id: 'shop-1',
    title: 'The Posh Paws',
    subtitle: 'Luxury Accessories & Food',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuDP0Bso5NdUTuYfQmdxjGfrU18IgCERDgWEobR1RzRKk0phJmTjprXxrZ2e6MSiBzYHNllyH_O29w9XG-5RgKbo7sx9KygQhzOHoPj74CO-x1GqUujm5wEjiMN462Jd5zLvEnUDGElVK2fb7LGOI7ziuz25lE42roHK7gbnIVfpE7H3TXg8vXkDQ8iQBLfj3YiIarAthoLCqep7tQ7gY0S0wJwunX30RA3VqJ-IO10PEIcc7YJiBsxCcI9DaozwNmO6Uu30bknpLIc',
    rating: '4.8',
    meta: '20 mins',
    metaIcon: 'clock',
    route: '/category/food',
  },
  {
    id: 'shop-2',
    title: 'Healthy Hounds Pantry',
    subtitle: 'Organic & Raw Diet Specialist',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuASfM4FE2gFaBN0OhgeTBOjES2tuJHOL72sgaRGgO-tENBpVYDnBud9une2vRaHplLerDL25aSx0vh9cJz69DTuFIW1egWGJvltzY6_RQn4GF_mmvas_iU801N87_y6-JFB3H3zQFxvQwyYXfEgQuV0F3BI5heqbe6Fn_zOitcCR1esBTCKNBI4NVMHkzRxgVe8mC0fGuNb2htuR3f91sz8odhN4x_vfPmxh9MBA5fDQuWEqnrBtDvcw7nJsN8Qi7g7AzJId8',
    rating: '4.5',
    meta: '25 mins',
    metaIcon: 'clock',
    route: '/category/food',
  },
  {
    id: 'shop-3',
    title: 'PetCare Pharmacy',
    subtitle: 'Nutrition, supplements & wellness',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuD6AeLhg79H-Qs95EgMOGxSG2HjJa3jlosvBXsvXE5r1rnCSeml3ETIaLhK2r2kkY8l014vbMq3CO9DvXCeF_udak6kApRBpmmenOLebPTGJDX0lNUEn2c_IpOj50T6QSmzoOy6xOPOOdMY2evfXi4nMgs9TZbCWytwpJvN8ZpQmxIi2hs9iM4G6ZZ-KAAlvmuhSUGUnp0BytQTvH3Yv5djAj6xrWVYt-TSyCg82T7EA5fDQuWEqnrBtDvcw7nJsN8Qi7g7AzJId8',
    rating: '4.7',
    meta: '30 mins',
    metaIcon: 'clock',
    route: '/category/food',
  },
];

const GROOMING_NEARBY: DiscoveryCardItem[] = [
  {
    id: 'groom-1',
    title: 'Paws & Bubbles Spa',
    subtitle: 'Luxury Grooming & Styling',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuCLDcsiQzTJ35jcCpCNHSC0CPGtsB--0Xdb-LVHpAoteDtktABgPSTQMMPGcfAgwvMEa22Twz_PWoxMANUVHDlfmcOgn53ytuQl7eHMq2kD2oBJX8mNowGEJjxAIHOdSyARgHYwDg6TFxoXYoYnVogC8c3QqEQxzKXQHBhPxhv1VK3mWc1o8kwr-eyteIwsACN_yi3C9LZwRdXcVVbk_7sQFr6t-JFQsx7yaIuZTVNVZeEEPbhBBDvdW00lu99huqxwo4ClJpdhVnY',
    rating: '4.8',
    meta: '0.8 km away',
    metaIcon: 'location',
    route: '/groomer/groom-1',
  },
  {
    id: 'groom-2',
    title: 'The Grooming Room',
    subtitle: 'Professional Pet Grooming',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuA8-OnbYbH6ervRc4iDKjRxKLt6mO6wKvK8uA3YF7QqP3s6MzG7DILE7cEzhjoG1QhhOujkvk6kROOkrlX_HL2AqoacPYkIXR9PWO8eOCuNrkd24m2rUzV3v_SsO_Tt-eng-sTQpDJE-rHj2Ksx8Qw8uGaUZB-6jpIsSfhmFTkAVrxBXvue6givMDI98jjybom420pH3sbIUeml2Io6RygcKD0Xk279U3oRRXPXcZSjpIgZMptmDBLqWFDLWZce7mlSIJJ-aZXYgOs',
    rating: '4.6',
    meta: '1.9 km away',
    metaIcon: 'location',
    route: '/groomer/groom-2',
  },
];

const HOSPITALS_AND_CARE: DiscoveryCardItem[] = [
  {
    id: 'hosp-1',
    title: 'City Pet Hospital',
    subtitle: 'Emergency & General Care',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuB5z2g3IHBH5gz3oR6QqQl6XDHPXhUN4b482F_jJ_bPPyD_OnMLA-gnGMdyNXz7v-jaFvfwW2nZgw5KX9NdTC9YFXzkoNU1GbbdvagvvRSdasnjCk7_elM2rSKuGbzmVkaxSgZdguhWDkjbumkNBU7ppWfcO0BHE2XmNjU2nF4ild_5dbokZ4jck5r_IU4B0KaW73XkasFSbOjZBQL9xAMihZ9AWDirYg99ysJl5RAKEqRVNyjhtIeMcQILmQFS97_A-HBozb9Kz-k',
    rating: '4.9',
    meta: '1.2 km away',
    metaIcon: 'location',
    route: '/hospital/hosp-1',
  },
  {
    id: 'hosp-2',
    title: 'PetCare Wellness Center',
    subtitle: 'Specialized Veterinary Services',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuDYKJG83KcL1yNh-w9EyZpJJHjgLNuCQIwoxOy4oxO9897FscAQj38VOtNLWetFhV0UcGvbpvYFMlMNisc1N7np5cd_0qaZcKNYGqSiaBeZDsParI4mxGmOxyw6mMU4RnJGckXQcWZv9-HU08XqZzmVBHFvSqAiJicfb1bes3T14Iv-yfAJJflwwAUl-CIk_HMUPFxRcCa1f_RtBSqklHewyESVhtAzbgZgixnF5Psbz6VhIkMXq-m2KovO2SB4RSYINa5KONreaS8',
    rating: '4.7',
    meta: '2.5 km away',
    metaIcon: 'location',
    route: '/hospital/hosp-2',
  },
];

const GUIDES: DiscoveryCardItem[] = [
  {
    id: 'guide-1',
    title: 'Puppy Nutrition (0–2 mo)',
    subtitle: 'Dietary guide',
    image: HOSPITALS_AND_CARE[0].image,
    meta: '3 min read',
    metaIcon: 'document',
    route: '/guides',
    likes: 128,
    authorName: 'Dr. Ananya Rao',
    companyName: 'City Pet Hospital',
  },
  {
    id: 'guide-2',
    title: 'Puppy Growth (2–12 mo)',
    subtitle: 'Milestone tracking',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuCwFpaCtKFAmO7u4LFhgbudFkcsabYvyZjr-jxBxNF4vzO99nSWyB1ctF35zFChTfG2kDD7lTnLyX3yqYMgslq2MKyTOED_O2D5wQ2IngywdmQOVbanZTXPwBa9bpdjzT3ViUDhNrkfbU-HKFLtGNI_9NRmi_NOj_ELxWiZJ-ZMpz9hOQhsTHA133HKuZbZbwUsiQeLUzzEbOmVrpylOe0dkYv5ib-0mHIRkyZllWTFC9L8NfD9mD0yqX9w1ck6HEsH3Z0tUxdpdpQ',
    meta: '4 min read',
    metaIcon: 'document',
    route: '/guides',
    likes: 94,
    authorName: 'Dr. Vivek Sharma',
    companyName: 'PetCare Wellness Center',
  },
  {
    id: 'guide-3',
    title: 'Coat & Skin Health',
    subtitle: 'Seasonal care essentials',
    image: GROOMING_NEARBY[0].image,
    meta: '5 min read',
    metaIcon: 'document',
    route: '/guides',
    likes: 76,
    authorName: 'Meera Reddy',
    companyName: 'Paws & Bubbles Spa',
  },
];

function SectionHeading({ title, actionLabel, onAction }: { title: string; actionLabel?: string; onAction?: () => void }) {
  const theme = useTheme();
  return (
    <View style={styles.sectionHeading}>
      <ThemedText style={[styles.sectionTitle, { color: theme.text }]}>{title}</ThemedText>
      {actionLabel && onAction ? (
        <Pressable onPress={onAction} hitSlop={8}>
          <ThemedText style={[styles.sectionAction, { color: theme.cta }]}>{actionLabel}</ThemedText>
        </Pressable>
      ) : null}
    </View>
  );
}

function DiscoveryCard({ item }: { item: DiscoveryCardItem }) {
  const router = useRouter();
  const theme = useTheme();

  return (
    <Pressable
      onPress={() => router.push(item.route as never)}
      style={({ pressed }) => [
        styles.discoveryCard,
        { backgroundColor: theme.backgroundElement, borderColor: theme.border },
        pressed && styles.pressed,
      ]}
    >
      <View style={styles.cardImageWrap}>
        <Image source={{ uri: item.image }} style={styles.cardImage} contentFit="cover" transition={180} />
        {item.rating ? (
          <View style={styles.ratingBadge}>
            <ThemedText style={styles.ratingText}>{item.rating}</ThemedText>
            <AppIcon name="star" color="#F59E0B" size={13} />
          </View>
        ) : null}
      </View>
      <View style={styles.cardBody}>
        <ThemedText style={[styles.cardTitle, { color: theme.text }]} numberOfLines={1}>{item.title}</ThemedText>
        <ThemedText style={[styles.cardSubtitle, { color: theme.textSecondary }]} numberOfLines={1}>{item.subtitle}</ThemedText>
        <View style={styles.metaRow}>
          <View style={[styles.metaPill, { backgroundColor: theme.muted }]}>
            <AppIcon name={item.metaIcon} color={theme.textSecondary} size={13} />
            <ThemedText style={[styles.metaText, { color: theme.text }]}>{item.meta}</ThemedText>
          </View>
          {typeof item.likes === 'number' ? (
            <View style={[styles.likePill, { backgroundColor: theme.primarySoft }]}>
              <AppIcon name="heart" color={theme.primary} size={13} />
              <ThemedText style={[styles.likeText, { color: theme.primary }]}>{item.likes}</ThemedText>
            </View>
          ) : null}
        </View>
        {item.authorName || item.companyName ? (
          <ThemedText style={[styles.byline, { color: theme.textSecondary }]} numberOfLines={2}>
            By {item.authorName ?? 'MyPet Expert'}{item.companyName ? ` · ${item.companyName}` : ''}
          </ThemedText>
        ) : null}
      </View>
    </Pressable>
  );
}

function HorizontalCardSection({
  title,
  items,
  actionLabel,
  onAction,
}: {
  title: string;
  items: DiscoveryCardItem[];
  actionLabel?: string;
  onAction?: () => void;
}) {
  return (
    <View style={styles.section}>
      <SectionHeading title={title} actionLabel={actionLabel} onAction={onAction} />
      <ScrollView
        horizontal
        nestedScrollEnabled
        directionalLockEnabled
        showsHorizontalScrollIndicator={false}
        decelerationRate="fast"
        snapToInterval={CARD_WIDTH + CARD_GAP}
        snapToAlignment="start"
        contentContainerStyle={styles.horizontalCards}
      >
        {items.map((item) => <DiscoveryCard key={item.id} item={item} />)}
      </ScrollView>
    </View>
  );
}

export default function HomeScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const theme = useTheme();
  const { user } = useAuth();
  const { t } = useTranslation();
  const { activeCity, openLocationModal } = useLocation();
  const [searchQuery, setSearchQuery] = useState('');
  const [activeFilter, setActiveFilter] = useState('All');
  const [banners, setBanners] = useState<PromoBanner[]>(PROMO_BANNERS);
  const [guideItems, setGuideItems] = useState<DiscoveryCardItem[]>(GUIDES);

  useEffect(() => {
    void fetchBanners()
      .then((items) => setBanners(items.length > 0 ? items : PROMO_BANNERS))
      .catch(() => setBanners(PROMO_BANNERS));

    void fetchGuides(null)
      .then((articles) => {
        if (articles.length === 0) return;
        const images = GUIDES.map((guide) => guide.image);
        setGuideItems(articles.slice(0, 6).map((article: GuideArticle, index) => ({
          id: article.id,
          title: article.title,
          subtitle: article.summary,
          image: images[index % images.length],
          meta: `${article.readMinutes} min read`,
          metaIcon: 'document',
          route: '/guides',
          likes: article.likeCount,
          authorName: article.authorName,
          companyName: article.companyName,
        })));
      })
      .catch(() => setGuideItems(GUIDES));
  }, []);

  const firstName = useMemo(() => {
    if (typeof user?.user_metadata?.full_name === 'string') {
      return user.user_metadata.full_name.split(' ')[0];
    }
    return t('common.petParent');
  }, [t, user]);

  return (
    <>
      <ScrollView
        style={[styles.container, { backgroundColor: theme.background }]}
        contentContainerStyle={[styles.contentContainer, { paddingTop: insets.top + 10 }]}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.topRow}>
          <Pressable onPress={openLocationModal} style={styles.locationSummary} accessibilityRole="button">
            <AppIcon name="location" color={theme.primary} size={21} />
            <View style={styles.flexOne}>
              <View style={styles.locationTitleRow}>
                <ThemedText style={[styles.locationTitle, { color: theme.text }]}>Home</ThemedText>
                <AppIcon name="chevron" color={theme.textSecondary} size={13} />
              </View>
              <ThemedText style={[styles.locationSubtitle, { color: theme.textSecondary }]} numberOfLines={1}>
                {activeCity.displayName}, {activeCity.state}
              </ThemedText>
            </View>
          </Pressable>

          <View style={styles.profileSummary}>
            <View style={styles.profileCopy}>
              <View style={styles.premiumPill}>
                <ThemedText style={styles.premiumText}>Premium</ThemedText>
              </View>
              <ThemedText style={[styles.profileName, { color: theme.textSecondary }]} numberOfLines={1}>{firstName}</ThemedText>
            </View>
            <View style={[styles.avatar, { borderColor: theme.warning, backgroundColor: theme.primarySoft }]}>
              <AppIcon name="paw" color={theme.primary} size={20} />
            </View>
          </View>
        </View>

        <View style={[styles.searchField, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <AppIcon name="search" color={theme.textSecondary} size={20} />
          <TextInput
            value={searchQuery}
            onChangeText={setSearchQuery}
            placeholder="Search for 'Pedigree' or 'Grooming'..."
            placeholderTextColor={theme.textSecondary}
            style={[styles.searchInput, { color: theme.text }]}
            returnKeyType="search"
            onSubmitEditing={() => {
              const value = searchQuery.trim();
              if (value) router.push({ pathname: '/search', params: { q: value } } as never);
            }}
          />
          <View style={[styles.searchDivider, { backgroundColor: theme.border }]} />
          <Pressable onPress={() => router.push({ pathname: '/search', params: { mic: 'true' } } as never)} hitSlop={8}>
            <AppIcon name="sparkle" color={theme.primary} size={19} />
          </Pressable>
        </View>

        <BannerCarousel banners={banners} onPress={() => router.push('/category/new-arrivals' as never)} />

        <View style={[styles.quickActions, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          {QUICK_ACTIONS.map((action, index) => (
            <React.Fragment key={action.id}>
              {index > 0 ? <View style={[styles.quickDivider, { backgroundColor: theme.border }]} /> : null}
              <Pressable onPress={() => router.push(action.route as never)} style={({ pressed }) => [styles.quickAction, pressed && styles.pressed]}>
                <AppIcon name={action.icon} color={theme.primary} size={21} />
                <ThemedText style={[styles.quickLabel, { color: theme.text }]}>{action.label}</ThemedText>
              </Pressable>
            </React.Fragment>
          ))}
        </View>

        <View style={[styles.healthPanel, { backgroundColor: theme.primarySoft, borderColor: theme.border }]}>
          <View style={styles.healthTitleRow}>
            <AppIcon name="document" color={theme.primary} size={17} />
            <ThemedText style={[styles.healthTitle, { color: theme.text }]}>Reports & Health</ThemedText>
          </View>
          <View style={styles.healthGrid}>
            {HEALTH_ACTIONS.map((action) => (
              <Pressable
                key={action.id}
                onPress={() => router.push(action.route as never)}
                style={({ pressed }) => [
                  styles.healthCard,
                  { backgroundColor: theme.backgroundElement },
                  pressed && styles.pressed,
                ]}
              >
                <AppIcon name={action.icon} color={theme.primary} size={22} />
                <ThemedText style={[styles.healthLabel, { color: theme.text }]} numberOfLines={2}>{action.label}</ThemedText>
              </Pressable>
            ))}
          </View>
        </View>

        <View style={styles.section}>
          <ThemedText style={[styles.mindTitle, { color: theme.text }]}>What&apos;s on your pet&apos;s mind? ✨</ThemedText>
          <ThemedText style={[styles.mindSubtitle, { color: theme.textSecondary }]}>Choose from premium foods, grooming, hospitals and more</ThemedText>

          <ScrollView horizontal nestedScrollEnabled showsHorizontalScrollIndicator={false} contentContainerStyle={styles.categoryRow}>
            {CATEGORIES.map((category) => (
              <Pressable
                key={category.id}
                onPress={() => router.push(category.route as never)}
                style={({ pressed }) => [styles.categoryItem, pressed && styles.pressed]}
              >
                <View style={[styles.categoryImageWrap, { backgroundColor: theme.muted }]}>
                  <Image source={{ uri: category.image }} style={styles.categoryImage} contentFit="cover" transition={180} />
                </View>
                <ThemedText style={[styles.categoryLabel, { color: theme.textSecondary }]} numberOfLines={2}>{category.label}</ThemedText>
              </Pressable>
            ))}
          </ScrollView>

          <ScrollView horizontal nestedScrollEnabled showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filterRow}>
            {FILTERS.map((filter) => {
              const active = activeFilter === filter;
              return (
                <Pressable
                  key={filter}
                  onPress={() => setActiveFilter(filter)}
                  style={[
                    styles.filterChip,
                    { backgroundColor: active ? theme.primary : theme.muted },
                  ]}
                >
                  <ThemedText style={[styles.filterText, { color: active ? '#FFFFFF' : theme.textSecondary }]}>{filter}</ThemedText>
                </Pressable>
              );
            })}
          </ScrollView>
        </View>

        <HorizontalCardSection
          title="Food & Nutrition Nearby 🏆"
          items={FOOD_AND_NUTRITION}
          actionLabel="View all"
          onAction={() => router.push('/category/food' as never)}
        />

        <HorizontalCardSection
          title="Grooming Nearby ✂️"
          items={GROOMING_NEARBY}
          actionLabel="View spas"
          onAction={() => router.push('/groom' as never)}
        />

        <HorizontalCardSection
          title="Hospitals & Care Nearby 🏥"
          items={HOSPITALS_AND_CARE}
          actionLabel="View hospitals"
          onAction={() => router.push('/vet' as never)}
        />

        <HorizontalCardSection
          title="Guides 🩺"
          items={guideItems}
          actionLabel="All guides"
          onAction={() => router.push('/guides' as never)}
        />
      </ScrollView>
      <LocationModal />
      <NotifyCityModal />
    </>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  contentContainer: {
    paddingHorizontal: Spacing.three,
    paddingBottom: 112,
    gap: 14,
  },
  flexOne: { flex: 1 },
  pressed: { opacity: 0.86 },
  topRow: {
    minHeight: 48,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  locationSummary: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 8 },
  locationTitleRow: { flexDirection: 'row', alignItems: 'center', gap: 2 },
  locationTitle: { fontSize: 14, lineHeight: 18, fontWeight: '800' },
  locationSubtitle: { maxWidth: 190, fontSize: 11, lineHeight: 15 },
  profileSummary: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  profileCopy: { alignItems: 'flex-end', maxWidth: 88 },
  premiumPill: { borderRadius: 999, backgroundColor: '#FDBA2D', paddingHorizontal: 6, paddingVertical: 2 },
  premiumText: { color: '#4A2C00', fontSize: 9, lineHeight: 11, fontWeight: '900' },
  profileName: { marginTop: 2, fontSize: 10, lineHeight: 13 },
  avatar: { width: 39, height: 39, borderRadius: 20, borderWidth: 2, alignItems: 'center', justifyContent: 'center' },
  searchField: {
    height: 48,
    borderWidth: 1,
    borderRadius: 13,
    paddingHorizontal: 13,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    ...Shadows.card,
  },
  searchInput: { flex: 1, height: 46, fontSize: 14, paddingVertical: 0 },
  searchDivider: { width: 1, height: 20 },
  quickActions: {
    minHeight: 68,
    borderWidth: 1,
    borderRadius: Radius.xl,
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 6,
    ...Shadows.card,
  },
  quickAction: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 5, paddingVertical: 7 },
  quickDivider: { width: 1, height: 32 },
  quickLabel: { fontSize: 10, lineHeight: 13, fontWeight: '700' },
  healthPanel: { borderRadius: Radius.xl, borderWidth: 1, padding: 10, gap: 9 },
  healthTitleRow: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  healthTitle: { fontSize: 13, lineHeight: 17, fontWeight: '800' },
  healthGrid: { flexDirection: 'row', gap: 7 },
  healthCard: {
    flex: 1,
    minHeight: 74,
    borderRadius: 9,
    paddingHorizontal: 6,
    paddingVertical: 9,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  healthLabel: { textAlign: 'center', fontSize: 10, lineHeight: 13, fontWeight: '600' },
  section: { gap: 10, marginTop: 3 },
  sectionHeading: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  sectionTitle: { flex: 1, fontSize: 18, lineHeight: 23, fontWeight: '800' },
  sectionAction: { fontSize: 12, lineHeight: 16, fontWeight: '800' },
  mindTitle: { fontSize: 18, lineHeight: 23, fontWeight: '800' },
  mindSubtitle: { marginTop: -8, fontSize: 11, lineHeight: 15 },
  categoryRow: { gap: 13, paddingVertical: 2, paddingRight: Spacing.three },
  categoryItem: { width: 72, alignItems: 'center', gap: 7 },
  categoryImageWrap: { width: 64, height: 64, borderRadius: 32, overflow: 'hidden' },
  categoryImage: { width: '100%', height: '100%' },
  categoryLabel: { minHeight: 29, textAlign: 'center', fontSize: 10, lineHeight: 14, fontWeight: '600' },
  filterRow: { gap: 8, paddingRight: Spacing.three },
  filterChip: { minHeight: 30, borderRadius: 999, paddingHorizontal: 14, alignItems: 'center', justifyContent: 'center' },
  filterText: { fontSize: 11, lineHeight: 15, fontWeight: '700' },
  horizontalCards: { gap: CARD_GAP, paddingRight: Spacing.three, paddingBottom: 2 },
  discoveryCard: {
    width: CARD_WIDTH,
    borderWidth: 1,
    borderRadius: 12,
    overflow: 'hidden',
    ...Shadows.card,
  },
  cardImageWrap: { height: 130, position: 'relative', overflow: 'hidden' },
  cardImage: { width: '100%', height: '100%' },
  ratingBadge: {
    position: 'absolute',
    top: 8,
    right: 8,
    minHeight: 27,
    borderRadius: 7,
    backgroundColor: 'rgba(255,255,255,0.94)',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    paddingHorizontal: 8,
    paddingVertical: 4,
  },
  ratingText: { color: '#152338', fontSize: 11, lineHeight: 14, fontWeight: '900' },
  cardBody: { padding: 10, gap: 3 },
  cardTitle: { fontSize: 14, lineHeight: 18, fontWeight: '800' },
  cardSubtitle: { fontSize: 11, lineHeight: 15 },
  metaRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  metaPill: {
    alignSelf: 'flex-start',
    minHeight: 25,
    marginTop: 4,
    borderRadius: 6,
    paddingHorizontal: 8,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  metaText: { fontSize: 10, lineHeight: 13, fontWeight: '700' },
  likePill: { minHeight: 25, borderRadius: 999, paddingHorizontal: 8, flexDirection: 'row', alignItems: 'center', gap: 4 },
  likeText: { fontSize: 10, lineHeight: 13, fontWeight: '800' },
  byline: { marginTop: 2, fontSize: 10, lineHeight: 14, fontWeight: '600' },
});
