import React, { useState } from 'react';
import { StyleSheet, TextInput, View, type TextInputProps } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type TextFieldProps = TextInputProps & {
  label?: string;
  hint?: string;
  error?: string;
};

export function TextField({
  label,
  hint,
  error,
  onFocus,
  onBlur,
  accessibilityLabel,
  ...props
}: TextFieldProps) {
  const theme = useTheme();
  const [focused, setFocused] = useState(false);
  const supportingText = error ?? hint;

  return (
    <View style={styles.wrapper}>
      {label ? (
        <ThemedText type="smallBold" style={{ color: error ? theme.danger : theme.textSecondary }}>
          {label}
        </ThemedText>
      ) : null}
      <TextInput
        placeholderTextColor={theme.textSecondary}
        accessibilityLabel={accessibilityLabel ?? label ?? props.placeholder}
        accessibilityHint={error ?? hint ?? props.accessibilityHint}
        style={[
          styles.input,
          {
            backgroundColor: theme.backgroundElement,
            borderColor: error ? theme.danger : focused ? theme.primary : theme.border,
            color: theme.text,
          },
        ]}
        onFocus={(event) => {
          setFocused(true);
          onFocus?.(event);
        }}
        onBlur={(event) => {
          setFocused(false);
          onBlur?.(event);
        }}
        {...props}
      />
      {supportingText ? (
        <ThemedText
          type="small"
          style={[styles.supportingText, { color: error ? theme.danger : theme.textSecondary }]}
          accessibilityLiveRegion={error ? 'polite' : 'none'}
        >
          {supportingText}
        </ThemedText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: { gap: Spacing.one },
  input: {
    minHeight: 52,
    borderRadius: Radius.md,
    borderWidth: 1,
    paddingHorizontal: Spacing.three,
    fontSize: 16,
  },
  supportingText: {
    paddingHorizontal: Spacing.one,
  },
});
