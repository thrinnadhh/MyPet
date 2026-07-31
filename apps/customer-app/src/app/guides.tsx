import { useRouter } from 'expo-router';
import React from 'react';
import { Image, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { ThemedText } from '@/components/themed-text';
import { ScreenHeader } from '@/components/ui/screen-header';
import { StatusBadge } from '@/components/ui/status-badge';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { ARTICLES_DATA } from '@/services/content-data';

export default function GuidesScreen() {
  const theme = useTheme();
  const router = useRouter();

  const articlesList = Object.values(ARTICLES_DATA);

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <ScreenHeader title="PetCare Health Guides" subtitle="Verified veterinary knowledge & tips" />

      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {articlesList.map((article) => (
          <Pressable
            key={article.id}
            onPress={() => router.push(`/guide/${article.id}` as never)}
            style={({ pressed }) => [
              styles.card,
              shadows.raised,
              { backgroundColor: theme.backgroundElement, borderColor: theme.border },
              pressed && styles.pressed,
            ]}
          >
            <Image source={{ uri: article.heroImageUrl }} style={styles.thumb} resizeMode="cover" />
            <View style={styles.cardInfo}>
              <View style={styles.badgeRow}>
                <StatusBadge label={article.category} color={theme.primary} />
                <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>⏱️ {article.readTimeMins} min read</ThemedText>
              </View>

              <ThemedText style={[styles.cardTitle, { color: theme.text }]}>{article.title}</ThemedText>
              <ThemedText style={{ fontSize: 12, color: theme.textSecondary }} numberOfLines={2}>
                {article.subtitle}
              </ThemedText>
            </View>
          </Pressable>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: spacing.x3 },
  content: { gap: spacing.x4, paddingBottom: spacing.x6 },
  card: { borderRadius: radii.card, borderWidth: 1, overflow: 'hidden' },
  thumb: { width: '100%', height: 140 },
  cardInfo: { padding: spacing.x3, gap: spacing.x2 },
  badgeRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  cardTitle: { ...typography.headline, fontSize: 16, fontWeight: '700' },
  pressed: { opacity: 0.88 },
});
