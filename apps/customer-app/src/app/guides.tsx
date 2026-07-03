import React, { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';

import { AppIcon } from '@/components/app-icon';
import { AppCard } from '@/components/ui/app-card';
import { ScreenHeader } from '@/components/ui/screen-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { GUIDE_CATEGORIES, type GuideCategory } from '@/constants/content';
import { fetchGuides, type GuideArticle } from '@/services/content';
import { BottomTabInset, Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export default function GuidesScreen() {
  const theme = useTheme();
  const router = useRouter();
  const [category, setCategory] = useState<GuideCategory>('puppy-kitten');
  const [articles, setArticles] = useState<GuideArticle[]>([]);

  useEffect(() => {
    void fetchGuides(category).then(setArticles).catch(() => setArticles([]));
  }, [category]);

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <ScreenHeader
          title="Pet care guides"
          subtitle="Health tips from trusted vets and groomers"
          trailing={
            <Pressable onPress={() => router.back()} accessibilityRole="button" accessibilityLabel="Go back">
              <ThemedText style={{ color: theme.primary, fontWeight: '800' }}>Back</ThemedText>
            </Pressable>
          }
        />
        <ScrollView
          showsVerticalScrollIndicator={false}
          contentContainerStyle={[styles.content, { paddingBottom: BottomTabInset + Spacing.six }]}
        >
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.categories}>
            {GUIDE_CATEGORIES.map((item) => {
              const selected = item.id === category;
              return (
                <Pressable
                  key={item.id}
                  onPress={() => setCategory(item.id)}
                  style={[
                    styles.categoryChip,
                    {
                      backgroundColor: selected ? theme.primary : theme.backgroundElement,
                      borderColor: theme.border,
                    },
                  ]}
                  accessibilityRole="button"
                  accessibilityLabel={item.label}
                >
                  <ThemedText style={{ color: selected ? '#FFFFFF' : theme.text, fontWeight: '800' }}>
                    {item.label}
                  </ThemedText>
                </Pressable>
              );
            })}
          </ScrollView>

          <ThemedText type="small" themeColor="textSecondary">
            {GUIDE_CATEGORIES.find((c) => c.id === category)?.description}
          </ThemedText>

          {articles.map((article) => (
            <AppCard key={article.id}>
              <View style={styles.articleRow}>
                <View style={[styles.articleIcon, { backgroundColor: theme.primarySoft }]}>
                  <AppIcon name="shield" color={theme.primary} size={20} />
                </View>
                <View style={{ flex: 1, gap: 4 }}>
                  <ThemedText style={{ fontWeight: '900' }}>{article.title}</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">{article.summary}</ThemedText>
                  <ThemedText type="small" style={{ color: theme.accent, fontWeight: '700' }}>
                    {article.readMinutes} min read
                  </ThemedText>
                </View>
              </View>
            </AppCard>
          ))}
        </ScrollView>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  safeArea: { flex: 1 },
  content: { padding: Spacing.four, gap: Spacing.three },
  categories: { gap: Spacing.two },
  categoryChip: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    borderRadius: Radius.md,
    borderWidth: 1,
    minHeight: 44,
    justifyContent: 'center',
  },
  articleRow: { flexDirection: 'row', gap: Spacing.three, alignItems: 'flex-start' },
  articleIcon: {
    width: 44,
    height: 44,
    borderRadius: Radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
