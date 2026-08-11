import React, { useEffect, useMemo } from 'react';
import { Animated, Pressable, StyleSheet, Vibration, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export function OrderIncomingAlert({
  visible,
  orderId,
  amount,
  onView,
  onDismiss,
}: {
  visible: boolean;
  orderId: string;
  amount: string;
  onView: () => void;
  onDismiss: () => void;
}) {
  const theme = useTheme();
  const pulse = useMemo(() => new Animated.Value(1), []);

  useEffect(() => {
    if (!visible) return undefined;
    Vibration.vibrate([0, 400, 200, 400, 200, 600]);
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(pulse, { toValue: 1.04, duration: 500, useNativeDriver: true }),
        Animated.timing(pulse, { toValue: 1, duration: 500, useNativeDriver: true }),
      ]),
    );
    loop.start();
    return () => loop.stop();
  }, [pulse, visible]);

  if (!visible) return null;

  return (
    <Animated.View style={[styles.wrap, { transform: [{ scale: pulse }] }]}>
      <View style={[styles.alert, { backgroundColor: theme.danger, borderColor: theme.border }]}>
        <View style={styles.row}>
          <AppIcon name="cart" color="#FFFFFF" size={24} />
          <View style={{ flex: 1, gap: 2 }}>
            <ThemedText style={styles.title}>New order received!</ThemedText>
            <ThemedText style={styles.subtitle}>
              Order #{orderId.slice(0, 8)} · {amount}
            </ThemedText>
            <ThemedText style={styles.hint}>Review this order before accepting or rejecting it</ThemedText>
          </View>
        </View>
        <View style={styles.actions}>
          <Pressable
            onPress={onView}
            style={[styles.accept, { backgroundColor: '#FFFFFF' }]}
            accessibilityRole="button"
            accessibilityLabel="View order"
          >
            <ThemedText style={{ color: theme.danger, fontWeight: '900' }}>View order</ThemedText>
          </Pressable>
          <Pressable onPress={onDismiss} accessibilityRole="button" accessibilityLabel="Dismiss alert">
            <ThemedText style={{ color: '#FFFFFF', fontWeight: '700' }}>Dismiss</ThemedText>
          </Pressable>
        </View>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  wrap: { marginBottom: Spacing.two },
  alert: {
    borderRadius: Radius.lg,
    borderWidth: 1,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  row: { flexDirection: 'row', gap: Spacing.three, alignItems: 'center' },
  title: { color: '#FFFFFF', fontSize: 18, fontWeight: '900' },
  subtitle: { color: '#FEE2E2', fontWeight: '700' },
  hint: { color: 'rgba(255,255,255,0.85)', fontSize: 12, fontWeight: '600' },
  actions: { flexDirection: 'row', alignItems: 'center', gap: Spacing.three },
  accept: {
    minHeight: 44,
    paddingHorizontal: Spacing.four,
    borderRadius: Radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
