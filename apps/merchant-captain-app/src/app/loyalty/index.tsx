import React, { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Switch, TextInput, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { ThemedText } from '@/components/themed-text';
import { PrimaryButton } from '@/components/ui/primary-button';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { appConfig } from '@/utils/app-config';

interface LoyaltyProgramDto {
  programId?: string;
  providerId: string;
  targetStars: number;
  rewardAmount: number;
  minOrderValue: number;
  welcomeStarPolicy: boolean;
  isActive: boolean;
  isStackable: boolean;
  expiryDays: number;
}

export default function MerchantLoyaltyScreen() {
  const theme = useTheme();
  const dummyProviderId = '11111111-1111-1111-1111-111111111111';

  const [program, setProgram] = useState<LoyaltyProgramDto>({
    providerId: dummyProviderId,
    targetStars: 10,
    rewardAmount: 50,
    minOrderValue: 199,
    welcomeStarPolicy: true,
    isActive: true,
    isStackable: false,
    expiryDays: 60,
  });

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const loadProgram = useCallback(async () => {
    setLoading(true);
    try {
      const response = await fetch(
        `${appConfig.apiBaseUrl}/api/v1/loyalty/programs?providerId=${dummyProviderId}`
      );
      if (response.ok) {
        const data = await response.json();
        setProgram(data);
      }
    } catch {
      // Fallback
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadProgram();
  }, [loadProgram]);

  const handleSave = async () => {
    setSaving(true);
    try {
      const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/loyalty/programs`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-User-Role': 'MERCHANT',
          'X-User-Id': '22222222-2222-2222-2222-222222222222',
        },
        body: JSON.stringify(program),
      });

      if (!response.ok) {
        throw new Error('Could not save loyalty program settings');
      }

      const updated = await response.json();
      setProgram(updated);
      Alert.alert('Settings Saved', 'Loyalty program settings updated successfully!');
    } catch (err: any) {
      Alert.alert('Error', err.message || 'Failed to save settings');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <View style={[styles.center, { backgroundColor: theme.background }]}>
        <ActivityIndicator size="large" color={theme.primary} />
        <ThemedText style={{ marginTop: 12 }}>Loading program settings...</ThemedText>
      </View>
    );
  }

  return (
    <ScrollView style={{ flex: 1, backgroundColor: theme.background }} contentContainerStyle={styles.container}>
      <View style={styles.header}>
        <AppIcon name="sparkle" size={28} color={theme.primary} />
        <ThemedText style={styles.title}>Store Loyalty Management</ThemedText>
      </View>

      {/* Metrics Summary */}
      <View style={[styles.metricsGrid, Shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
        <View style={styles.metricCell}>
          <ThemedText style={styles.metricVal}>10 Stars</ThemedText>
          <ThemedText style={{ color: theme.textSecondary, fontSize: 12 }}>Target Stars</ThemedText>
        </View>
        <View style={styles.metricCell}>
          <ThemedText style={[styles.metricVal, { color: theme.primary }]}>₹{program.rewardAmount}</ThemedText>
          <ThemedText style={{ color: theme.textSecondary, fontSize: 12 }}>Reward Off</ThemedText>
        </View>
        <View style={styles.metricCell}>
          <ThemedText style={styles.metricVal}>₹{program.minOrderValue}</ThemedText>
          <ThemedText style={{ color: theme.textSecondary, fontSize: 12 }}>Min Order</ThemedText>
        </View>
      </View>

      {/* Program Status Controls */}
      <View style={[styles.card, Shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
        <View style={styles.rowBetween}>
          <View style={{ gap: 2 }}>
            <ThemedText style={styles.cardTitle}>Loyalty Program Status</ThemedText>
            <ThemedText style={{ color: theme.textSecondary, fontSize: 12 }}>
              {program.isActive ? 'Active & awarding stars to customers' : 'Program Paused'}
            </ThemedText>
          </View>
          <Switch
            value={program.isActive}
            onValueChange={(val) => setProgram((prev) => ({ ...prev, isActive: val }))}
          />
        </View>

        <View style={[styles.divider, { backgroundColor: theme.border }]} />

        <View style={styles.rowBetween}>
          <View style={{ gap: 2 }}>
            <ThemedText style={styles.cardTitle}>Welcome Star (+1 ⭐)</ThemedText>
            <ThemedText style={{ color: theme.textSecondary, fontSize: 12 }}>
              Allow new customers to claim 1st welcome star
            </ThemedText>
          </View>
          <Switch
            value={program.welcomeStarPolicy}
            onValueChange={(val) => setProgram((prev) => ({ ...prev, welcomeStarPolicy: val }))}
          />
        </View>
      </View>

      {/* Reward Amount Selector */}
      <View style={[styles.card, Shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
        <ThemedText style={styles.cardTitle}>Reward Amount (₹)</ThemedText>
        <View style={styles.chipRow}>
          <Pressable
            onPress={() => setProgram((prev) => ({ ...prev, rewardAmount: 50 }))}
            style={[
              styles.chip,
              {
                backgroundColor: program.rewardAmount === 50 ? theme.primarySoft : theme.background,
                borderColor: program.rewardAmount === 50 ? theme.primary : theme.border,
              },
            ]}
          >
            <ThemedText style={{ color: program.rewardAmount === 50 ? theme.primary : theme.text, fontWeight: '700' }}>
              ₹50 Reward
            </ThemedText>
          </Pressable>
          <Pressable
            onPress={() => setProgram((prev) => ({ ...prev, rewardAmount: 100 }))}
            style={[
              styles.chip,
              {
                backgroundColor: program.rewardAmount === 100 ? theme.primarySoft : theme.background,
                borderColor: program.rewardAmount === 100 ? theme.primary : theme.border,
              },
            ]}
          >
            <ThemedText style={{ color: program.rewardAmount === 100 ? theme.primary : theme.text, fontWeight: '700' }}>
              ₹100 Reward
            </ThemedText>
          </Pressable>
        </View>
      </View>

      {/* Minimum Order Value */}
      <View style={[styles.card, Shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
        <ThemedText style={styles.cardTitle}>Minimum Order Amount (₹)</ThemedText>
        <TextInput
          value={String(program.minOrderValue)}
          onChangeText={(val) => setProgram((prev) => ({ ...prev, minOrderValue: Number(val) || 0 }))}
          keyboardType="numeric"
          style={[styles.input, { color: theme.text, borderColor: theme.border }]}
          placeholder="199"
        />
      </View>

      {/* Expiry Days */}
      <View style={[styles.card, Shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
        <ThemedText style={styles.cardTitle}>Reward Expiry (Days)</ThemedText>
        <View style={styles.chipRow}>
          {[30, 60, 90].map((days) => (
            <Pressable
              key={days}
              onPress={() => setProgram((prev) => ({ ...prev, expiryDays: days }))}
              style={[
                styles.chip,
                {
                  backgroundColor: program.expiryDays === days ? theme.primarySoft : theme.background,
                  borderColor: program.expiryDays === days ? theme.primary : theme.border,
                },
              ]}
            >
              <ThemedText style={{ color: program.expiryDays === days ? theme.primary : theme.text, fontWeight: '700' }}>
                {days} Days
              </ThemedText>
            </Pressable>
          ))}
        </View>
      </View>

      <PrimaryButton
        label="Save Program Settings"
        onPress={() => void handleSave()}
        loading={saving}
      />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  container: { padding: Spacing.four, gap: Spacing.three, paddingBottom: Spacing.six },
  header: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two, marginVertical: Spacing.two },
  title: { fontSize: 22, fontWeight: '800' },
  metricsGrid: { flexDirection: 'row', justifyContent: 'space-around', padding: Spacing.three, borderRadius: Radius.lg, borderWidth: 1 },
  metricCell: { alignItems: 'center', gap: 2 },
  metricVal: { fontSize: 18, fontWeight: '800' },
  card: { borderWidth: 1, borderRadius: Radius.lg, padding: Spacing.three, gap: Spacing.two },
  cardTitle: { fontSize: 14, fontWeight: '700' },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  divider: { height: 1, marginVertical: Spacing.one },
  chipRow: { flexDirection: 'row', gap: Spacing.two, marginTop: Spacing.one },
  chip: { paddingHorizontal: 16, paddingVertical: 10, borderRadius: Radius.md, borderWidth: 1 },
  input: { borderWidth: 1, borderRadius: Radius.md, paddingHorizontal: Spacing.three, height: 44, marginTop: Spacing.one },
});
