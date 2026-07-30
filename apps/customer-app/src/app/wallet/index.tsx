import React, { useCallback, useEffect, useState } from 'react';
import { Alert, FlatList, Pressable, StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { AppBar, FilterChip, StateView, StatusBadge } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { useAuth } from '@/context/AuthContext';
import { useAuthIntent } from '@/context/AuthIntentContext';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { isOfflineError } from '@/services/customer-profile';
import { fetchCustomerWallet, type LoyaltyRewardDto } from '@/services/loyalty';

export default function WalletScreen() {
  const theme = useTheme();
  const { user, session } = useAuth();
  const { requireAuth } = useAuthIntent();

  const [rewards, setRewards] = useState<LoyaltyRewardDto[]>([]);
  const [tab, setTab] = useState<'rewards' | 'coupons'>('rewards');
  const [state, setState] = useState<'loading' | 'ready' | 'offline' | 'error'>('loading');

  const loadData = useCallback(async () => {
    if (!user || !session) return;
    setState('loading');
    try {
      const data = await fetchCustomerWallet(session.access_token);
      setRewards(data);
      setState('ready');
    } catch (err) {
      setState(isOfflineError(err) ? 'offline' : 'error');
    }
  }, [session, user]);

  useEffect(() => {
    if (user && session) void loadData();
  }, [loadData, session, user]);

  if (!user || !session) {
    return (
      <ScreenShell scroll={false} header={<AppBar title="My Loyalty & Wallet" />}>
        <StateView
          kind="unauthenticated"
          title="Sign in to view your wallet"
          message="View your earned store rewards, discounts, and active coupons."
          actionLabel="Sign In"
          onAction={() => void requireAuth({ action: 'CHECKOUT', returnTo: '/wallet' })}
        />
      </ScreenShell>
    );
  }

  if (state === 'loading') {
    return (
      <ScreenShell scroll={false} header={<AppBar title="My Loyalty & Wallet" />}>
        <StateView kind="loading" title="Loading wallet items..." />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell header={<AppBar title="My Loyalty & Wallet" subtitle="Rewards & Promotions" />}>
      <View style={styles.container}>
        {/* Non-Stacking Policy Banner */}
        <View style={[styles.policyBanner, { backgroundColor: theme.primarySoft }]}>
          <AppIcon name="sparkle" size={18} color={theme.primary} />
          <ThemedText type="small" style={{ color: theme.primary, flex: 1, fontWeight: '600' }}>
            Store rewards and promo coupons apply one per checkout order to maximize savings.
          </ThemedText>
        </View>

        {/* Tab Selector */}
        <View style={styles.tabBar}>
          <FilterChip
            label={`Store Rewards (${rewards.length})`}
            selected={tab === 'rewards'}
            onPress={() => setTab('rewards')}
          />
          <FilterChip
            label="Available Coupons"
            selected={tab === 'coupons'}
            onPress={() => setTab('coupons')}
          />
        </View>

        {tab === 'rewards' ? (
          rewards.length === 0 ? (
            <StateView
              kind="empty"
              title="No Store Rewards Yet"
              message="Earn 10 stars at your favorite pet stores to unlock ₹50 & ₹100 discount rewards!"
            />
          ) : (
            <FlatList
              data={rewards}
              keyExtractor={(item) => item.rewardId}
              contentContainerStyle={styles.listContent}
              renderItem={({ item }) => (
                <View
                  style={[
                    styles.rewardCard,
                    shadows.card,
                    { backgroundColor: theme.backgroundElement, borderColor: theme.border },
                  ]}
                >
                  <View style={styles.cardTop}>
                    <View style={styles.amountCol}>
                      <ThemedText style={styles.rewardAmount}>₹{item.rewardAmount}</ThemedText>
                      <ThemedText type="small" themeColor="textSecondary">
                        Store Reward Discount
                      </ThemedText>
                    </View>
                    <StatusBadge
                      label={item.status}
                      tone={item.status === 'ISSUED' ? 'success' : 'neutral'}
                    />
                  </View>

                  <View style={[styles.codeBox, { backgroundColor: theme.background, borderColor: theme.border }]}>
                    <ThemedText style={styles.codeText}>{item.code}</ThemedText>
                    <Pressable
                      onPress={() => Alert.alert('Reward Code', `Code ${item.code} ready to use at checkout!`)}
                    >
                      <ThemedText style={{ color: theme.primary, fontWeight: '700' }}>Copy</ThemedText>
                    </Pressable>
                  </View>

                  <ThemedText type="small" themeColor="textSecondary">
                    Expires on: {new Date(item.expiresAt).toLocaleDateString()}
                  </ThemedText>
                </View>
              )}
            />
          )
        ) : (
          <View style={styles.couponsContainer}>
            <View
              style={[
                styles.rewardCard,
                shadows.card,
                { backgroundColor: theme.backgroundElement, borderColor: theme.border },
              ]}
            >
              <View style={styles.cardTop}>
                <View style={styles.amountCol}>
                  <ThemedText style={styles.rewardAmount}>FLAT ₹50 OFF</ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    Promo Coupon Code
                  </ThemedText>
                </View>
                <StatusBadge label="ACTIVE" tone="success" />
              </View>
              <View style={[styles.codeBox, { backgroundColor: theme.background, borderColor: theme.border }]}>
                <ThemedText style={styles.codeText}>SAVE50</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  Min order: ₹100
                </ThemedText>
              </View>
            </View>
          </View>
        )}
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: { padding: spacing.x4, gap: spacing.x3 },
  policyBanner: { flexDirection: 'row', alignItems: 'center', gap: spacing.x2, padding: spacing.x3, borderRadius: radii.card },
  tabBar: { flexDirection: 'row', gap: spacing.x2 },
  listContent: { gap: spacing.x3 },
  couponsContainer: { gap: spacing.x3 },
  rewardCard: { borderWidth: StyleSheet.hairlineWidth, borderRadius: radii.card, padding: spacing.x4, gap: spacing.x2 },
  cardTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  amountCol: { gap: 2 },
  rewardAmount: { ...typography.headline, color: '#10B981', fontWeight: '800' },
  codeBox: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', borderWidth: 1, borderStyle: 'dashed', borderRadius: radii.compact, padding: spacing.x3, marginVertical: spacing.x1 },
  codeText: { ...typography.title, letterSpacing: 1 },
});
