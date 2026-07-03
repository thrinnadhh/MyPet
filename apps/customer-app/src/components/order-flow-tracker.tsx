import React from 'react';
import { StyleSheet, View } from 'react-native';

import { AppIcon } from '@/components/app-icon';
import { ThemedText } from '@/components/themed-text';
import { ORDER_FLOW_STEPS, type OrderFlowStepId } from '@/constants/content';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const STEP_ORDER: OrderFlowStepId[] = ORDER_FLOW_STEPS.map((s) => s.id);

export function OrderFlowTracker({ currentStep }: { currentStep: OrderFlowStepId }) {
  const theme = useTheme();
  const currentIndex = STEP_ORDER.indexOf(currentStep);

  return (
    <View style={styles.container}>
      {ORDER_FLOW_STEPS.map((step, index) => {
        const done = index <= currentIndex;
        const active = index === currentIndex;
        return (
          <View key={step.id} style={styles.row}>
            <View
              style={[
                styles.dot,
                {
                  backgroundColor: done ? theme.primary : theme.muted,
                  borderColor: active ? theme.primary : theme.border,
                },
              ]}
            >
              {done ? <AppIcon name="check" color="#FFFFFF" size={12} /> : null}
            </View>
            <ThemedText
              type="small"
              style={{ fontWeight: active ? '900' : '600', color: done ? theme.text : theme.textSecondary }}
            >
              {step.label}
            </ThemedText>
          </View>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: Spacing.one },
  row: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  dot: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
