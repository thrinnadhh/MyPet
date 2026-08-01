import { DarkTheme, DefaultTheme, ThemeProvider, usePathname, useRouter } from 'expo-router';
import { ActivityIndicator, StyleSheet, View, useColorScheme } from 'react-native';

import { AnimatedSplashOverlay } from '@/components/animated-icon';
import AppTabs from '@/components/app-tabs';
import { AppBar, StateView } from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { AuthProvider, useAuth } from '@/context/AuthContext';
import { LocaleProvider } from '@/context/LocaleContext';
import { usePushNotifications } from '@/hooks/usePushNotifications';
import { useTheme } from '@/hooks/use-theme';
import { appConfig } from '@/utils/app-config';
import '@/i18n';
import '@/services/captain-location';
import LoginScreen from './login';

function canAccessPath(pathname: string, role: string | null): boolean {
  if (pathname.startsWith('/admin')) return role === 'ADMIN' || appConfig.allowDemoMode;
  if (pathname.startsWith('/delivery') || pathname.startsWith('/captain-onboarding')) return role === 'CAPTAIN';
  if (
    pathname.startsWith('/orders') ||
    pathname.startsWith('/inventory') ||
    pathname.startsWith('/onboarding') ||
    pathname.startsWith('/explore')
  ) {
    return role === 'PROVIDER';
  }
  if (pathname.startsWith('/billing')) return role === 'PROVIDER' || role === 'ADMIN';
  return true;
}

function TabLayoutContent() {
  const colorScheme = useColorScheme();
  const theme = useTheme();
  const pathname = usePathname();
  const router = useRouter();
  const { user, loading, session, activeRole } = useAuth();
  usePushNotifications(user?.id, session?.access_token, activeRole);

  if (loading) {
    return (
      <View
        style={[styles.loading, { backgroundColor: theme.background }]}
        accessibilityLiveRegion="polite"
        accessibilityLabel="Restoring your MyPet session"
      >
        <ActivityIndicator size="large" color={theme.primary} />
        <ThemedText type="small" themeColor="textSecondary">
          Restoring your workspace…
        </ThemedText>
      </View>
    );
  }

  const themeValue = colorScheme === 'dark' ? DarkTheme : DefaultTheme;
  const accessDenied = Boolean(user && !canAccessPath(pathname, activeRole));

  return (
    <ThemeProvider value={themeValue}>
      <AnimatedSplashOverlay />
      {!user ? (
        <LoginScreen />
      ) : accessDenied ? (
        <ScreenShell scroll={false} header={<AppBar title="Restricted workspace" subtitle="This route is not available for the active role" />}>
          <StateView
            kind="unauthorized"
            title="Role access required"
            message="Switch to the correct verified MyPet role before opening this operational screen. Backend authorization remains the final enforcement boundary."
            actionLabel="Return to home"
            onAction={() => router.replace('/' as never)}
          />
        </ScreenShell>
      ) : (
        <AppTabs />
      )}
    </ThemeProvider>
  );
}

export default function TabLayout() {
  return (
    <AuthProvider>
      <LocaleProvider>
        <TabLayoutContent />
      </LocaleProvider>
    </AuthProvider>
  );
}

const styles = StyleSheet.create({
  loading: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    gap: 12,
  },
});
