import React, { useMemo } from 'react';
import { ScrollView, StyleSheet, TouchableOpacity, useColorScheme, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';

import { AppIcon } from '@/components/app-icon';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Colors, Radius, Shadows, Spacing } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';
import { appConfig } from '@/utils/app-config';

const ACTIONS = [
  { id: 'approval', label: 'Provider approval', value: 'Pending proof', tone: 'warning' },
  { id: 'catalog', label: 'Catalog readiness', value: '8 active items', tone: 'success' },
  { id: 'orders', label: 'Open orders', value: '3 active', tone: 'cta' },
  { id: 'billing', label: 'POS sync', value: 'All clear', tone: 'success' },
] as const;

const LIVE_TASKS = [
  'Review today bookings and mark completed visits',
  'Check low stock before lunch peak',
  'Reconcile pending payout batch',
  'Verify support queue before closing',
];

export default function Index() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];
  const { user, activeRole } = useAuth();
  const router = useRouter();

  const greeting = useMemo(() => {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 17) return 'Good afternoon';
    return 'Good evening';
  }, []);

  const toneColor = (tone: (typeof ACTIONS)[number]['tone']) => {
    if (tone === 'warning') return colors.warning;
    if (tone === 'success') return colors.success;
    return colors.cta;
  };

  return (
    <ThemedView style={[styles.container, { backgroundColor: colors.background }]}>
      <SafeAreaView style={styles.safeArea}>
        <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>
          <View style={[styles.hero, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
            <View style={styles.heroCopy}>
              <ThemedText type="small" style={{ color: colors.textSecondary, fontWeight: '800' }}>
                {activeRole === 'PROVIDER' ? 'MERCHANT CONTROL ROOM' : 'CAPTAIN HOME'}
              </ThemedText>
              <ThemedText style={[styles.heroTitle, { color: colors.text }]}>
                {greeting}
              </ThemedText>
              <ThemedText type="small" style={{ color: colors.textSecondary }} numberOfLines={1}>
                {user?.email ?? 'Signed in operator'}
              </ThemedText>
            </View>
            <View style={[styles.liveBadge, { backgroundColor: colors.muted }]}>
              <View style={[styles.liveDot, { backgroundColor: appConfig.allowDemoMode ? colors.warning : colors.success }]} />
              <ThemedText type="small" style={{ color: colors.text, fontWeight: '900' }}>
                {appConfig.allowDemoMode ? 'Demo' : 'Live'}
              </ThemedText>
            </View>
          </View>

          <View style={styles.metricsGrid}>
            {ACTIONS.map((action) => (
              <View key={action.id} style={[styles.metricCard, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                <ThemedText type="small" style={{ color: colors.textSecondary }}>{action.label}</ThemedText>
                <ThemedText style={[styles.metricValue, { color: toneColor(action.tone) }]}>{action.value}</ThemedText>
              </View>
            ))}
          </View>

          <View style={[styles.panel, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
            <View style={styles.panelHeader}>
              <View>
                <ThemedText style={[styles.panelTitle, { color: colors.text }]}>Today</ThemedText>
                <ThemedText type="small" style={{ color: colors.textSecondary }}>Operational checklist</ThemedText>
              </View>
              <AppIcon name="calendar" color={colors.primary} size={22} />
            </View>
            {LIVE_TASKS.map((task, index) => (
              <View key={task} style={styles.taskRow}>
                <View style={[styles.taskNumber, { backgroundColor: colors.muted }]}>
                  <ThemedText type="small" style={{ color: colors.text, fontWeight: '900' }}>{index + 1}</ThemedText>
                </View>
                <ThemedText style={{ color: colors.text, flex: 1 }}>{task}</ThemedText>
              </View>
            ))}
          </View>

          <View style={styles.quickActions}>
            {[
              { label: 'Onboarding', icon: 'store', route: '/onboarding' },
              { label: 'Inventory', icon: 'cart', route: '/inventory' },
              { label: 'Bookings', icon: 'calendar', route: '/explore' },
              { label: 'Payouts', icon: 'medical', route: '/earnings' },
            ].map((item) => (
              <TouchableOpacity
                key={item.label}
                style={[styles.quickAction, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}
                activeOpacity={0.75}
                onPress={() => router.push(item.route as never)}
                accessibilityRole="button"
                accessibilityLabel={item.label}
              >
                <AppIcon name={item.icon as never} color={colors.cta} size={20} />
                <ThemedText type="small" style={{ color: colors.text, fontWeight: '900' }}>{item.label}</ThemedText>
              </TouchableOpacity>
            ))}
          </View>

          <View style={[styles.launchPanel, { backgroundColor: colors.muted }]}>
            <ThemedText style={[styles.panelTitle, { color: colors.text }]}>Launch readiness</ThemedText>
            <ThemedText type="small" style={{ color: colors.textSecondary }}>
              Legal pages, refund flow, rollback checklist, and support workflows are visible in the app shell while backend proof continues sprint by sprint.
            </ThemedText>
          </View>
        </ScrollView>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  safeArea: { flex: 1 },
  scrollContent: {
    padding: Spacing.four,
    paddingBottom: Spacing.six,
    gap: Spacing.four,
  },
  hero: {
    minHeight: 124,
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.four,
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: Spacing.three,
    ...Shadows.card,
  },
  heroCopy: { flex: 1, gap: Spacing.one },
  heroTitle: { fontSize: 28, fontWeight: '900' },
  liveBadge: {
    minHeight: 36,
    borderRadius: 18,
    paddingHorizontal: Spacing.three,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
  },
  liveDot: { width: 8, height: 8, borderRadius: 4 },
  metricsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  metricCard: {
    width: '48%',
    minHeight: 96,
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.three,
    justifyContent: 'space-between',
  },
  metricValue: { fontSize: 20, fontWeight: '900' },
  panel: {
    borderWidth: 1,
    borderRadius: Radius.lg,
    padding: Spacing.three,
    gap: Spacing.three,
  },
  panelHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  panelTitle: { fontSize: 18, fontWeight: '900' },
  taskRow: {
    minHeight: 48,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  taskNumber: {
    width: 30,
    height: 30,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
  },
  quickActions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  quickAction: {
    width: '48%',
    minHeight: 86,
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.three,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
  },
  launchPanel: {
    borderRadius: Radius.lg,
    padding: Spacing.three,
    gap: Spacing.one,
  },
});
