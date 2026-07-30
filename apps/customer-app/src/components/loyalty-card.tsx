import React, { useState } from 'react';
import { Alert, Pressable, StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { StatusBadge } from '@/components/foundation/primitives';
import { ThemedText } from '@/components/themed-text';
import { radii, shadows, spacing, typography } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';
import { claimWelcomeStar, type LoyaltyProgressDto } from '@/services/loyalty';

interface LoyaltyCardProps {
  progress: LoyaltyProgressDto;
  accessToken?: string | null;
  onProgressUpdated?: (updated: LoyaltyProgressDto) => void;
}

export function LoyaltyCard({ progress, accessToken, onProgressUpdated }: LoyaltyCardProps) {
  const theme = useTheme();
  const [claiming, setClaiming] = useState(false);

  const handleClaimWelcomeStar = async () => {
    if (!accessToken || claiming) return;
    setClaiming(true);
    try {
      const updated = await claimWelcomeStar(progress.providerId, accessToken);
      Alert.alert('Welcome Star Claimed! ⭐', 'Your first star has been added to your loyalty card.');
      onProgressUpdated?.(updated);
    } catch (err: any) {
      Alert.alert('Claim Failed', err.message || 'Could not claim welcome star.');
    } finally {
      setClaiming(false);
    }
  };

  const stars = Array.from({ length: progress.targetStars || 10 }, (_, i) => i < progress.starBalance);

  return (
    <View
      style={[
        styles.card,
        shadows.card,
        { backgroundColor: theme.backgroundElement, borderColor: theme.border },
      ]}
      testID="loyalty-card"
    >
      <View style={styles.header}>
        <View style={styles.titleRow}>
          <AppIcon name="sparkle" size={20} color={theme.primary} />
          <ThemedText style={styles.cardTitle}>Store Loyalty Rewards</ThemedText>
        </View>
        <StatusBadge
          label={progress.isProgramActive ? `${progress.starBalance}/${progress.targetStars} Stars` : 'Paused'}
          tone={progress.isProgramActive ? 'success' : 'neutral'}
        />
      </View>

      {/* 10 Visible Star Icons Grid */}
      <View style={styles.starsGrid}>
        {stars.map((filled, idx) => (
          <View
            key={idx}
            style={[
              styles.starBubble,
              {
                backgroundColor: filled ? theme.primary : theme.background,
                borderColor: filled ? theme.primary : theme.border,
              },
            ]}
          >
            <AppIcon
              name="sparkle"
              size={16}
              color={filled ? '#FFFFFF' : theme.textSecondary}
            />
          </View>
        ))}
      </View>

      <ThemedText style={styles.rewardCopy}>
        Collect {progress.targetStars} stars to get ₹{progress.rewardAmount} off your next order!
      </ThemedText>

      <View style={styles.rulesRow}>
        <ThemedText type="small" themeColor="textSecondary">
          • Min order amount: ₹{progress.minOrderValue}
        </ThemedText>
        {progress.cycleCount > 0 ? (
          <ThemedText type="small" style={{ color: theme.primary, fontWeight: '700' }}>
            • Completed cycles: {progress.cycleCount}
          </ThemedText>
        ) : null}
      </View>

      {/* Welcome Star Action */}
      {!progress.welcomeStarClaimed && progress.isProgramActive && accessToken ? (
        <Pressable
          style={[
            styles.claimBtn,
            { backgroundColor: theme.primary, opacity: claiming ? 0.6 : 1 },
          ]}
          onPress={() => void handleClaimWelcomeStar()}
          disabled={claiming}
          accessibilityLabel="Add your first welcome star"
        >
          <AppIcon name="sparkle" size={16} color="#FFF" />
          <ThemedText style={styles.claimBtnText}>
            {claiming ? 'Claiming...' : 'Add your first star (+1 ⭐)'}
          </ThemedText>
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  card: { borderWidth: StyleSheet.hairlineWidth, borderRadius: radii.card, padding: spacing.x4, gap: spacing.x3 },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.x2 },
  cardTitle: { ...typography.label, fontWeight: '700' },
  starsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2, justifyContent: 'center', marginVertical: spacing.x2 },
  starBubble: { width: 36, height: 36, borderRadius: 18, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  rewardCopy: { ...typography.body, fontWeight: '600', textAlign: 'center' },
  rulesRow: { flexDirection: 'row', justifyContent: 'space-around', alignItems: 'center' },
  claimBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: spacing.x2, paddingVertical: spacing.x3, borderRadius: radii.compact, marginTop: spacing.x1 },
  claimBtnText: { color: '#FFF', fontWeight: '800', fontSize: 14 },
});
