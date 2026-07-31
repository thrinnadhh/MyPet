import { useLocalSearchParams, useRouter } from 'expo-router';
import React, { useMemo, useState } from 'react';
import { Image, Pressable, ScrollView, Share, StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { ThemedText } from '@/components/themed-text';
import { PrimaryButton } from '@/components/ui/primary-button';
import { ScreenHeader } from '@/components/ui/screen-header';
import { StatusBadge } from '@/components/ui/status-badge';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { ARTICLES_DATA, type GuideArticle } from '@/services/content-data';

export default function GuideDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const theme = useTheme();

  const [readProgress, setReadProgress] = useState(0.2);

  const article: GuideArticle = useMemo(() => {
    const articleId = id ?? 'puppy-nutrition-0-2-mo';
    return ARTICLES_DATA[articleId] ?? ARTICLES_DATA['puppy-nutrition-0-2-mo'];
  }, [id]);

  const relatedArticles = useMemo(() => {
    return article.relatedGuideIds.map((relId) => ARTICLES_DATA[relId]).filter(Boolean);
  }, [article]);

  const handleShare = async () => {
    try {
      await Share.share({
        title: article.title,
        message: `${article.title} - ${article.subtitle} Read on MyPet: https://mypet.app/guide/${article.id}`,
      });
    } catch (e) {
      // Ignored
    }
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      {/* Top Reading Progress Bar */}
      <View style={[styles.progressTrack, { backgroundColor: theme.muted }]}>
        <View style={[styles.progressFill, { width: `${readProgress * 100}%`, backgroundColor: theme.primary }]} />
      </View>

      <ScreenHeader
        title={article.category}
        subtitle={`${article.readTimeMins} min read`}
        trailing={
          <Pressable onPress={() => void handleShare()} style={{ padding: 4 }}>
            <AppIcon name="sparkle" color={theme.primary} size={20} />
          </Pressable>
        }
      />

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
        onScroll={(e) => {
          const { layoutMeasurement, contentOffset, contentSize } = e.nativeEvent;
          const pct = Math.min(1, Math.max(0, (contentOffset.y + layoutMeasurement.height) / contentSize.height));
          setReadProgress(pct);
        }}
        scrollEventThrottle={16}
      >
        {/* Hero Card */}
        <View style={styles.heroCard}>
          <Image source={{ uri: article.heroImageUrl }} style={styles.heroImage} resizeMode="cover" />
          <View style={styles.heroOverlay}>
            {article.veterinaryApproved && (
              <StatusBadge label="✓ Verified Veterinary Knowledge" color={theme.success} />
            )}
          </View>
        </View>

        {/* Title Header */}
        <View style={styles.section}>
          <ThemedText style={[styles.title, { color: theme.text }]}>{article.title}</ThemedText>
          <ThemedText style={[styles.subtitle, { color: theme.textSecondary }]}>{article.subtitle}</ThemedText>
          <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>By {article.author}</ThemedText>
        </View>

        {/* Key Takeaways Box */}
        <View style={[styles.takeawaysBox, { backgroundColor: theme.primarySoft, borderColor: theme.primary }]}>
          <ThemedText style={[styles.takeawaysTitle, { color: theme.primary }]}>💡 Key Takeaways for Pet Parents</ThemedText>
          {article.keyTakeaways.map((item, idx) => (
            <View key={idx} style={styles.bulletRow}>
              <ThemedText style={{ color: theme.primary, fontWeight: '800' }}>•</ThemedText>
              <ThemedText style={{ fontSize: 13, color: theme.text, flex: 1, lineHeight: 18 }}>{item}</ThemedText>
            </View>
          ))}
        </View>

        {/* Rich Content Sections */}
        {article.sections.map((sec, idx) => (
          <View key={idx} style={styles.section}>
            <ThemedText style={[styles.heading, { color: theme.text }]}>{sec.heading}</ThemedText>
            <ThemedText style={[styles.bodyText, { color: theme.textSecondary }]}>{sec.body}</ThemedText>
            {sec.tips && sec.tips.length > 0 && (
              <View style={[styles.tipCard, { backgroundColor: theme.muted, borderColor: theme.border }]}>
                {sec.tips.map((tip, tIdx) => (
                  <ThemedText key={tIdx} style={{ fontSize: 13, color: theme.text }}>
                    📌 <ThemedText style={{ fontWeight: '700' }}>Pro-tip: </ThemedText>{tip}
                  </ThemedText>
                ))}
              </View>
            )}
          </View>
        ))}

        {/* Related Guides Carousel */}
        {relatedArticles.length > 0 && (
          <View style={styles.section}>
            <ThemedText style={[styles.heading, { color: theme.text }]}>Related Pet Health Guides</ThemedText>
            <View style={styles.relatedGrid}>
              {relatedArticles.map((rel) => (
                <Pressable
                  key={rel.id}
                  onPress={() => router.push(`/guide/${rel.id}` as never)}
                  style={[styles.relCard, shadows.raised, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}
                >
                  <ThemedText style={{ fontSize: 11, color: theme.primary, fontWeight: '700' }}>{rel.category}</ThemedText>
                  <ThemedText style={{ fontSize: 14, fontWeight: '700', color: theme.text }} numberOfLines={2}>
                    {rel.title}
                  </ThemedText>
                  <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>⏱️ {rel.readTimeMins} min read</ThemedText>
                </Pressable>
              ))}
            </View>
          </View>
        )}

        <PrimaryButton label="Consult a Vet Specialist" onPress={() => router.push('/hospital/city-pet-hospital' as never)} />
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: spacing.x3 },
  progressTrack: { height: 4, width: '100%', borderRadius: 2, overflow: 'hidden' },
  progressFill: { height: '100%' },
  scrollContent: { paddingBottom: spacing.x6, gap: spacing.x4 },
  heroCard: { width: '100%', height: 200, borderRadius: radii.card, overflow: 'hidden', position: 'relative' },
  heroImage: { width: '100%', height: '100%' },
  heroOverlay: { position: 'absolute', top: 12, left: 12 },
  section: { gap: spacing.x2 },
  title: { ...typography.headline, fontSize: 20, fontWeight: '800' },
  subtitle: { fontSize: 14, lineHeight: 20 },
  takeawaysBox: { padding: spacing.x4, borderRadius: radii.card, borderWidth: 1, gap: spacing.x2 },
  takeawaysTitle: { ...typography.headline, fontSize: 15, fontWeight: '700' },
  bulletRow: { flexDirection: 'row', gap: 8, alignItems: 'flex-start' },
  heading: { ...typography.headline, fontSize: 16, fontWeight: '700' },
  bodyText: { ...typography.body, fontSize: 14, lineHeight: 22 },
  tipCard: { padding: spacing.x3, borderRadius: radii.compact, borderWidth: 1, marginTop: 4, gap: 4 },
  relatedGrid: { gap: spacing.x3 },
  relCard: { padding: spacing.x3, borderRadius: radii.card, borderWidth: 1, gap: 4 },
});
