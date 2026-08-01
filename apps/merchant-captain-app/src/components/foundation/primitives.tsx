import type { ReactNode } from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  View,
  type StyleProp,
  type ViewStyle,
} from 'react-native';

import { AppIcon, type AppIconName } from '@/components/app-icon';
import { ThemedText } from '@/components/themed-text';
import { Shadows } from '@/constants/theme';
import {
  radii,
  roleAccent,
  spacing,
  touchTarget,
  typography,
  type MyPetScheme,
  type OperationalRole,
} from '@/design/tokens';
import { useColorScheme } from '@/hooks/use-color-scheme';
import { useTheme } from '@/hooks/use-theme';

export function AppBar({
  title,
  subtitle,
  eyebrow,
  action,
}: {
  title: string;
  subtitle?: string;
  eyebrow?: string;
  action?: ReactNode;
}) {
  return (
    <View style={styles.appBar} accessibilityRole="header">
      <View style={styles.flex}>
        {eyebrow ? (
          <ThemedText type="smallBold" themeColor="textSecondary" style={styles.eyebrow}>
            {eyebrow}
          </ThemedText>
        ) : null}
        <ThemedText style={styles.appBarTitle} maxFontSizeMultiplier={1.5}>
          {title}
        </ThemedText>
        {subtitle ? (
          <ThemedText type="small" themeColor="textSecondary" maxFontSizeMultiplier={1.6}>
            {subtitle}
          </ThemedText>
        ) : null}
      </View>
      {action}
    </View>
  );
}

export function RoleBadge({ role }: { role: OperationalRole }) {
  const scheme = useColorScheme();
  const resolvedScheme: MyPetScheme = scheme === 'dark' ? 'dark' : 'light';
  const accent = roleAccent(role, resolvedScheme);
  return (
    <View style={[styles.roleBadge, { backgroundColor: accent.accentSoft }]}>
      <ThemedText type="smallBold" style={{ color: accent.accent }}>
        {role === 'merchant' ? 'Merchant' : role === 'captain' ? 'Captain' : 'Admin'}
      </ThemedText>
    </View>
  );
}

export function SectionHeader({
  title,
  subtitle,
  actionLabel,
  onAction,
}: {
  title: string;
  subtitle?: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  const theme = useTheme();
  return (
    <View style={styles.sectionHeader}>
      <View style={styles.flex}>
        <ThemedText style={styles.sectionTitle}>{title}</ThemedText>
        {subtitle ? (
          <ThemedText type="small" themeColor="textSecondary">
            {subtitle}
          </ThemedText>
        ) : null}
      </View>
      {actionLabel && onAction ? (
        <Pressable
          onPress={onAction}
          accessibilityRole="button"
          accessibilityLabel={actionLabel}
          style={({ pressed }) => [styles.textAction, pressed && styles.pressed]}
        >
          <ThemedText type="smallBold" style={{ color: theme.primary }}>
            {actionLabel}
          </ThemedText>
        </Pressable>
      ) : null}
    </View>
  );
}

export function MetricCard({
  label,
  value,
  icon,
  tone = 'primary',
  hint,
  style,
}: {
  label: string;
  value: string;
  icon: AppIconName;
  tone?: 'primary' | 'success' | 'warning' | 'danger' | 'accent';
  hint?: string;
  style?: StyleProp<ViewStyle>;
}) {
  const theme = useTheme();
  const color =
    tone === 'success'
      ? theme.success
      : tone === 'warning'
        ? theme.warning
        : tone === 'danger'
          ? theme.danger
          : tone === 'accent'
            ? theme.accent
            : theme.primary;

  return (
    <View
      style={[
        styles.metricCard,
        Shadows.card,
        { backgroundColor: theme.backgroundElement, borderColor: theme.border },
        style,
      ]}
      accessible
      accessibilityLabel={`${label}: ${value}${hint ? `. ${hint}` : ''}`}
    >
      <View style={[styles.metricIcon, { backgroundColor: theme.muted }]}>
        <AppIcon name={icon} color={color} size={20} />
      </View>
      <ThemedText type="small" themeColor="textSecondary">
        {label}
      </ThemedText>
      <ThemedText style={[styles.metricValue, { color }]} maxFontSizeMultiplier={1.4}>
        {value}
      </ThemedText>
      {hint ? (
        <ThemedText type="small" themeColor="textSecondary" numberOfLines={2}>
          {hint}
        </ThemedText>
      ) : null}
    </View>
  );
}

export function StatusBadge({
  label,
  tone = 'neutral',
}: {
  label: string;
  tone?: 'neutral' | 'success' | 'warning' | 'danger' | 'info';
}) {
  const theme = useTheme();
  const backgroundColor =
    tone === 'success'
      ? theme.successSoft
      : tone === 'warning'
        ? theme.ctaSoft
        : tone === 'danger'
          ? theme.errorSoft
          : tone === 'info'
            ? theme.primarySoft
            : theme.muted;
  const color =
    tone === 'success'
      ? theme.success
      : tone === 'warning'
        ? theme.warning
        : tone === 'danger'
          ? theme.danger
          : tone === 'info'
            ? theme.primary
            : theme.textSecondary;

  return (
    <View style={[styles.badge, { backgroundColor }]} accessible accessibilityLabel={`Status: ${label}`}>
      <ThemedText type="smallBold" style={{ color }}>
        {label}
      </ThemedText>
    </View>
  );
}

export function FilterChip({
  label,
  selected,
  onPress,
  icon,
}: {
  label: string;
  selected?: boolean;
  onPress: () => void;
  icon?: AppIconName;
}) {
  const theme = useTheme();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      accessibilityLabel={label}
      style={({ pressed }) => [
        styles.chip,
        {
          backgroundColor: selected ? theme.primarySoft : theme.backgroundElement,
          borderColor: selected ? theme.primary : theme.border,
        },
        pressed && styles.pressed,
      ]}
    >
      {icon ? <AppIcon name={icon} color={selected ? theme.primary : theme.textSecondary} size={16} /> : null}
      <ThemedText type="smallBold" style={{ color: selected ? theme.primary : theme.text }}>
        {label}
      </ThemedText>
    </Pressable>
  );
}

export function FeedbackBanner({
  title,
  message,
  tone = 'info',
  icon,
}: {
  title: string;
  message?: string;
  tone?: 'info' | 'success' | 'warning' | 'danger';
  icon?: AppIconName;
}) {
  const theme = useTheme();
  const color =
    tone === 'success'
      ? theme.success
      : tone === 'warning'
        ? theme.warning
        : tone === 'danger'
          ? theme.danger
          : theme.primary;
  const backgroundColor =
    tone === 'success'
      ? theme.successSoft
      : tone === 'warning'
        ? theme.ctaSoft
        : tone === 'danger'
          ? theme.errorSoft
          : theme.primarySoft;
  const resolvedIcon = icon ?? (tone === 'danger' ? 'dispute' : tone === 'success' ? 'check' : 'sparkle');

  return (
    <View
      style={[styles.banner, { backgroundColor, borderColor: color }]}
      accessibilityLiveRegion="polite"
      accessible
      accessibilityLabel={`${title}. ${message ?? ''}`}
    >
      <AppIcon name={resolvedIcon} color={color} size={20} />
      <View style={styles.flex}>
        <ThemedText type="smallBold" style={{ color: theme.text }}>
          {title}
        </ThemedText>
        {message ? (
          <ThemedText type="small" style={{ color: theme.textSecondary }}>
            {message}
          </ThemedText>
        ) : null}
      </View>
    </View>
  );
}

export type StateKind = 'loading' | 'empty' | 'error' | 'offline' | 'unauthorized';

export function StateView({
  kind,
  title,
  message,
  actionLabel,
  onAction,
}: {
  kind: StateKind;
  title: string;
  message?: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  const theme = useTheme();
  const icon: AppIconName =
    kind === 'offline'
      ? 'dispute'
      : kind === 'error'
        ? 'xmark'
        : kind === 'unauthorized'
          ? 'shield'
          : kind === 'empty'
            ? 'inventory'
            : 'sparkle';

  return (
    <View style={styles.state} accessibilityLiveRegion="polite">
      {kind === 'loading' ? (
        <ActivityIndicator size="large" color={theme.primary} />
      ) : (
        <View style={[styles.stateIcon, { backgroundColor: theme.primarySoft }]}>
          <AppIcon name={icon} color={theme.primary} size={30} />
        </View>
      )}
      <ThemedText style={styles.sectionTitle} maxFontSizeMultiplier={1.5}>
        {title}
      </ThemedText>
      {message ? (
        <ThemedText type="small" themeColor="textSecondary" style={styles.center} maxFontSizeMultiplier={1.6}>
          {message}
        </ThemedText>
      ) : null}
      {actionLabel && onAction ? <ActionButton label={actionLabel} onPress={onAction} /> : null}
    </View>
  );
}

export type ActionVariant = 'primary' | 'secondary' | 'destructive' | 'ghost';

export function ActionButton({
  label,
  onPress,
  disabled,
  loading,
  variant = 'primary',
  icon,
  style,
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  loading?: boolean;
  variant?: ActionVariant;
  icon?: AppIconName;
  style?: StyleProp<ViewStyle>;
}) {
  const theme = useTheme();
  const isDisabled = Boolean(disabled || loading);
  const backgroundColor =
    variant === 'primary'
      ? theme.cta
      : variant === 'secondary'
        ? theme.ctaSoft
        : variant === 'destructive'
          ? theme.danger
          : 'transparent';
  const borderColor = variant === 'ghost' ? theme.border : backgroundColor;
  const color =
    variant === 'primary' || variant === 'destructive'
      ? '#FFFFFF'
      : variant === 'secondary'
        ? theme.cta
        : theme.primary;

  return (
    <Pressable
      onPress={onPress}
      disabled={isDisabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled: isDisabled, busy: loading }}
      style={({ pressed }) => [
        styles.actionButton,
        { backgroundColor, borderColor },
        isDisabled && styles.disabled,
        pressed && !isDisabled && styles.pressed,
        style,
      ]}
    >
      {loading ? <ActivityIndicator color={color} /> : icon ? <AppIcon name={icon} color={color} size={18} /> : null}
      {!loading ? (
        <ThemedText type="smallBold" style={{ color }} maxFontSizeMultiplier={1.5}>
          {label}
        </ThemedText>
      ) : null}
    </Pressable>
  );
}

export function StickyActionBar({ children }: { children: ReactNode }) {
  const theme = useTheme();
  return (
    <View style={[styles.stickyBar, Shadows.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
      {children}
    </View>
  );
}

export function SkeletonBlock({ height = 96 }: { height?: number }) {
  const theme = useTheme();
  return (
    <View
      accessibilityLabel="Loading content"
      style={[styles.skeleton, { height, backgroundColor: theme.muted, borderColor: theme.border }]}
    />
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  appBar: {
    minHeight: 72,
    paddingHorizontal: spacing.x4,
    paddingVertical: spacing.x2,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.x3,
  },
  eyebrow: { letterSpacing: 0.3 },
  appBarTitle: { ...typography.headline },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: spacing.x3,
  },
  sectionTitle: { ...typography.title },
  textAction: {
    minHeight: touchTarget,
    justifyContent: 'center',
    paddingHorizontal: spacing.x2,
  },
  roleBadge: {
    minHeight: 32,
    borderRadius: radii.pill,
    paddingHorizontal: spacing.x3,
    alignItems: 'center',
    justifyContent: 'center',
  },
  metricCard: {
    minHeight: 132,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: radii.card,
    padding: spacing.x4,
    gap: spacing.x2,
  },
  metricIcon: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  metricValue: { ...typography.title },
  badge: {
    minHeight: 28,
    borderRadius: radii.pill,
    paddingHorizontal: spacing.x2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  chip: {
    minHeight: touchTarget,
    borderWidth: 1,
    borderRadius: radii.pill,
    paddingHorizontal: spacing.x4,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.x2,
  },
  banner: {
    minHeight: 64,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: radii.card,
    padding: spacing.x3,
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.x3,
  },
  state: {
    flex: 1,
    minHeight: 280,
    padding: spacing.x6,
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.x3,
  },
  stateIcon: {
    width: 68,
    height: 68,
    borderRadius: 34,
    alignItems: 'center',
    justifyContent: 'center',
  },
  center: { textAlign: 'center', maxWidth: 440 },
  actionButton: {
    minHeight: touchTarget,
    borderWidth: 1,
    borderRadius: radii.compact,
    paddingHorizontal: spacing.x5 ?? spacing.x6,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.x2,
  },
  stickyBar: {
    borderTopWidth: StyleSheet.hairlineWidth,
    padding: spacing.x4,
    flexDirection: 'row',
    gap: spacing.x2,
  },
  skeleton: {
    borderRadius: radii.card,
    borderWidth: StyleSheet.hairlineWidth,
    opacity: 0.72,
  },
  disabled: { opacity: 0.55 },
  pressed: { opacity: 0.82 },
});
