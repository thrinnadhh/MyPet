import React, { useState, useEffect } from 'react';
import { 
  StyleSheet, 
  View, 
  FlatList, 
  ActivityIndicator, 
  useColorScheme, 
  Platform,
  TouchableOpacity
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing, Colors } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';

const API_BASE_URL = Platform.select({
  android: 'http://10.0.2.2:8080',
  ios: 'http://localhost:8080',
  default: 'http://localhost:8080',
});

interface EarningRecord {
  earningId: string;
  captainId: string;
  orderId: string;
  amount: number;
  earnedAt: string;
}

const MOCK_EARNINGS: EarningRecord[] = [
  {
    earningId: 'earn-1',
    captainId: 'd3b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
    orderId: 'order-101',
    amount: 150.00,
    earnedAt: new Date(Date.now() - 3600 * 1000 * 3).toISOString()
  },
  {
    earningId: 'earn-2',
    captainId: 'd3b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
    orderId: 'order-102',
    amount: 150.00,
    earnedAt: new Date(Date.now() - 3600 * 1000 * 24).toISOString()
  },
  {
    earningId: 'earn-3',
    captainId: 'd3b07384-d113-4e4e-9c8e-3d8e3d8e3d8e',
    orderId: 'order-103',
    amount: 150.00,
    earnedAt: new Date(Date.now() - 3600 * 1000 * 48).toISOString()
  }
];

export default function EarningsScreen() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];
  const { user } = useAuth();

  const [earnings, setEarnings] = useState<EarningRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const fetchEarnings = async (showLoader = true) => {
    if (!user) return;
    if (showLoader) setLoading(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/v1/captains/${user.id}/earnings`);
      const data = await response.json();
      if (response.ok) {
        setEarnings(data);
      } else {
        console.warn('API returned error status, loading mock earnings.');
        setEarnings(MOCK_EARNINGS);
      }
    } catch (err) {
      console.warn('Earnings API unreachable, loading sandbox earnings.');
      setEarnings(MOCK_EARNINGS);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchEarnings();
  }, [user]);

  const handleRefresh = () => {
    setRefreshing(true);
    fetchEarnings(false);
  };

  const totalEarnings = earnings.reduce((sum, item) => sum + item.amount, 0);
  const totalDeliveries = earnings.length;

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <View style={styles.header}>
          <ThemedText type="subtitle">Earnings Dashboard</ThemedText>
          <ThemedText type="small" style={{ color: colors.textSecondary }}>
            Track your delivery payouts and stats 📈
          </ThemedText>
        </View>

        {/* Stats Grid */}
        <View style={styles.statsRow}>
          <View style={[styles.statBox, { backgroundColor: colors.backgroundElement }]}>
            <ThemedText type="small" style={{ color: colors.textSecondary }}>Total Earnings</ThemedText>
            <ThemedText style={[styles.statValue, { color: colors.cta }]}>₹{totalEarnings.toFixed(2)}</ThemedText>
          </View>

          <View style={[styles.statBox, { backgroundColor: colors.backgroundElement }]}>
            <ThemedText type="small" style={{ color: colors.textSecondary }}>Deliveries</ThemedText>
            <ThemedText style={[styles.statValue, { color: colors.primary }]}>{totalDeliveries}</ThemedText>
          </View>
        </View>

        {/* List Header */}
        <View style={styles.listHeader}>
          <ThemedText style={{ fontWeight: '700' }}>Recent Delivery Transactions</ThemedText>
        </View>

        {/* List of earnings */}
        {loading ? (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" />
          </View>
        ) : (
          <FlatList
            data={earnings}
            keyExtractor={(item) => item.earningId}
            onRefresh={handleRefresh}
            refreshing={refreshing}
            renderItem={({ item }) => (
              <View style={[styles.earningItem, { borderBottomColor: colors.backgroundSelected }]}>
                <View>
                  <ThemedText style={{ fontWeight: '600' }}>Order #{item.orderId.split('-').pop() || item.orderId}</ThemedText>
                  <ThemedText type="small" style={{ color: colors.textSecondary, marginTop: Spacing.one }}>
                    {new Date(item.earnedAt).toLocaleString()}
                  </ThemedText>
                </View>
                <ThemedText style={{ fontWeight: '700', color: colors.cta }}>
                  +₹{item.amount.toFixed(2)}
                </ThemedText>
              </View>
            )}
            ListEmptyComponent={
              <View style={styles.centered}>
                <ThemedText style={{ color: colors.textSecondary }}>No earnings recorded yet.</ThemedText>
              </View>
            }
          />
        )}
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
  },
  header: {
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.three,
    marginBottom: Spacing.three,
  },
  statsRow: {
    flexDirection: 'row',
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    marginBottom: Spacing.four,
  },
  statBox: {
    flex: 1,
    padding: Spacing.three,
    borderRadius: Spacing.two,
    gap: Spacing.one,
  },
  statValue: {
    fontSize: 22,
    fontWeight: 'bold',
  },
  listHeader: {
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.two,
  },
  earningItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.three,
    borderBottomWidth: 1,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  centered: {
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.six,
  },
});
