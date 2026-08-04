import { useRouter } from 'expo-router';
import React, { useEffect, useMemo, useState } from 'react';
import { StyleSheet, View, useWindowDimensions, type DimensionValue } from 'react-native';

import type { AppIconName } from '@/components/app-icon';
import {
  ActionButton,
  AppBar,
  FeedbackBanner,
  MetricCard,
  RoleBadge,
  SectionHeader,
} from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { OrderIncomingAlert } from '@/components/order-incoming-alert';
import { ThemedText } from '@/components/themed-text';
import { AppCard } from '@/components/ui/app-card';
import { useAuth } from '@/context/AuthContext';
import { spacing, typography } from '@/design/tokens';
import { playMerchantOrderAlertSound } from '@/hooks/usePushNotifications';
import { useTheme } from '@/hooks/use-theme';
import { useTranslation } from '@/i18n';
import { fetchUnreadMerchantAlerts, markAlertRead } from '@/services/notifications';
import { appConfig } from '@/utils/app-config';

const ACTIONS = [
  { id: 'approval', labelKey: 'home.providerApproval', valueKey: 'home.providerApprovalValue', tone: 'warning', icon: 'shield' },
  { id: 'catalog', labelKey: 'home.catalogReadiness', valueKey: 'home.catalogReadinessValue', tone: 'success', icon: 'inventory' },
  { id: 'orders', labelKey: 'home.openOrders', valueKey: 'home.openOrdersValue', tone: 'primary', icon: 'cart' },
  { id: 'billing', labelKey: 'home.posSync', valueKey: 'home.posSyncValue', tone: 'success', icon: 'wallet' },
] as const;

const LIVE_TASK_KEYS = ['home.task1', 'home.task2', 'home.task3', 'home.task4'] as const;

type QuickAction = {
  labelKey: string;
  icon: AppIconName;
  route: string;
};

export default function Index() {
  const { width } = useWindowDimensions();
  const theme = useTheme();
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

  const workspaceLabel =
    activeRole === 'ADMIN'
      ? t('home.adminRoom')
      : activeRole === 'PROVIDER'
        ? t('home.merchantRoom')
        : t('home.captainHome');

  const roleBadge = activeRole === 'ADMIN' ? 'admin' : activeRole === 'PROVIDER' ? 'merchant' : 'captain';
  const cardWidth: DimensionValue = width >= 920 ? '23.5%' : width >= 620 ? '48%' : '100%';

  const quickActions = useMemo<QuickAction[]>(() => {
    const shared: QuickAction[] = [
      { labelKey: 'home.messages', icon: 'message', route: '/chat' },
      { labelKey: 'home.payouts', icon: 'wallet', route: '/earnings' },
      { labelKey: 'home.legal', icon: 'shield', route: '/legal' },
    ];

    if (activeRole === 'ADMIN') {
      return [
        { labelKey: 'home.superAdmin', icon: 'shield', route: '/admin' },
        { labelKey: 'home.bookings', icon: 'calendar', route: '/explore' },
        { labelKey: 'home.inventory', icon: 'inventory', route: '/inventory' },
        ...shared,
      ];
    }

    if (activeRole === 'PROVIDER') {
      return [
        { labelKey: 'home.onboarding', icon: 'store', route: '/onboarding' },
        { labelKey: 'home.openOrders', icon: 'cart', route: '/orders' },
        { labelKey: 'home.inventory', icon: 'inventory', route: '/inventory' },
        { labelKey: 'home.bookings', icon: 'calendar', route: '/explore' },
        ...shared,
        ...(role === 'ADMIN' || appConfig.allowDemoMode
          ? [{ labelKey: 'home.superAdmin', icon: 'shield' as const, route: '/admin' }]
          : []),
      ];
    }

    return [
      { labelKey: 'home.onboarding', icon: 'truck', route: '/captain-onboarding' },
      { labelKey: 'home.bookings', icon: 'truck', route: '/delivery' },
      ...shared,
    ];
  }, [activeRole, role]);

  return (
    <ScreenShell
      header={
        <AppBar
          eyebrow={workspaceLabel}
          title={greeting}
          subtitle={user?.email ?? t('home.signedInOperator')}
          action={<RoleBadge role={roleBadge} />}
        />
      }
      testID="operational-home"
    >
      <FeedbackBanner
        tone={appConfig.allowDemoMode ? 'warning' : 'success'}
        title={appConfig.allowDemoMode ? t('common.demo') : t('common.live')}
        message={
          appConfig.allowDemoMode
            ? 'Demo data is enabled for this workspace. Live actions remain clearly separated.'
            : 'Connected to the live MyPet operational services.'
        }
        icon={appConfig.allowDemoMode ? 'sparkle' : 'check'}
      />

      {activeRole === 'PROVIDER' && incomingOrder ? (
        <OrderIncomingAlert
          visible
          orderId={incomingOrder.id}
          amount={incomingOrder.amount}
          onAccept={() => {
            setIncomingOrder(null);
            router.push('/orders' as never);
          }}
          onDismiss={() => setIncomingOrder(null)}
        />
      ) : null}

      <View style={styles.metricGrid}>
        {ACTIONS.map((action) => (
          <MetricCard
            key={action.id}
            label={t(action.labelKey)}
            value={t(action.valueKey)}
            tone={action.tone}
            icon={action.icon}
            style={{ width: cardWidth }}
          />
        ))}
      </View>

      <AppCard style={styles.sectionCard}>
        <SectionHeader title={t('home.today')} subtitle={t('home.operationalChecklist')} />
        <View style={styles.taskList}>
          {LIVE_TASK_KEYS.map((taskKey, index) => (
            <View key={taskKey} style={styles.taskRow} accessible accessibilityLabel={`${index + 1}. ${t(taskKey)}`}>
              <View style={[styles.taskNumber, { backgroundColor: theme.primarySoft }]}>
                <ThemedText type="smallBold">{index + 1}</ThemedText>
              </View>
              <ThemedText style={styles.taskText}>{t(taskKey)}</ThemedText>
            </View>
          ))}
        </View>
      </AppCard>

      <View style={styles.sectionStack}>
        <SectionHeader title="Workspace shortcuts" subtitle="Role-safe actions for the current operational mode" />
        <View style={styles.quickActions}>
          {activeRole === 'PROVIDER' ? (
            <ActionButton
              label="Write health guide"
              icon="document"
              variant="secondary"
              onPress={() => router.push('/guides' as never)}
              style={styles.quickAction}
            />
          ) : null}
          {quickActions.map((item) => (
            <ActionButton
              key={`${item.labelKey}-${item.route}`}
              label={t(item.labelKey)}
              icon={item.icon}
              variant="secondary"
              onPress={() => router.push(item.route as never)}
              style={styles.quickAction}
            />
          ))}
        </View>
      </View>

      <FeedbackBanner
        title={t('home.launchReadiness')}
        message={t('home.launchReadinessBody')}
        tone="info"
        icon="shield"
      />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  metricGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.x3,
  },
  sectionCard: {
    padding: spacing.x4,
    gap: spacing.x4,
  },
  sectionStack: {
    gap: spacing.x3,
  },
  taskList: {
    gap: spacing.x2,
  },
  taskRow: {
    minHeight: 52,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.x3,
  },
  taskNumber: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  taskText: {
    flex: 1,
    ...typography.body,
  },
  quickActions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.x2,
  },
  quickAction: {
    flexGrow: 1,
    flexBasis: 180,
  },
});
