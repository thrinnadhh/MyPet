import React from 'react';
import type { StyleProp, ViewStyle } from 'react-native';

import { ActionButton, type ActionVariant } from '@/components/foundation/primitives';

type ButtonVariant = Extract<ActionVariant, 'primary' | 'secondary' | 'ghost'>;

export function PrimaryButton({
  label,
  onPress,
  disabled,
  loading,
  variant = 'primary',
  style,
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  loading?: boolean;
  variant?: ButtonVariant;
  style?: StyleProp<ViewStyle>;
}) {
  return (
    <ActionButton
      label={label}
      onPress={onPress}
      disabled={disabled}
      loading={loading}
      variant={variant}
      style={style}
    />
  );
}
