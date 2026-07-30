import { useRouter } from 'expo-router';
import React from 'react';
import { StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { AppBar, EntityCard, LocationHeader, SearchField, SectionHeader } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { useAuth } from '@/context/AuthContext';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { useTranslation } from '@/i18n';

export default function HomeScreen() {
  const router = useRouter();
  const theme = useTheme();
  const { user } = useAuth();
  const { t } = useTranslation();
  const name = typeof user?.user_metadata?.full_name === 'string' ? user.user_metadata.full_name.split(' ')[0] : null;

  return (
    <ScreenShell header={<AppBar title={t('common.brand')} subtitle={name ? `${t('home.greetingMorning')}, ${name}` : t('home.greetingGuest')} />} testID="guest-home-screen">
      <LocationHeader label={t('home.deliveryLabel')} location={t('home.locationLive')} />
      <SearchField value="" onChangeText={() => undefined} placeholder={t('home.searchPlaceholder')} editable={false} onPress={() => router.push('/(tabs)/search' as never)} />
      <View style={[styles.hero, shadows.raised, { backgroundColor: theme.primary, borderColor: theme.primary }]}>
        <View style={[styles.paw, { backgroundColor: theme.backgroundElement }]}><AppIcon name="paw" color={theme.primary} size={28} /></View>
        <ThemedText style={styles.heroTitle}>{t('home.greetingGuest')}</ThemedText>
        <ThemedText style={styles.heroBody}>{t('home.heroSubtitle')}</ThemedText>
      </View>
      <SectionHeader title={t('home.browseTitle')} />
      <View style={styles.grid}>
        <EntityCard title={t('home.petShop')} subtitle={t('home.petShopSubtitle')} icon="store" onPress={() => router.push('/shop' as never)} />
        <EntityCard title={t('home.vetCare')} subtitle={t('home.vetCareSubtitle')} icon="medical" onPress={() => router.push('/vet' as never)} />
        <EntityCard title={t('home.grooming')} subtitle={t('home.groomingSubtitle')} icon="groom" onPress={() => router.push('/groom' as never)} />
        <EntityCard title={t('home.guides')} subtitle={t('home.guidesSubtitle')} icon="shield" onPress={() => router.push('/guides' as never)} />
      </View>
      <View style={[styles.guestNote, { backgroundColor: theme.primarySoft }]}><AppIcon name="shield" color={theme.primary} /><ThemedText style={styles.noteText}>{t('home.guestNote')}</ThemedText></View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  hero: { borderRadius: radii.feature, padding: spacing.x6, gap: spacing.x3, minHeight: 190, justifyContent: 'center' },
  paw: { width: 52, height: 52, borderRadius: 26, alignItems: 'center', justifyContent: 'center' },
  heroTitle: { ...typography.headline, color: '#FFFFFF' },
  heroBody: { ...typography.body, color: '#EAF1FF' },
  grid: { gap: spacing.x3 },
  guestNote: { flexDirection: 'row', gap: spacing.x3, padding: spacing.x4, borderRadius: radii.card, alignItems: 'center' },
  noteText: { flex: 1, ...typography.label },
});
