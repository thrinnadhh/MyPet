import React, { useEffect, useMemo, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';

import { AppIcon } from '@/components/app-icon';
import { AppCard } from '@/components/ui/app-card';
import { BannerCarousel } from '@/components/ui/banner-carousel';
import { ServiceTile } from '@/components/ui/service-tile';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { BottomTabInset, Radius, Shadows, Spacing } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/hooks/use-theme';
import { useTranslation } from '@/i18n';
import { appConfig } from '@/utils/app-config';
import { fetchBanners, type PromoBanner } from '@/services/content';
import { PROMO_BANNERS } from '@/constants/content';

export default function HomeScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { user } = useAuth();
  const { t } = useTranslation();

  const greeting = useMemo(() => {
    const hour = new Date().getHours();
    if (hour < 12) return t('home.greetingMorning');
    if (hour < 17) return t('home.greetingAfternoon');
    return t('home.greetingEvening');
  }, [t]);

  const firstName = user?.user_metadata?.full_name?.split(' ')?.[0] ?? t('common.petParent');
  const [banners, setBanners] = useState<PromoBanner[]>(
    appConfig.allowDemoMode
      ? PROMO_BANNERS.map((b) => ({ id: b.id, title: b.title, subtitle: b.subtitle, accent: b.accent, durationSec: b.durationSec }))
      : [],
  );

  useEffect(() => {
    void fetchBanners().then(setBanners).catch(() => undefined);
  }, []);

  const highlights = useMemo(
    () => [
      { id: '1', title: t('home.highlight1Title'), subtitle: t('home.highlight1Subtitle') },
      { id: '2', title: t('home.highlight2Title'), subtitle: t('home.highlight2Subtitle') },
      { id: '3', title: t('home.highlight3Title'), subtitle: t('home.highlight3Subtitle') },
    ],
    [t],
  );

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <ScrollView
          showsVerticalScrollIndicator={false}
          contentContainerStyle={[styles.content, { paddingBottom: BottomTabInset + Spacing.six }]}
        >
          <View style={[styles.hero, { backgroundColor: theme.primarySoft, borderColor: theme.border }]}>
            <View style={styles.heroTop}>
              <View style={[styles.brandBadge, { backgroundColor: theme.primary }]}>
                <AppIcon name="paw" color="#FFFFFF" size={18} />
                <ThemedText style={styles.brandText}>{t('common.brand')}</ThemedText>
              </View>
              <View style={[styles.livePill, { backgroundColor: theme.backgroundElement }]}>
                <View style={[styles.liveDot, { backgroundColor: appConfig.allowDemoMode ? theme.warning : theme.success }]} />
                <ThemedText type="small" style={{ fontWeight: '800' }}>
                  {appConfig.allowDemoMode ? t('common.demo') : t('home.locationLive')}
                </ThemedText>
              </View>
            </View>
            <ThemedText style={styles.heroTitle}>{t('home.greeting', { greeting, name: firstName })}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {t('home.heroSubtitle')}
            </ThemedText>
          </View>

          <BannerCarousel banners={banners} onPress={() => router.push('/guides' as never)} />

          <View>
            <ThemedText style={styles.sectionTitle}>{t('home.services')}</ThemedText>
            <View style={styles.serviceGrid}>
              <ServiceTile
                title={t('home.petShop')}
                subtitle={t('home.petShopSubtitle')}
                icon="cart"
                tone="primary"
                onPress={() => router.push('/shop' as never)}
              />
              <ServiceTile
                title={t('home.vetCare')}
                subtitle={t('home.vetCareSubtitle')}
                icon="medical"
                tone="cta"
                onPress={() => router.push('/vet' as never)}
              />
              <ServiceTile
                title={t('home.grooming')}
                subtitle={t('home.groomingSubtitle')}
                icon="groom"
                tone="accent"
                onPress={() => router.push('/groom' as never)}
              />
              <ServiceTile
                title={t('home.guides')}
                subtitle={t('home.guidesSubtitle')}
                icon="shield"
                tone="accent"
                onPress={() => router.push('/guides' as never)}
              />
              <ServiceTile
                title={t('home.history')}
                subtitle={t('home.historySubtitle')}
                icon="history"
                tone="primary"
                onPress={() => router.push('/explore' as never)}
              />
            </View>
          </View>

          <AppCard style={{ backgroundColor: theme.muted, borderColor: theme.border }}>
            <View style={styles.reminderRow}>
              <View style={[styles.reminderIcon, { backgroundColor: theme.backgroundElement }]}>
                <AppIcon name="medical" color={theme.cta} size={22} />
              </View>
              <View style={{ flex: 1, gap: 4 }}>
                <ThemedText style={{ fontWeight: '900' }}>{t('home.vaccinationReminders')}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {t('home.vaccinationReminderBody')}
                </ThemedText>
              </View>
            </View>
          </AppCard>

          <AppCard>
            <View style={styles.cardHeader}>
              <ThemedText style={styles.sectionTitle}>{t('home.whyTitle')}</ThemedText>
              <AppIcon name="shield" color={theme.accent} size={22} />
            </View>
            {highlights.map((item, index) => (
              <View key={item.id} style={[styles.highlightRow, index > 0 && styles.highlightBorder, { borderTopColor: theme.border }]}>
                <View style={[styles.highlightIndex, { backgroundColor: theme.muted }]}>
                  <ThemedText type="small" style={{ fontWeight: '900' }}>{index + 1}</ThemedText>
                </View>
                <View style={styles.highlightCopy}>
                  <ThemedText style={{ fontWeight: '800' }}>{item.title}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">{item.subtitle}</ThemedText>
                </View>
              </View>
            ))}
          </AppCard>

          <AppCard style={[styles.supportCard, { backgroundColor: theme.ctaSoft, borderColor: theme.border }]}>
            <View style={styles.supportRow}>
              <View style={[styles.supportIcon, { backgroundColor: theme.backgroundElement }]}>
                <AppIcon name="support" color={theme.cta} size={22} />
              </View>
              <View style={{ flex: 1, gap: 4 }}>
                <ThemedText style={{ fontWeight: '900' }}>{t('home.needHelp')}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {t('home.needHelpBody')}
                </ThemedText>
              </View>
            </View>
          </AppCard>
        </ScrollView>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  safeArea: { flex: 1 },
  content: {
    padding: Spacing.four,
    gap: Spacing.four,
  },
  hero: {
    borderWidth: 1,
    borderRadius: Radius.xl,
    padding: Spacing.four,
    gap: Spacing.two,
    ...Shadows.card,
  },
  heroTop: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  brandBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
    paddingHorizontal: Spacing.two,
    paddingVertical: 6,
    borderRadius: Radius.md,
  },
  brandText: {
    color: '#FFFFFF',
    fontWeight: '900',
    fontSize: 14,
  },
  livePill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: Spacing.two,
    paddingVertical: 6,
    borderRadius: 999,
  },
  liveDot: { width: 8, height: 8, borderRadius: 4 },
  heroTitle: {
    fontSize: 28,
    fontWeight: '900',
    lineHeight: 34,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '900',
    marginBottom: Spacing.two,
  },
  serviceGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    rowGap: Spacing.two,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: Spacing.one,
  },
  highlightRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.two,
    paddingVertical: Spacing.two,
  },
  highlightBorder: {
    borderTopWidth: 1,
  },
  highlightIndex: {
    width: 28,
    height: 28,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  highlightCopy: { flex: 1, gap: 2 },
  reminderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
  },
  reminderIcon: {
    width: 48,
    height: 48,
    borderRadius: Radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  supportCard: {
    gap: Spacing.two,
  },
  supportRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
  },
  supportIcon: {
    width: 48,
    height: 48,
    borderRadius: Radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
