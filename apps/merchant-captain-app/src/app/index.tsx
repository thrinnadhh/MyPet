import React, { useEffect, useMemo, useState } from 'react';
import { ScrollView, StyleSheet, TouchableOpacity, useColorScheme, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';

import { AppIcon } from '@/components/app-icon';
import { OrderIncomingAlert } from '@/components/order-incoming-alert';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Colors, Radius, Shadows, Spacing } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';
import { useTranslation } from '@/i18n';
import { appConfig } from '@/utils/app-config';
import { fetchUnreadMerchantAlerts, markAlertRead } from '@/services/notifications';
import { playMerchantOrderAlertSound } from '@/hooks/usePushNotifications';

const ACTIONS = [
  { id: 'approval', labelKey: 'home.providerApproval', valueKey: 'home.providerApprovalValue', tone: 'warning' },
  { id: 'catalog', labelKey: 'home.catalogReadiness', valueKey: 'home.catalogReadinessValue', tone: 'success' },
  { id: 'orders', labelKey: 'home.openOrders', valueKey: 'home.openOrdersValue', tone: 'cta' },
  { id: 'billing', labelKey: 'home.posSync', valueKey: 'home.posSyncValue', tone: 'success' },
] as const;

const LIVE_TASK_KEYS = [
  'home.task1',
  'home.task2',
  'home.task3',
  'home.task4',
] as const;

export default function Index() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];
  const { user, role, activeRole, session } = useAuth();
  const { t } = useTranslation();
  const router = useRouter();
  const [incomingOrder, setIncomingOrder] = useState<{ id: string; amount: string } | null>(null);

  useEffect(() => {
    if (activeRole !== 'PROVIDER') return undefined;

    const poll = async () => {
      if (appConfig.allowDemoMode) return;
      const alerts = await fetchUnreadMerchantAlerts(session?.access_token);
      const next = alerts[0];
      if (!next) return;
      setIncomingOrder({
        id: next.referenceId ?? next.notificationId,
        amount: next.body.includes('₹') ? next.body.split('·')[1]?.trim() ?? '' : '',
      });
      void playMerchantOrderAlertSound();
      await markAlertRead(next.notificationId, session?.access_token);
    };

    void poll();
    const interval = setInterval(() => void poll(), 8000);
    return () => clearInterval(interval);
  }, [activeRole, session?.access_token]);

  useEffect(() => {
    if (activeRole !== 'PROVIDER' || !appConfig.allowDemoMode) return undefined;
    const timer = setTimeout(() => {
      setIncomingOrder({ id: 'ord-demo-8842', amount: '₹1,240' });
    }, 4000);
    return () => clearTimeout(timer);
  }, [activeRole]);

  const greeting = useMemo(() => {
    const hour = new Date().getHours();
    if (hour < 12) return t('home.greetingMorning');
    if (hour < 17) return t('home.greetingAfternoon');
    return t('home.greetingEvening');
  }, [t]);

  const toneColor = (tone: (typeof ACTIONS)[number]['tone']) => {
    if (tone === 'warning') return colors.warning;
    if (tone === 'success') return colors.success;
    return colors.cta;
  };

  return (
    <ThemedView style={[styles.container, { backgroundColor: colors.background }]}>
      <SafeAreaView style={styles.safeArea}>
        <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>
          <View style={[styles.hero, { backgroundColor: colors.primarySoft, borderColor: colors.border }]}>
            <View style={styles.heroCopy}>
              <ThemedText type="small" style={{ color: colors.textSecondary, fontWeight: '800' }}>
              {activeRole === 'ADMIN'
                ? t('home.adminRoom')
                : activeRole === 'PROVIDER'
                  ? t('home.merchantRoom')
                  : t('home.captainHome')}
              </ThemedText>
              <ThemedText style={[styles.heroTitle, { color: colors.text }]}>
                {greeting}
              </ThemedText>
              <ThemedText type="small" style={{ color: colors.textSecondary }} numberOfLines={1}>
                {user?.email ?? t('home.signedInOperator')}
              </ThemedText>
            </View>
            <View style={[styles.liveBadge, { backgroundColor: colors.muted }]}>
              <View style={[styles.liveDot, { backgroundColor: appConfig.allowDemoMode ? colors.warning : colors.success }]} />
              <ThemedText type="small" style={{ color: colors.text, fontWeight: '900' }}>
                {appConfig.allowDemoMode ? t('common.demo') : t('common.live')}
              </ThemedText>
            </View>
          </View>

          {activeRole === 'PROVIDER' && incomingOrder ? (
            <OrderIncomingAlert
              visible
              orderId={incomingOrder.id}
              amount={incomingOrder.amount}
              onAccept={() => {
                setIncomingOrder(null);
                router.push('/explore' as never);
              }}
              onDismiss={() => setIncomingOrder(null)}
            />
          ) : null}

          <View style={styles.metricsGrid}>
            {ACTIONS.map((action) => (
              <View key={action.id} style={[styles.metricCard, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
                <ThemedText type="small" style={{ color: colors.textSecondary }}>{t(action.labelKey)}</ThemedText>
                <ThemedText style={[styles.metricValue, { color: toneColor(action.tone) }]}>{t(action.valueKey)}</ThemedText>
              </View>
            ))}
          </View>

          <View style={[styles.panel, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
            <View style={styles.panelHeader}>
              <View>
                <ThemedText style={[styles.panelTitle, { color: colors.text }]}>{t('home.today')}</ThemedText>
                <ThemedText type="small" style={{ color: colors.textSecondary }}>{t('home.operationalChecklist')}</ThemedText>
              </View>
              <AppIcon name="calendar" color={colors.primary} size={22} />
            </View>
            {LIVE_TASK_KEYS.map((taskKey, index) => (
              <View key={taskKey} style={styles.taskRow}>
                <View style={[styles.taskNumber, { backgroundColor: colors.muted }]}>
                  <ThemedText type="small" style={{ color: colors.text, fontWeight: '900' }}>{index + 1}</ThemedText>
                </View>
                <ThemedText style={{ color: colors.text, flex: 1 }}>{t(taskKey)}</ThemedText>
              </View>
            ))}
          </View>

          <View style={styles.quickActions}>
            {[
              { labelKey: 'home.onboarding', icon: 'store', route: '/onboarding' },
              { labelKey: 'home.healthGuides', icon: 'shield', route: '/inventory' },
              { labelKey: 'home.inventory', icon: 'cart', route: '/inventory' },
              { labelKey: 'home.bookings', icon: 'calendar', route: '/explore' },
              { labelKey: 'home.messages', icon: 'message', route: '/explore' },
              { labelKey: 'home.payouts', icon: 'wallet', route: '/earnings' },
              { labelKey: 'home.legal', icon: 'shield', route: '/legal' },
              ...(role === 'ADMIN' || appConfig.allowDemoMode
                ? [{ labelKey: 'home.superAdmin', icon: 'shield', route: '/admin' }]
                : []),
            ].map((item) => (
              <TouchableOpacity
                key={item.labelKey}
                style={[styles.quickAction, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}
                activeOpacity={0.75}
                onPress={() => router.push(item.route as never)}
                accessibilityRole="button"
                accessibilityLabel={t(item.labelKey)}
              >
                <AppIcon name={item.icon as never} color={colors.cta} size={20} />
                <ThemedText type="small" style={{ color: colors.text, fontWeight: '900' }}>{t(item.labelKey)}</ThemedText>
              </TouchableOpacity>
            ))}
          </View>

          <View style={[styles.launchPanel, { backgroundColor: colors.muted }]}>
            <ThemedText style={[styles.panelTitle, { color: colors.text }]}>{t('home.launchReadiness')}</ThemedText>
            <ThemedText type="small" style={{ color: colors.textSecondary }}>
              {t('home.launchReadinessBody')}
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
