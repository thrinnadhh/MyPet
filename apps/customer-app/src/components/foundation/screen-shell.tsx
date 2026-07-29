import type { PropsWithChildren, ReactNode } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { BottomTabInset } from '@/constants/theme';
import { spacing } from '@/design/tokens';
import { useTheme } from '@/hooks/use-theme';

interface ScreenShellProps extends PropsWithChildren {
  scroll?: boolean;
  header?: ReactNode;
  footer?: ReactNode;
  testID?: string;
}

export function ScreenShell({ children, scroll = true, header, footer, testID }: ScreenShellProps) {
  const theme = useTheme();
  const content = scroll ? (
    <ScrollView
      keyboardShouldPersistTaps="handled"
      showsVerticalScrollIndicator={false}
      contentContainerStyle={[styles.content, { paddingBottom: BottomTabInset + spacing.x8 }]}
    >
      {children}
    </ScrollView>
  ) : (
    <View style={styles.fill}>{children}</View>
  );

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['top', 'left', 'right']} testID={testID}>
      <KeyboardAvoidingView style={styles.fill} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        {header}
        {content}
        {footer}
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  fill: { flex: 1 },
  content: { paddingHorizontal: spacing.x4, paddingTop: spacing.x4, gap: spacing.x6 },
});
