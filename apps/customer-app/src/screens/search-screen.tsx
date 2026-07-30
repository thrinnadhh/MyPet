import AsyncStorage from '@react-native-async-storage/async-storage';
import { useLocalSearchParams, useRouter } from 'expo-router';
import React, { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, TextInput, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { FilterChip } from '@/components/foundation/primitives';
import { ThemedText } from '@/components/themed-text';
import { StatusBadge } from '@/components/ui/status-badge';
import { useLocation } from '@/context/LocationContext';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { appConfig } from '@/utils/app-config';

export interface SearchResultItem {
  id: string;
  type: string; // 'PRODUCT', 'PET_SHOP', 'HOSPITAL', 'GROOMER', 'SERVICE', 'GUIDE'
  title: string;
  subtitle?: string;
  imageUrl?: string;
  rating?: string;
  price?: string;
  distanceKm?: number;
  route: string;
  isEmergency?: boolean;
}

const RECENT_SEARCHES_KEY = 'mypet_recent_searches_v1';
const FILTER_TYPES = [
  { id: 'ALL', label: 'All' },
  { id: 'PRODUCT', label: 'Products' },
  { id: 'PET_SHOP', label: 'Shops' },
  { id: 'HOSPITAL', label: 'Hospitals' },
  { id: 'GROOMER', label: 'Grooming' },
  { id: 'GUIDE', label: 'Guides' },
];

export default function UniversalSearchScreen() {
  const router = useRouter();
  const theme = useTheme();
  const params = useLocalSearchParams<{ q?: string; mic?: string }>();
  const { activeCity } = useLocation();

  const [query, setQuery] = useState(params.q ?? '');
  const [selectedType, setSelectedType] = useState('ALL');
  const [results, setResults] = useState<SearchResultItem[]>([]);
  const [recentSearches, setRecentSearches] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [isListeningVoice, setIsListeningVoice] = useState(params.mic === 'true');

  useEffect(() => {
    const loadRecent = async () => {
      try {
        const stored = await AsyncStorage.getItem(RECENT_SEARCHES_KEY);
        if (stored) {
          setRecentSearches(JSON.parse(stored) as string[]);
        }
      } catch (e) {
        console.warn('Failed to load recent searches', e);
      }
    };
    void loadRecent();
  }, []);

  const saveRecentSearch = useCallback(async (term: string) => {
    const clean = term.trim();
    if (!clean) return;
    setRecentSearches((prev) => {
      const filtered = prev.filter((item) => item.toLowerCase() !== clean.toLowerCase());
      const next = [clean, ...filtered].slice(0, 5);
      void AsyncStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify(next));
      return next;
    });
  }, []);

  const clearRecentSearches = useCallback(async () => {
    setRecentSearches([]);
    await AsyncStorage.removeItem(RECENT_SEARCHES_KEY);
  }, []);

  const performSearch = useCallback(async (searchQuery: string, type: string) => {
    if (!searchQuery.trim()) {
      setResults([]);
      return;
    }
    setLoading(true);
    try {
      const typeParam = type !== 'ALL' ? `&type=${type}` : '';
      const url = `${appConfig.apiBaseUrl}/api/v1/discovery/search?q=${encodeURIComponent(searchQuery.trim())}&city=${encodeURIComponent(activeCity.cityIdentity)}${typeParam}`;
      const res = await fetch(url);
      if (res.ok) {
        const data = await res.json();
        setResults(data.results ?? []);
        void saveRecentSearch(searchQuery);
      }
    } catch (e) {
      console.warn('Universal search API error', e);
    } finally {
      setLoading(false);
    }
  }, [activeCity.cityIdentity, saveRecentSearch]);

  // Debounced search trigger
  useEffect(() => {
    const timer = setTimeout(() => {
      if (query.trim()) {
        void performSearch(query, selectedType);
      } else {
        setResults([]);
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [query, selectedType, performSearch]);

  const handleVoiceTrigger = useCallback(() => {
    setIsListeningVoice(true);
    // Simulate voice listening assistant
    setTimeout(() => {
      setQuery('Puppy Nutrition');
      setIsListeningVoice(false);
    }, 1500);
  }, []);

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      {/* Top Navigation Bar */}
      <View style={[styles.navHeader, { borderColor: theme.border }]}>
        <Pressable onPress={() => router.back()} style={styles.backBtn} accessibilityLabel="Back">
          <AppIcon name="warning" color={theme.text} size={20} />
        </Pressable>

        <View style={[styles.searchBox, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <AppIcon name="search" color={theme.textSecondary} size={18} />
          <TextInput
            value={query}
            onChangeText={setQuery}
            placeholder="Search food, clinics, grooming, guides..."
            placeholderTextColor={theme.textSecondary}
            style={[styles.searchInput, { color: theme.text }]}
            autoFocus={!params.q}
            returnKeyType="search"
          />
          {query.length > 0 ? (
            <Pressable onPress={() => setQuery('')} style={styles.iconBtn}>
              <AppIcon name="warning" color={theme.textSecondary} size={16} />
            </Pressable>
          ) : (
            <Pressable onPress={handleVoiceTrigger} style={styles.iconBtn} accessibilityLabel="Voice search">
              <AppIcon name="sparkle" color={theme.primary} size={20} />
            </Pressable>
          )}
        </View>
      </View>

      {/* Voice Assistant Overlay */}
      {isListeningVoice && (
        <View style={[styles.voiceBanner, { backgroundColor: theme.primarySoft }]}>
          <ActivityIndicator color={theme.primary} size="small" />
          <ThemedText style={{ color: theme.primary, fontWeight: '700' }}>
            {`Listening... Speak now (e.g., "Vet Clinics near me")`}
          </ThemedText>
        </View>
      )}


      {/* Filter Tabs */}
      <View style={styles.filterRow}>
        <FlatList
          horizontal
          showsHorizontalScrollIndicator={false}
          data={FILTER_TYPES}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.filterList}
          renderItem={({ item }) => (
            <FilterChip
              label={item.label}
              selected={selectedType === item.id}
              onPress={() => setSelectedType(item.id)}
            />
          )}
        />
      </View>

      {/* Main Results / Recent Searches */}
      {loading ? (
        <View style={styles.centered}>
          <ActivityIndicator size="large" color={theme.primary} />
          <ThemedText style={{ color: theme.textSecondary, marginTop: 8 }}>Searching active providers...</ThemedText>
        </View>
      ) : query.trim().length === 0 ? (
        <View style={styles.recentSection}>
          {recentSearches.length > 0 && (
            <>
              <View style={styles.recentHeader}>
                <ThemedText style={styles.recentTitle}>Recent Searches</ThemedText>
                <Pressable onPress={clearRecentSearches}>
                  <ThemedText style={{ color: theme.primary, fontSize: 13, fontWeight: '700' }}>Clear All</ThemedText>
                </Pressable>
              </View>
              <View style={styles.chipGrid}>
                {recentSearches.map((term, idx) => (
                  <Pressable
                    key={idx}
                    onPress={() => setQuery(term)}
                    style={[styles.recentChip, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}
                  >
                    <AppIcon name="history" color={theme.textSecondary} size={14} />
                    <ThemedText style={{ fontSize: 13, color: theme.text }}>{term}</ThemedText>
                  </Pressable>
                ))}
              </View>
            </>
          )}

          <ThemedText style={[styles.recentTitle, { marginTop: spacing.x6 }]}>Popular Categories</ThemedText>

          <View style={styles.chipGrid}>
            {['Maxi Puppy Food', '24/7 Vet ICU', 'Full Grooming Bath', 'Puppy Teething Guide'].map((term, idx) => (
              <Pressable
                key={idx}
                onPress={() => setQuery(term)}
                style={[styles.recentChip, { backgroundColor: theme.primarySoft, borderColor: theme.primary }]}
              >
                <AppIcon name="sparkle" color={theme.primary} size={14} />
                <ThemedText style={{ fontSize: 13, color: theme.primary, fontWeight: '600' }}>{term}</ThemedText>
              </Pressable>
            ))}
          </View>
        </View>
      ) : results.length === 0 ? (
        <View style={styles.centered}>
          <AppIcon name="search" color={theme.textSecondary} size={40} />
          <ThemedText style={{ fontWeight: '700', fontSize: 16, marginTop: 12 }}>No matching results</ThemedText>
          <ThemedText style={{ color: theme.textSecondary, fontSize: 13, marginTop: 4 }}>
            Try searching for food, vet hospitals, grooming spas, or guides in {activeCity.displayName}.
          </ThemedText>
        </View>
      ) : (
        <FlatList
          data={results}
          keyExtractor={(item) => `${item.type}-${item.id}`}
          contentContainerStyle={styles.resultsList}
          renderItem={({ item }) => (
            <Pressable
              onPress={() => router.push(item.route as never)}
              style={({ pressed }) => [
                styles.resultCard,
                shadows.raised,
                { backgroundColor: theme.backgroundElement, borderColor: theme.border },
                pressed && styles.pressed,
              ]}
            >
              <View style={styles.resultTypeBadge}>
                <StatusBadge label={item.type.replace('_', ' ')} color={theme.primary} />
                {item.isEmergency && <StatusBadge label="24/7 ICU" color={theme.danger} />}
              </View>

              <ThemedText style={[styles.resultTitle, { color: theme.text }]}>{item.title}</ThemedText>
              {item.subtitle && (
                <ThemedText style={{ fontSize: 13, color: theme.textSecondary }}>{item.subtitle}</ThemedText>
              )}

              <View style={styles.resultFooter}>
                {item.price && <ThemedText style={{ fontWeight: '700', color: theme.primary }}>{item.price}</ThemedText>}
                {item.distanceKm && (
                  <ThemedText style={{ fontSize: 12, color: theme.textSecondary }}>
                    📍 {item.distanceKm.toFixed(1)} km away
                  </ThemedText>
                )}
              </View>
            </Pressable>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  navHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing.x3, paddingHorizontal: spacing.x4, paddingTop: spacing.x6, paddingBottom: spacing.x3, borderBottomWidth: 1 },

  backBtn: { padding: 4 },
  searchBox: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: spacing.x2, borderWidth: 1, borderRadius: radii.compact, paddingHorizontal: spacing.x3, height: 44 },
  searchInput: { flex: 1, height: 44, ...typography.body },
  iconBtn: { padding: 4 },
  voiceBanner: { flexDirection: 'row', alignItems: 'center', gap: spacing.x2, padding: spacing.x3, paddingHorizontal: spacing.x4 },
  filterRow: { paddingVertical: spacing.x2 },
  filterList: { gap: spacing.x2, paddingHorizontal: spacing.x4 },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.x6 },
  recentSection: { padding: spacing.x4, gap: spacing.x3 },
  recentHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  recentTitle: { ...typography.label, fontSize: 13, color: '#888888', textTransform: 'uppercase' },
  chipGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  recentChip: { flexDirection: 'row', alignItems: 'center', gap: spacing.x2, paddingHorizontal: spacing.x3, paddingVertical: spacing.x2, borderRadius: radii.compact, borderWidth: 1 },
  resultsList: { padding: spacing.x4, gap: spacing.x3 },
  resultCard: { padding: spacing.x4, borderRadius: radii.card, borderWidth: 1, gap: spacing.x2 },
  resultTypeBadge: { flexDirection: 'row', gap: spacing.x2, alignItems: 'center' },
  resultTitle: { ...typography.headline, fontSize: 16 },
  resultFooter: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 4 },
  pressed: { opacity: 0.85 },
});
